package com.parqueadero.backend.analitica;

import com.parqueadero.backend.analitica.AnaliticaDTO.*;
import com.parqueadero.backend.analitica.AnaliticaRepository.PagoMes;
import com.parqueadero.backend.analitica.AnaliticaRepository.Tarifa;
import com.parqueadero.backend.analitica.AnaliticaRepository.Vigencia;
import com.parqueadero.backend.analitica.Intervalos.Intervalo;
import com.parqueadero.backend.config.AppConfig;
import com.parqueadero.backend.entity.TipoVehiculo;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.service.Fechas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnaliticaServiceImpl implements AnaliticaService {

    private static final String ZONA = AppConfig.ZONA.getId();

    private final AnaliticaRepository repo;
    private final Clock clock;
    private final int diasGracia;

    AnaliticaServiceImpl(AnaliticaRepository repo, Clock clock,
                         @Value("${usuarios.vencimiento.dias-gracia:5}") int diasGracia) {
        this.repo = repo;
        this.clock = clock;
        this.diasGracia = diasGracia;
    }

    private LocalDate hoy() {
        return LocalDate.now(clock.withZone(AppConfig.ZONA));
    }

    /** Rango inclusivo: hasta = hoy y desde = hasta - 6 días por omisión; máximo 400 días. */
    private LocalDate[] rango(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta != null ? hasta : hoy();
        LocalDate d = desde != null ? desde : h.minusDays(6);
        if (d.isAfter(h)) {
            throw NegocioException.invalido("'desde' no puede ser posterior a 'hasta'");
        }
        if (ChronoUnit.DAYS.between(d, h) + 1 > MAX_DIAS) {
            throw NegocioException.invalido("El rango máximo es de " + MAX_DIAS + " días");
        }
        return new LocalDate[]{d, h};
    }

    private static double r2(double v) {
        return Estadistica.redondear(v, 2);
    }

    // ---------------------------------------------------------------------------------------------------- resumen

    @Override
    public Resumen resumen(boolean sim) {
        Instant ahora = clock.instant();
        LocalDate hoy = hoy();

        List<CuposTipo> cupos = repo.cupos(sim).stream()
                .map(c -> new CuposTipo(c.tipo(), c.totales(), c.asignados(), c.vigentes(),
                        c.totales() == 0 ? 0 : Estadistica.redondear(100.0 * c.vigentes() / c.totales(), 1)))
                .toList();

        int carro = 0;
        int moto = 0;
        for (Object[] f : repo.dentroAhora(sim, ahora)) {
            if ("CARRO".equals(f[0])) {
                carro = (Integer) f[1];
            } else {
                moto = (Integer) f[1];
            }
        }

        Map<String, Integer> estados = new HashMap<>();
        repo.usuariosPorEstado(sim).forEach(f -> estados.put((String) f[0], (Integer) f[1]));

        LocalDate mes = hoy.withDayOfMonth(1);
        long actual = repo.ingresosEntre(Fechas.inicioDelDia(mes), Fechas.inicioDelDia(mes.plusMonths(1)), sim);
        long anterior = repo.ingresosEntre(Fechas.inicioDelDia(mes.minusMonths(1)), Fechas.inicioDelDia(mes), sim);
        Double variacion = anterior == 0 ? null : Estadistica.redondear(100.0 * (actual - anterior) / anterior, 1);

        List<Vencimiento> proximos = repo.vigencias(sim).stream()
                .filter(v -> "ACTIVO".equals(v.estado()) && v.fin() != null && !v.fin().isBefore(hoy)
                        && !v.fin().isAfter(hoy.plusDays(30)))
                .sorted(Comparator.comparing(Vigencia::fin).thenComparing(Vigencia::usuarioId))
                .map(v -> new Vencimiento(v.usuarioId(), v.cupo(), v.fin(), (int) ChronoUnit.DAYS.between(hoy, v.fin())))
                .toList();
        int d7 = (int) proximos.stream().filter(v -> v.diasRestantes() <= 7).count();
        int d15 = (int) proximos.stream().filter(v -> v.diasRestantes() <= 15).count();

        return new Resumen(Fechas.local(ahora), sim, cupos, new DentroAhora(carro, moto),
                new UsuariosPorEstado(estados.getOrDefault("ACTIVO", 0), estados.getOrDefault("VENCIDO", 0),
                        estados.getOrDefault("SUSPENDIDO", 0)),
                new IngresosMes(actual, anterior, variacion), repo.pagosPendientes(sim),
                new Vencimientos(d7, d15, proximos.size(), proximos));
    }

    // ------------------------------------------------------------------------------------------------- ocupación

    private List<Intervalo> intervalos(Instant desde, Instant hasta, boolean sim) {
        return Intervalos.construir(repo.eventos(desde.minus(Intervalos.MAXIMO), hasta.plus(Intervalos.MAXIMO), sim),
                clock.instant());
    }

    private static List<Intervalo> deTipo(List<Intervalo> ivs, TipoVehiculo t) {
        return ivs.stream().filter(i -> i.tipo() == t).toList();
    }

    @Override
    public SerieOcupacion ocupacion(LocalDate desde, LocalDate hasta, String granularidad, boolean sim) {
        boolean porHora = "hora".equalsIgnoreCase(granularidad);
        if (!porHora && !"dia".equalsIgnoreCase(granularidad)) {
            throw NegocioException.invalido("La granularidad debe ser 'hora' o 'dia'");
        }
        LocalDate[] r = rango(desde, hasta);
        Instant ini = Fechas.inicioDelDia(r[0]);
        Instant fin = Fechas.inicioDelDia(r[1].plusDays(1));
        long dias = ChronoUnit.DAYS.between(r[0], r[1]) + 1;
        Rejilla rej = new Rejilla(ini, porHora ? Duration.ofHours(1) : Duration.ofDays(1),
                (int) (porHora ? dias * 24 : dias));

        List<Intervalo> ivs = intervalos(ini, fin, sim);
        Rejilla.Resultado carros = rej.ocupacion(deTipo(ivs, TipoVehiculo.CARRO));
        Rejilla.Resultado motos = rej.ocupacion(deTipo(ivs, TipoVehiculo.MOTO));
        int[] cens = new int[rej.n()];
        for (Intervalo iv : ivs) {
            int[] ab = iv.censurada() ? rej.rango(iv.ini(), iv.fin()) : null;
            if (ab != null) {
                for (int i = ab[0]; i <= ab[1]; i++) {
                    cens[i]++;
                }
            }
        }
        List<PuntoOcupacion> serie = new ArrayList<>(rej.n());
        for (int i = 0; i < rej.n(); i++) {
            serie.add(new PuntoOcupacion(Fechas.local(rej.inicioDe(i)), r2(carros.media()[i]), r2(motos.media()[i]),
                    carros.pico()[i], motos.pico()[i], cens[i]));
        }
        return new SerieOcupacion(r[0], r[1], porHora ? "hora" : "dia", ZONA, sim, serie);
    }

    @Override
    public MapaCalor mapaCalor(LocalDate desde, LocalDate hasta, boolean sim) {
        LocalDate[] r = rango(desde, hasta);
        Instant ini = Fechas.inicioDelDia(r[0]);
        Instant fin = Fechas.inicioDelDia(r[1].plusDays(1));
        Rejilla rej = new Rejilla(ini, Duration.ofHours(1), (int) (ChronoUnit.DAYS.between(r[0], r[1]) + 1) * 24);
        int cuposCarro = repo.cuposCarroActivos();

        List<Intervalo> ivs = intervalos(ini, fin, sim);
        Rejilla.Resultado carros = rej.ocupacion(deTipo(ivs, TipoVehiculo.CARRO));
        Rejilla.Resultado motos = rej.ocupacion(deTipo(ivs, TipoVehiculo.MOTO));

        int[] celda = new int[rej.n()];
        for (int i = 0; i < rej.n(); i++) {
            ZonedDateTime z = rej.inicioDe(i).atZone(AppConfig.ZONA);
            celda[i] = (z.getDayOfWeek().getValue() - 1) * 24 + z.getHour();
        }
        double[] sumaCarro = new double[168];
        double[] sumaMoto = new double[168];
        int[] llenos = new int[168];
        int[] muestras = new int[168];
        for (int i = 0; i < rej.n(); i++) {
            int c = celda[i];
            muestras[c]++;
            sumaCarro[c] += carros.media()[i];
            sumaMoto[c] += motos.media()[i];
            if (cuposCarro > 0 && carros.pico()[i] >= cuposCarro) {
                llenos[c]++;
            }
        }
        List<Set<Long>> usuarios = new ArrayList<>();
        for (int c = 0; c < 168; c++) {
            usuarios.add(new HashSet<>());
        }
        for (Intervalo iv : ivs) {
            int[] ab = rej.rango(iv.ini(), iv.fin());
            if (ab != null) {
                for (int i = ab[0]; i <= ab[1]; i++) {
                    usuarios.get(celda[i]).add(iv.usuarioId());
                }
            }
        }
        List<CeldaCalor> celdas = new ArrayList<>();
        for (int c = 0; c < 168; c++) {
            if (muestras[c] > 0) {
                celdas.add(new CeldaCalor(c / 24 + 1, c % 24, r2(sumaCarro[c] / muestras[c]),
                        r2(sumaMoto[c] / muestras[c]),
                        cuposCarro > 0 ? r2((double) llenos[c] / muestras[c]) : null,
                        usuarios.get(c).size(), muestras[c]));
            }
        }
        return new MapaCalor(r[0], r[1], ZONA, sim, cuposCarro, celdas);
    }

    // -------------------------------------------------------------------------------------------------- ingresos

    private static int tarifaVigente(List<Tarifa> tarifas, TipoVehiculo tipo, LocalDate fecha) {
        return tarifas.stream().filter(t -> t.tipo() == tipo && !t.desde().isAfter(fecha))
                .max(Comparator.comparing(Tarifa::desde)).map(Tarifa::valor).orElse(0);
    }

    private static IngresosTipo ingresosTipo(Map<Integer, PagoMes> m, int mes, int tarifa) {
        PagoMes p = m.get(mes);
        long suma = p == null ? 0 : p.suma();
        int n = p == null ? 0 : p.cantidad();
        return new IngresosTipo(suma, n, n == 0 ? 0 : Math.round((double) suma / n), tarifa);
    }

    @Override
    public IngresosAnio ingresos(Integer anioParam, boolean sim) {
        int anio = anioParam != null ? anioParam : hoy().getYear();
        if (anio < 2000 || anio > 2200) {
            throw NegocioException.invalido("Año fuera de rango");
        }
        LocalDate enero = LocalDate.of(anio, 1, 1);
        List<Tarifa> tarifas = repo.tarifas();
        List<PagoMes> pagos = repo.pagosPorMes(Fechas.inicioDelDia(enero), Fechas.inicioDelDia(enero.plusYears(1)), sim);
        Map<TipoVehiculo, Map<Integer, PagoMes>> por = new EnumMap<>(TipoVehiculo.class);
        for (TipoVehiculo t : TipoVehiculo.values()) {
            por.put(t, pagos.stream().filter(p -> p.tipo() == t).collect(Collectors.toMap(PagoMes::mes, p -> p)));
        }
        List<IngresosMesTipo> meses = new ArrayList<>();
        long acumulado = 0;
        long totCarro = 0;
        long totMoto = 0;
        int totPagos = 0;
        for (int m = 1; m <= 12; m++) {
            LocalDate primero = LocalDate.of(anio, m, 1);
            IngresosTipo c = ingresosTipo(por.get(TipoVehiculo.CARRO), m, tarifaVigente(tarifas, TipoVehiculo.CARRO, primero));
            IngresosTipo mo = ingresosTipo(por.get(TipoVehiculo.MOTO), m, tarifaVigente(tarifas, TipoVehiculo.MOTO, primero));
            long total = c.ingresos() + mo.ingresos();
            int n = c.pagos() + mo.pagos();
            acumulado += total;
            totCarro += c.ingresos();
            totMoto += mo.ingresos();
            totPagos += n;
            meses.add(new IngresosMesTipo(m, c, mo, total, n, n == 0 ? 0 : Math.round((double) total / n), acumulado));
        }
        long totalAnual = totCarro + totMoto;
        List<TarifaAnio> aplicadas = new ArrayList<>();
        for (TipoVehiculo t : TipoVehiculo.values()) {
            LocalDate finAnio = enero.plusYears(1).minusDays(1);
            LocalDate inicial = tarifas.stream().filter(x -> x.tipo() == t && !x.desde().isAfter(enero))
                    .map(Tarifa::desde).max(Comparator.naturalOrder()).orElse(null);
            tarifas.stream().filter(x -> x.tipo() == t && x.desde().equals(inicial)
                            || x.tipo() == t && x.desde().isAfter(enero) && !x.desde().isAfter(finAnio))
                    .forEach(x -> aplicadas.add(new TarifaAnio(t, x.valor(), x.desde())));
        }
        return new IngresosAnio(anio, sim, meses, new TotalAnual(totCarro, totMoto, totalAnual, totPagos,
                totPagos == 0 ? 0 : Math.round((double) totalAnual / totPagos)), aplicadas);
    }

    // ------------------------------------------------------------------------------------------------- morosidad

    @Override
    public Morosidad morosidad(boolean sim) {
        LocalDate hoy = hoy();
        List<Tarifa> tarifas = repo.tarifas();
        List<Moroso> morosos = new ArrayList<>();
        int sinPago = 0;
        for (Vigencia v : repo.vigencias(sim)) {
            if (!"VENCIDO".equals(v.estado())) {
                continue;
            }
            if (v.fin() == null) {
                sinPago++;
                continue;
            }
            int dias = (int) Math.max(0, ChronoUnit.DAYS.between(v.fin(), hoy));
            int meses = Math.max(1, (int) Math.ceil(dias / 30.0));
            morosos.add(new Moroso(v.usuarioId(), v.cupo(), v.tipo(), v.fin(), dias, meses,
                    (long) meses * tarifaVigente(tarifas, v.tipo(), hoy)));
        }
        morosos.sort(Comparator.comparingInt(Moroso::diasMora).reversed().thenComparingLong(Moroso::usuarioId));

        final int ventana = 12;
        List<Integer> atrasos = repo.atrasos(Fechas.inicioDelDia(hoy.withDayOfMonth(1).minusMonths(ventana)), sim);
        List<Integer> tardios = atrasos.stream().filter(a -> a > 0).toList();
        List<RenovacionMes> renov = repo.renovaciones(hoy.withDayOfMonth(1).minusMonths(6), hoy, diasGracia, sim)
                .stream().map(f -> new RenovacionMes(f.mes(), f.vencian(), f.renovaron(),
                        f.vencian() == 0 ? null : Estadistica.redondear((double) f.renovaron() / f.vencian(), 3)))
                .toList();
        AtrasoPagos atraso = new AtrasoPagos(ventana, atrasos.size(), tardios.size(),
                atrasos.isEmpty() ? 0 : Estadistica.redondear(100.0 * tardios.size() / atrasos.size(), 1),
                Estadistica.mediana(tardios), Estadistica.percentil(tardios, 0.9), diasGracia, renov);
        return new Morosidad(hoy, sim, morosos, morosos.stream().mapToLong(Moroso::montoAdeudado).sum(), sinPago, atraso);
    }

    // ----------------------------------------------------------------------------------------------- permanencia

    private static int dia(Instant i) {
        return i.atZone(AppConfig.ZONA).getDayOfWeek().getValue();
    }

    private static double minutos(Instant a, Instant b) {
        return Duration.between(a, b).toSeconds() / 60.0;
    }

    @Override
    public Permanencia permanencia(LocalDate desde, LocalDate hasta, boolean sim) {
        LocalDate[] r = rango(desde, hasta);
        Instant ini = Fechas.inicioDelDia(r[0]);
        Instant fin = Fechas.inicioDelDia(r[1].plusDays(1));
        List<Intervalo> ivs = Intervalos.construir(repo.eventos(ini, fin.plus(Intervalos.MAXIMO), sim), clock.instant())
                .stream().filter(i -> i.ini().isBefore(fin)).toList();

        int[][] hist = new int[2][24];
        // clave: tipo ordinal * 8 + dia (0 = todos los días)
        Map<Integer, List<Double>> estancias = new HashMap<>();
        Map<Integer, List<Double>> fuera = new HashMap<>();
        Intervalo previo = null;
        for (Intervalo iv : ivs) {
            hist[iv.tipo().ordinal()][iv.ini().atZone(AppConfig.ZONA).getHour()]++;
            if (!iv.censurada()) {
                double m = minutos(iv.ini(), iv.fin());
                for (int d : new int[]{0, dia(iv.ini())}) {
                    estancias.computeIfAbsent(iv.tipo().ordinal() * 8 + d, k -> new ArrayList<>()).add(m);
                }
            }
            if (previo != null && previo.vehiculoId() == iv.vehiculoId() && !previo.censurada()) {
                double m = minutos(previo.fin(), iv.ini());
                for (int d : new int[]{0, dia(previo.fin())}) {
                    fuera.computeIfAbsent(iv.tipo().ordinal() * 8 + d, k -> new ArrayList<>()).add(m);
                }
            }
            previo = iv;
        }
        List<GrupoPermanencia> porTipo = new ArrayList<>();
        List<GrupoPermanencia> porDia = new ArrayList<>();
        for (TipoVehiculo t : TipoVehiculo.values()) {
            for (int d = 0; d <= 7; d++) {
                int k = t.ordinal() * 8 + d;
                List<Double> e = estancias.getOrDefault(k, List.of());
                List<Double> f = fuera.getOrDefault(k, List.of());
                if (e.isEmpty() && f.isEmpty()) {
                    continue;
                }
                GrupoPermanencia g = new GrupoPermanencia(t, d == 0 ? null : d, e.size(),
                        new Cuartiles(Estadistica.mediana(e), Estadistica.percentil(e, 0.25), Estadistica.percentil(e, 0.75)),
                        new Fuera(Estadistica.media(f), Estadistica.mediana(f)));
                (d == 0 ? porTipo : porDia).add(g);
            }
        }
        List<HoraEntrada> histograma = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            histograma.add(new HoraEntrada(h, hist[TipoVehiculo.CARRO.ordinal()][h], hist[TipoVehiculo.MOTO.ordinal()][h]));
        }
        return new Permanencia(r[0], r[1], ZONA, sim, porTipo, porDia, histograma);
    }

    // --------------------------------------------------------------------------------------------------- usuario

    @Override
    public ResumenUsuario resumenUsuario(long usuarioId) {
        String estado = repo.estadoDeUsuario(usuarioId)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        Instant ahora = clock.instant();
        LocalDate hoy = hoy();
        Integer dias = repo.vigenteHasta(usuarioId)
                .map(f -> (int) Math.max(0, ChronoUnit.DAYS.between(hoy, f))).orElse(null);
        long total = repo.totalPagado(usuarioId, Fechas.inicioDelDia(hoy.withDayOfYear(1)),
                Fechas.inicioDelDia(hoy.withDayOfYear(1).plusYears(1)));

        Instant desde = Fechas.inicioDelDia(hoy.minusDays(90));
        List<Intervalo> ivs = Intervalos.construir(repo.eventosDeUsuario(usuarioId, desde, ahora.plusSeconds(1)), ahora);
        Instant mes = Fechas.inicioDelDia(hoy.withDayOfMonth(1));
        int visitasMes = (int) ivs.stream().filter(i -> !i.ini().isBefore(mes)).count();
        Double permanencia = Estadistica.media(ivs.stream().filter(i -> !i.censurada())
                .map(i -> minutos(i.ini(), i.fin())).toList());
        List<Integer> llegadas = ivs.stream().map(i -> {
            ZonedDateTime z = i.ini().atZone(AppConfig.ZONA);
            return z.getHour() * 60 + z.getMinute();
        }).toList();
        Double med = Estadistica.mediana(llegadas);
        String hora = med == null ? null : "%02d:%02d".formatted((int) (med / 60), (int) (med % 60));
        return new ResumenUsuario(estado, dias, total, Math.round((double) total / hoy.getMonthValue()), visitasMes,
                permanencia, hora);
    }
}
