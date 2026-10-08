package com.parqueadero.backend.analitica;

import com.parqueadero.backend.entity.TipoVehiculo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Contratos de salida de la analítica (docs/contrato-analitica.md). Montos en COP enteros; sin datos personales. */
public final class AnaliticaDTO {

    private AnaliticaDTO() {
    }

    // ---- resumen
    public record CuposTipo(TipoVehiculo tipo, int totales, int asignados, int vigentes, double porcentajeContratado) {
    }

    public record DentroAhora(int carro, int moto) {
    }

    public record UsuariosPorEstado(int activo, int vencido, int suspendido) {
    }

    public record IngresosMes(long mesActual, long mesAnterior, Double variacionPct) {
    }

    public record Vencimiento(long usuarioId, String cupo, LocalDate fechaFin, int diasRestantes) {
    }

    public record Vencimientos(int en7Dias, int en15Dias, int en30Dias, List<Vencimiento> lista) {
    }

    public record Resumen(OffsetDateTime generadoEn, boolean incluirSimulados, List<CuposTipo> cupos,
                          DentroAhora dentroAhora, UsuariosPorEstado usuarios, IngresosMes ingresos,
                          int pagosPendientes, Vencimientos vencimientos) {
    }

    // ---- ocupación
    public record PuntoOcupacion(OffsetDateTime inicio, double carro, double moto, int picoCarro, int picoMoto,
                                 int censuradas) {
    }

    public record SerieOcupacion(LocalDate desde, LocalDate hasta, String granularidad, String zonaHoraria,
                                 boolean incluirSimulados, List<PuntoOcupacion> serie) {
    }

    public record CeldaCalor(int diaSemana, int hora, double ocupacionMediaCarro, double ocupacionMediaMoto,
                             Double probabilidadLlenoCarro, int usuariosDistintos, int muestras) {
    }

    public record MapaCalor(LocalDate desde, LocalDate hasta, String zonaHoraria, boolean incluirSimulados,
                            int cuposCarro, List<CeldaCalor> celdas) {
    }

    // ---- ingresos
    public record IngresosTipo(long ingresos, int pagos, long ticketPromedio, int tarifaVigente) {
    }

    public record IngresosMesTipo(int mes, IngresosTipo carro, IngresosTipo moto, long total, int pagos,
                                  long ticketPromedio, long acumulado) {
    }

    public record TarifaAnio(TipoVehiculo tipo, int valorMensual, LocalDate vigenteDesde) {
    }

    public record TotalAnual(long carro, long moto, long total, int pagos, long ticketPromedio) {
    }

    public record IngresosAnio(int anio, boolean incluirSimulados, List<IngresosMesTipo> meses, TotalAnual totalAnual,
                               List<TarifaAnio> tarifas) {
    }

    // ---- morosidad
    public record Moroso(long usuarioId, String cupo, TipoVehiculo tipo, LocalDate vencioEl, int diasMora,
                         int mesesAdeudados, long montoAdeudado) {
    }

    public record RenovacionMes(String mes, int vencian, int renovaron, Double tasa) {
    }

    public record AtrasoPagos(int ventanaMeses, int pagos, int tardios, double porcentajeTardios,
                              Double medianaDiasAtraso, Double p90DiasAtraso, int diasGracia,
                              List<RenovacionMes> renovacionMensual) {
    }

    public record Morosidad(LocalDate fecha, boolean incluirSimulados, List<Moroso> usuarios, long totalAdeudado,
                            int vencidosSinPagoPrevio, AtrasoPagos atraso) {
    }

    // ---- permanencia
    public record Cuartiles(Double mediana, Double p25, Double p75) {
    }

    public record Fuera(Double media, Double mediana) {
    }

    /** diaSemana ISO (1 = lunes ... 7 = domingo); nulo en las filas "por tipo". */
    public record GrupoPermanencia(TipoVehiculo tipo, Integer diaSemana, int visitas, Cuartiles estanciaMin,
                                   Fuera fueraMin) {
    }

    public record HoraEntrada(int hora, int carro, int moto) {
    }

    public record Permanencia(LocalDate desde, LocalDate hasta, String zonaHoraria, boolean incluirSimulados,
                              List<GrupoPermanencia> porTipo, List<GrupoPermanencia> porTipoYDia,
                              List<HoraEntrada> histogramaHoraEntrada) {
    }

    // ---- usuario
    public record ResumenUsuario(String estado, Integer diasRestantes, long totalPagadoAnio,
                                 long promedioMensualPagado, int visitasMes, Double permanenciaPromedioMin,
                                 String horaHabitualLlegada) {
    }
}
