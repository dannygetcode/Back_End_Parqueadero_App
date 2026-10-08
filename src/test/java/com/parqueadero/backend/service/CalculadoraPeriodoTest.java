package com.parqueadero.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraPeriodoTest {

    private static final int GRACIA = 5;

    private static CalculadoraPeriodo.Periodo calc(String ultimoFin, String referencia) {
        return CalculadoraPeriodo.calcular(ultimoFin == null ? null : LocalDate.parse(ultimoFin),
                LocalDate.parse(referencia), GRACIA);
    }

    @Test
    void sinPagosAprobadosEmpiezaEnLaFechaDeReferencia() {
        var p = calc(null, "2026-10-07");
        assertThat(p.inicio()).isEqualTo("2026-10-07");
        assertThat(p.fin()).isEqualTo("2026-11-06");
    }

    @Test
    void continuaSinHuecoSiElUltimoTerminaDentroDeLaGracia() {
        // HU-A02: último periodo hasta el 14-oct, aprobado el 15 -> 15-oct a 14-nov.
        var p = calc("2026-10-14", "2026-10-15");
        assertThat(p.inicio()).isEqualTo("2026-10-15");
        assertThat(p.fin()).isEqualTo("2026-11-14");
    }

    @Test
    void pagoAdelantadoContinuaDespuesDelPeriodoVigente() {
        var p = calc("2026-10-31", "2026-10-20");
        assertThat(p.inicio()).isEqualTo("2026-11-01");
        assertThat(p.fin()).isEqualTo("2026-11-30");
    }

    @Test
    void ultimoDiaDeGraciaTodaviaContinua() {
        // fin 01-oct + 5 días de gracia = 06-oct.
        assertThat(calc("2026-10-01", "2026-10-06").inicio()).isEqualTo("2026-10-02");
    }

    @Test
    void pasadaLaGraciaEmpiezaElDiaDeAprobacion() {
        assertThat(calc("2026-10-01", "2026-10-07").inicio()).isEqualTo("2026-10-07");
        assertThat(calc("2026-07-01", "2026-10-07").inicio()).isEqualTo("2026-10-07");
    }

    @Test
    void finDeMesNoAcortaElPeriodo() {
        assertThat(calc(null, "2026-01-31").fin()).isEqualTo("2026-02-28");
        assertThat(calc(null, "2028-01-31").fin()).isEqualTo("2028-02-29");
        assertThat(calc(null, "2026-03-31").fin()).isEqualTo("2026-04-30");
        assertThat(calc(null, "2026-03-01").fin()).isEqualTo("2026-03-31");
        assertThat(calc(null, "2026-02-28").fin()).isEqualTo("2026-03-27");
        assertThat(calc(null, "2026-12-15").fin()).isEqualTo("2027-01-14");
    }
}
