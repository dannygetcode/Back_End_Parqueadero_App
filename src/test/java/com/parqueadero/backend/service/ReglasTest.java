package com.parqueadero.backend.service;

import com.parqueadero.backend.entity.OcrEstado;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas puras: placas, PIN trivial e interpretación del OCR. */
class ReglasTest {

    @Test
    void placasSeNormalizanYValidanPorTipo() {
        assertThat(Placas.normalizar(" abc-123 ")).isEqualTo("ABC123");
        assertThat(Placas.normalizar("abc 12d")).isEqualTo("ABC12D");
        assertThat(Placas.formatoValido("ABC123", TipoVehiculo.CARRO)).isTrue();
        assertThat(Placas.formatoValido("ABC12", TipoVehiculo.CARRO)).isFalse();
        assertThat(Placas.formatoValido("ABC12", TipoVehiculo.MOTO)).isTrue();
        assertThat(Placas.formatoValido("ABC12D", TipoVehiculo.MOTO)).isTrue();
        assertThat(Placas.formatoValido("ABC123", TipoVehiculo.MOTO)).isFalse();
    }

    @Test
    void pinesTriviales() {
        assertThat(ReglasPin.esTrivial("000000")).isTrue();
        assertThat(ReglasPin.esTrivial("777777")).isTrue();
        assertThat(ReglasPin.esTrivial("123456")).isTrue();
        assertThat(ReglasPin.esTrivial("654321")).isTrue();
        assertThat(ReglasPin.esTrivial("482915")).isFalse();
    }

    @Test
    void interpretacionDelOcr() {
        var ok = InterpreteOcr.interpretar(Map.of("fecha", "2026-10-01", "valor", 80000));
        assertThat(ok.estado()).isEqualTo(OcrEstado.EXITOSO);
        assertThat(ok.monto()).isEqualTo(80000);
        assertThat(ok.fecha()).isEqualTo(LocalDate.of(2026, 10, 1));

        var parcial = InterpreteOcr.interpretar(Map.of("valor", "$ 80.000"));
        assertThat(parcial.estado()).isEqualTo(OcrEstado.PARCIAL);
        assertThat(parcial.monto()).isEqualTo(80000);

        assertThat(InterpreteOcr.interpretar(Map.of()).estado()).isEqualTo(OcrEstado.FALLIDO);
        assertThat(InterpreteOcr.interpretar(null).estado()).isEqualTo(OcrEstado.FALLIDO);
        assertThat(InterpreteOcr.fecha("6 de junio de 2025")).isEqualTo(LocalDate.of(2025, 6, 6));
        assertThat(InterpreteOcr.fecha("16/06/2025")).isEqualTo(LocalDate.of(2025, 6, 16));
    }
}
