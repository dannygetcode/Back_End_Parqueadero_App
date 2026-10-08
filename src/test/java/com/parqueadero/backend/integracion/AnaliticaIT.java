package com.parqueadero.backend.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Analítica (RF-50..53, RF-55). Los datos simulados se siembran por JDBC en fechas fijas y lejanas (2020 para
 * accesos, 2025 para pagos y mora con el reloj fijado en 2025-06-15, 2023 para el resumen del usuario) para que los
 * resultados se calculen a mano aunque el contenedor se comparta con las demás pruebas.
 */
class AnaliticaIT extends PruebaIntegracion {

    private static boolean sembrado;
    private static long cupoMoroso1;
    private static long cupoMoroso2;
    private static long idMoroso1;
    private static long idMoroso2;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void sembrar() throws Exception {
        if (sembrado) {
            return;
        }
        sembrado = true;
        // Accesos 2020 (lunes 2 de marzo): A carro 08-10 y 12-13:30, B carro 09-11, C moto 09:30-10:30; martes: D carro
        // entra a las 20:00 y no sale (censurada a las 24 h).
        long ua = usuarioSim(null), ub = usuarioSim(null), uc = usuarioSim(null), ud = usuarioSim(null);
        long a = vehiculo(ua, "ZZA101", "CARRO"), b = vehiculo(ub, "ZZB102", "CARRO");
        long c = vehiculo(uc, "ZZC10A", "MOTO"), d = vehiculo(ud, "ZZD104", "CARRO");
        evento(a, ua, "ENTRADA", "2020-03-02T08:00"); evento(a, ua, "SALIDA", "2020-03-02T10:00");
        evento(a, ua, "ENTRADA", "2020-03-02T12:00"); evento(a, ua, "SALIDA", "2020-03-02T13:30");
        evento(b, ub, "ENTRADA", "2020-03-02T09:00"); evento(b, ub, "SALIDA", "2020-03-02T11:00");
        evento(c, uc, "ENTRADA", "2020-03-02T09:30"); evento(c, uc, "SALIDA", "2020-03-02T10:30");
        evento(d, ud, "ENTRADA", "2020-03-03T20:00");

        // Pagos 2025 con tarifa 2025: enero carro 2 x 64.000 y moto 1 x 8.000 (+ una cortesía de 0); febrero carro 64.000.
        pago(ua, "CARRO", "2025-01-10", "2025-02-09", 64000, "2025-01-10T12:00");
        pago(ub, "CARRO", "2025-01-20", "2025-02-19", 64000, "2025-01-20T12:00");
        pago(uc, "MOTO", "2025-01-25", "2025-02-24", 8000, "2025-01-25T12:00");
        pago(uc, "MOTO", "2025-03-01", "2025-03-31", 0, "2025-03-01T12:00");
        pago(ua, "CARRO", "2025-02-10", "2025-03-09", 64000, "2025-02-10T12:00");

        // Mora con el reloj en 2025-06-15: m1 (carro) venció el 2025-05-06 (40 d), m2 (moto) el 2025-06-05 (10 d).
        cupoMoroso1 = crearCupo(TipoVehiculo.CARRO);
        cupoMoroso2 = crearCupo(TipoVehiculo.MOTO);
        idMoroso1 = usuarioSim(cupoMoroso1);
        idMoroso2 = usuarioSim(cupoMoroso2);
        pago(idMoroso1, "CARRO", "2025-04-07", "2025-05-06", 64000, "2025-04-07T12:00");
        pago(idMoroso2, "MOTO", "2025-05-06", "2025-06-05", 8000, "2025-05-06T12:00");
        // Renovación de abril: m3 y m4 vencían el 2025-04-10; m3 renovó el 13 (dentro de la gracia de 5 d), m4 el 30.
        long m3 = usuarioSim(null), m4 = usuarioSim(null);
        pago(m3, "CARRO", "2025-03-11", "2025-04-10", 64000, "2025-03-11T12:00");
        pago(m4, "CARRO", "2025-03-11", "2025-04-10", 64000, "2025-03-11T12:00");
        pago(m3, "CARRO", "2025-04-11", "2025-05-10", 64000, "2025-04-13T12:00");
        pago(m4, "CARRO", "2025-04-11", "2025-05-10", 64000, "2025-04-30T12:00");
    }

    // ---- siembra
    private long usuarioSim(Long cupoId) {
        return jdbc.queryForObject("""
                INSERT INTO usuario (parqueadero_id, cupo_id, telefono, nombre, apellido, estado, simulado)
                VALUES (1, ?, ?, 'Sim', 'Ulado', ?, true) RETURNING id
                """, Long.class, cupoId, telefonoAleatorio(), cupoId == null ? "ACTIVO" : "VENCIDO");
    }

    private long vehiculo(long usuarioId, String placa, String tipo) {
        return jdbc.queryForObject("""
                INSERT INTO vehiculo (usuario_id, placa, tipo_vehiculo, carroceria, color, simulado)
                VALUES (?, ?, ?, ?, 'Gris', true) RETURNING id
                """, Long.class, usuarioId, placa, tipo, tipo.equals("CARRO") ? "SEDAN" : null);
    }

    private void evento(long vehiculoId, long usuarioId, String tipo, String localBogota) {
        eventoCon(vehiculoId, usuarioId, tipo, localBogota, true);
    }

    private void eventoCon(long vehiculoId, long usuarioId, String tipo, String localBogota, boolean sim) {
        jdbc.update("""
                INSERT INTO evento_acceso (parqueadero_id, placa_leida, vehiculo_id, usuario_id, tipo, resultado, origen,
                                           ocurrido_en, simulado)
                VALUES (1, 'X', ?, ?, ?, 'PERMITIDO', 'CAMARA', ?::timestamptz, ?)
                """, vehiculoId, usuarioId, tipo, localBogota + ":00-05:00", sim);
    }

    private void pago(long usuarioId, String tipo, String inicio, String fin, int monto, String creadoLocal) {
        pagoCon(usuarioId, tipo, inicio, fin, monto, creadoLocal, true);
    }

    private void pagoCon(long usuarioId, String tipo, String inicio, String fin, int monto, String creadoLocal,
                         boolean sim) {
        jdbc.update("""
                INSERT INTO pago (usuario_id, tarifa_id, periodo_inicio, periodo_fin, monto_esperado, ocr_estado,
                                  monto_confirmado, estado, registrado_por, revisado_en, simulado, creado_en)
                VALUES (?, (SELECT id FROM tarifa WHERE tipo_vehiculo = ? AND vigente_desde = DATE '2025-01-01'),
                        ?::date, ?::date, ?, 'NO_APLICA', ?, 'APROBADO', 'ADMIN', ?::timestamptz, ?, ?::timestamptz)
                """, usuarioId, tipo, inicio, fin, monto, monto, creadoLocal + ":00-05:00", sim,
                creadoLocal + ":00-05:00");
    }

    private JsonNode admin(String url) throws Exception {
        return leer(mvc.perform(get(url).header("Authorization", bearerAdmin())).andExpect(status().isOk()).andReturn());
    }

    private JsonNode punto(JsonNode serie, String inicio) {
        for (JsonNode p : serie) {
            if (p.get("inicio").asText().startsWith(inicio)) {
                return p;
            }
        }
        throw new AssertionError("Sin punto " + inicio);
    }

    // ---- ocupación
    @Test
    void ocupacionPorHoraYDiaConCensura() throws Exception {
        String q = "/api/analitica/ocupacion?desde=2020-03-02&hasta=2020-03-03&incluirSimulados=true";
        JsonNode horas = admin(q + "&granularidad=hora").get("serie");
        assertThat(horas).hasSize(48);
        assertThat(punto(horas, "2020-03-02T09:00").get("carro").asDouble()).isEqualTo(2.0);
        assertThat(punto(horas, "2020-03-02T09:00").get("moto").asDouble()).isEqualTo(0.5);
        assertThat(punto(horas, "2020-03-02T09:00").get("picoCarro").asInt()).isEqualTo(2);
        assertThat(punto(horas, "2020-03-02T10:00").get("carro").asDouble()).isEqualTo(1.0);
        assertThat(punto(horas, "2020-03-02T13:00").get("carro").asDouble()).isEqualTo(0.5);
        assertThat(punto(horas, "2020-03-03T19:00").get("carro").asDouble()).isZero();
        assertThat(punto(horas, "2020-03-03T23:00").get("carro").asDouble()).isEqualTo(1.0);
        assertThat(punto(horas, "2020-03-03T23:00").get("censuradas").asInt()).isEqualTo(1);
        assertThat(punto(horas, "2020-03-02T09:00").get("censuradas").asInt()).isZero();

        JsonNode dias = admin(q + "&granularidad=dia").get("serie");
        assertThat(dias).hasSize(2);
        assertThat(dias.get(0).get("carro").asDouble()).isCloseTo(5.5 / 24, within(0.005));
        assertThat(dias.get(1).get("carro").asDouble()).isCloseTo(4.0 / 24, within(0.005));
        assertThat(dias.get(1).get("censuradas").asInt()).isEqualTo(1);
    }

    @Test
    void mapaDeCalorPorDiaDeLaSemanaYHora() throws Exception {
        JsonNode m = admin("/api/analitica/ocupacion/mapa-calor?desde=2020-03-02&hasta=2020-03-03&incluirSimulados=true");
        JsonNode lunes9 = null;
        for (JsonNode c : m.get("celdas")) {
            if (c.get("diaSemana").asInt() == 1 && c.get("hora").asInt() == 9) {
                lunes9 = c;
            }
        }
        assertThat(lunes9).isNotNull();
        assertThat(lunes9.get("ocupacionMediaCarro").asDouble()).isEqualTo(2.0);
        assertThat(lunes9.get("ocupacionMediaMoto").asDouble()).isEqualTo(0.5);
        assertThat(lunes9.get("usuariosDistintos").asInt()).isEqualTo(3);
        assertThat(lunes9.get("muestras").asInt()).isEqualTo(1);
        // Lleno si los carros dentro alcanzan los cupos de CARRO activos.
        assertThat(lunes9.get("probabilidadLlenoCarro").asDouble()).isEqualTo(2 >= m.get("cuposCarro").asInt() ? 1.0 : 0.0);
        assertThat(m.get("celdas").size()).isEqualTo(48);
    }

    // ---- permanencia
    @Test
    void permanenciaEstanciaTiempoFueraYHistograma() throws Exception {
        JsonNode r = admin("/api/analitica/permanencia?desde=2020-03-02&hasta=2020-03-03&incluirSimulados=true");
        JsonNode carro = r.get("porTipo").get(0);
        assertThat(carro.get("tipo").asText()).isEqualTo("CARRO");
        assertThat(carro.get("visitas").asInt()).isEqualTo(3);   // la censurada no cuenta como estancia
        assertThat(carro.at("/estanciaMin/mediana").asDouble()).isEqualTo(120.0);
        assertThat(carro.at("/estanciaMin/p25").asDouble()).isEqualTo(105.0);
        assertThat(carro.at("/estanciaMin/p75").asDouble()).isEqualTo(120.0);
        assertThat(carro.at("/fueraMin/media").asDouble()).isEqualTo(120.0);
        JsonNode moto = r.get("porTipo").get(1);
        assertThat(moto.at("/estanciaMin/mediana").asDouble()).isEqualTo(60.0);
        assertThat(r.get("porTipoYDia").get(0).get("diaSemana").asInt()).isEqualTo(1);
        JsonNode h = r.get("histogramaHoraEntrada");
        assertThat(h).hasSize(24);
        assertThat(h.get(9).get("carro").asInt()).isEqualTo(1);
        assertThat(h.get(9).get("moto").asInt()).isEqualTo(1);
        assertThat(h.get(20).get("carro").asInt()).isEqualTo(1);
    }

    // ---- ingresos
    @Test
    void ingresosPorMesYTipoConTarifaDelAnio() throws Exception {
        JsonNode r = admin("/api/analitica/ingresos?anio=2025&incluirSimulados=true");
        JsonNode ene = r.get("meses").get(0);
        assertThat(ene.at("/carro/ingresos").asLong()).isEqualTo(128000);
        assertThat(ene.at("/carro/pagos").asInt()).isEqualTo(2);
        assertThat(ene.at("/carro/ticketPromedio").asLong()).isEqualTo(64000);
        assertThat(ene.at("/carro/tarifaVigente").asInt()).isEqualTo(64000);
        assertThat(ene.at("/moto/ingresos").asLong()).isEqualTo(8000);
        assertThat(ene.get("total").asLong()).isEqualTo(136000);
        assertThat(r.get("meses").get(1).get("acumulado").asLong()).isEqualTo(200000);  // la cortesía de 0 no cuenta
    }

    @Test
    void tarifaSubeEn2026YSimuladosSeExcluyenPorDefecto() throws Exception {
        assertThat(admin("/api/analitica/ingresos?anio=2026").at("/meses/0/carro/tarifaVigente").asInt()).isEqualTo(80000);
        JsonNode sinSim = admin("/api/analitica/ingresos?anio=2025");
        assertThat(sinSim.at("/totalAnual/total").asLong()).isZero();
        assertThat(admin("/api/analitica/ocupacion?desde=2020-03-02&hasta=2020-03-03&granularidad=dia")
                .at("/serie/0/carro").asDouble()).isZero();
    }

    // ---- morosidad
    @Test
    void morosidadConDiasMoraMontoYAtraso() throws Exception {
        reloj.fijar(Instant.parse("2025-06-15T17:00:00Z"));
        JsonNode r = admin("/api/analitica/morosidad?incluirSimulados=true");
        JsonNode m1 = null, m2 = null;
        for (JsonNode u : r.get("usuarios")) {
            if (u.get("usuarioId").asLong() == idMoroso1) { m1 = u; }
            if (u.get("usuarioId").asLong() == idMoroso2) { m2 = u; }
        }
        assertThat(m1.get("diasMora").asInt()).isEqualTo(40);
        assertThat(m1.get("mesesAdeudados").asInt()).isEqualTo(2);
        assertThat(m1.get("montoAdeudado").asLong()).isEqualTo(2 * 64000);   // tarifa vigente el 2025-06-15
        assertThat(m2.get("diasMora").asInt()).isEqualTo(10);
        assertThat(m2.get("montoAdeudado").asLong()).isEqualTo(8000);
        assertThat(r.get("usuarios").get(0).get("usuarioId").asLong()).isEqualTo(idMoroso1);  // mayor mora primero
        assertThat(m1.has("telefono")).isFalse();

        JsonNode at = r.get("atraso");
        assertThat(at.get("tardios").asInt()).isEqualTo(2);              // atrasos de 2 y 19 días
        assertThat(at.get("medianaDiasAtraso").asDouble()).isEqualTo(10.5);
        assertThat(at.get("p90DiasAtraso").asDouble()).isEqualTo(17.3);
        JsonNode abril = null;
        for (JsonNode f : at.get("renovacionMensual")) {
            if (f.get("mes").asText().equals("2025-04")) { abril = f; }
        }
        assertThat(abril.get("vencian").asInt()).isEqualTo(2);
        assertThat(abril.get("renovaron").asInt()).isEqualTo(1);
        assertThat(abril.get("tasa").asDouble()).isEqualTo(0.5);
    }

    // ---- resumen general y de usuario
    @Test
    void resumenGeneralTieneLasSeccionesYFiltraSimulados() throws Exception {
        JsonNode r = admin("/api/analitica/resumen");
        assertThat(r.get("cupos")).isNotEmpty();
        assertThat(r.at("/vencimientos/lista").isArray()).isTrue();
        assertThat(r.at("/ingresos/mesActual").isNumber()).isTrue();
        int sinSim = r.at("/usuarios/activo").asInt() + r.at("/usuarios/vencido").asInt();
        JsonNode con = admin("/api/analitica/resumen?incluirSimulados=true");
        assertThat(con.at("/usuarios/activo").asInt() + con.at("/usuarios/vencido").asInt()).isGreaterThan(sinSim);
    }

    @Test
    void resumenDelUsuarioDesdeElToken() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        long veh = jdbc.queryForObject("SELECT id FROM vehiculo WHERE usuario_id = ?", Long.class, u.id());
        // Reloj en 2023-06-15: pagos de marzo y junio (vigente hasta el 30 de junio) y dos visitas en junio.
        pagoCon(u.id(), "CARRO", "2023-03-01", "2023-03-31", 64000, "2023-03-01T12:00", false);
        pagoCon(u.id(), "CARRO", "2023-06-01", "2023-06-30", 64000, "2023-06-01T12:00", false);
        eventoCon(veh, u.id(), "ENTRADA", "2023-06-10T08:00", false);
        eventoCon(veh, u.id(), "SALIDA", "2023-06-10T10:00", false);
        eventoCon(veh, u.id(), "ENTRADA", "2023-06-11T09:00", false);
        eventoCon(veh, u.id(), "SALIDA", "2023-06-11T10:00", false);
        reloj.fijar(Instant.parse("2023-06-15T17:00:00Z"));
        JsonNode r = leer(mvc.perform(get("/api/usuarios/yo/resumen").header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.get("estado").asText()).isEqualTo("VENCIDO");
        assertThat(r.get("diasRestantes").asInt()).isEqualTo(15);
        assertThat(r.get("totalPagadoAnio").asLong()).isEqualTo(128000);
        assertThat(r.get("promedioMensualPagado").asLong()).isEqualTo(Math.round(128000 / 6.0));
        assertThat(r.get("visitasMes").asInt()).isEqualTo(2);
        assertThat(r.get("permanenciaPromedioMin").asDouble()).isEqualTo(90.0);
        assertThat(r.get("horaHabitualLlegada").asText()).isEqualTo("08:30");
    }

    // ---- validación de rangos
    @Test
    void rangosInvalidosDan400() throws Exception {
        for (String url : new String[]{
                "/api/analitica/ocupacion?desde=2020-01-01&hasta=2021-02-05",           // 401 días
                "/api/analitica/ocupacion/mapa-calor?desde=2020-01-01&hasta=2021-02-05",
                "/api/analitica/permanencia?desde=2020-01-01&hasta=2021-02-05",
                "/api/analitica/ocupacion?desde=2020-03-05&hasta=2020-03-01",           // invertido
                "/api/analitica/ocupacion?desde=2020-03-01&hasta=2020-03-02&granularidad=mes",
                "/api/analitica/ocupacion?desde=ayer&hasta=2020-03-02",
                "/api/analitica/ingresos?anio=1900"}) {
            mvc.perform(get(url).header("Authorization", bearerAdmin())).andExpect(status().isBadRequest());
        }
        // 400 días exactos sí se aceptan.
        mvc.perform(get("/api/analitica/ocupacion?desde=2020-01-01&hasta=2021-02-03&granularidad=dia")
                .header("Authorization", bearerAdmin())).andExpect(status().isOk());
    }
}
