package com.parqueadero.backend.service;

import java.time.LocalDate;

/**
 * Periodo de un pago (ADR 0002, RF-27, decisión P-02 del dueño). Para una fecha de referencia F:
 * <ul>
 *   <li>si el último pago APROBADO termina en una fecha >= F - díasGracia, el nuevo periodo continúa sin hueco
 *       (empieza el día siguiente a ese fin);</li>
 *   <li>si no (o no hay pagos aprobados), empieza en F.</li>
 * </ul>
 * El periodo dura un mes: fin = inicio + 1 mes - 1 día, salvo cuando el día de inicio no existe en el mes
 * siguiente: entonces el fin es el último día de ese mes (31-ene -> 28-feb, o 29 en bisiesto; 31-mar -> 30-abr),
 * para no acortar el periodo.
 * Al subir el comprobante F es la fecha de subida (periodo propuesto); al aprobar se recalcula con F = fecha de
 * aprobación.
 */
public final class CalculadoraPeriodo {

    private CalculadoraPeriodo() {
    }

    public record Periodo(LocalDate inicio, LocalDate fin) {
    }

    public static Periodo calcular(LocalDate ultimoFinAprobado, LocalDate referencia, int diasGracia) {
        LocalDate inicio = (ultimoFinAprobado != null && !ultimoFinAprobado.isBefore(referencia.minusDays(diasGracia)))
                ? ultimoFinAprobado.plusDays(1)
                : referencia;
        LocalDate masUnMes = inicio.plusMonths(1);
        // Si el día no existe en el mes siguiente (31-ene -> 28-feb), plusMonths ya cae en el último día: ese es el fin.
        LocalDate fin = masUnMes.getDayOfMonth() < inicio.getDayOfMonth() ? masUnMes : masUnMes.minusDays(1);
        return new Periodo(inicio, fin);
    }
}
