package com.parqueadero.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/** RNF-12: un error no controlado responde 500 con detalle genérico, sin mensaje de la excepción ni SQL. */
class ManejadorErroresTest {

    @Test
    void errorInesperadoNoFiltraDetalles() {
        ProblemDetail pd = new ManejadorErrores().inesperado(
                new RuntimeException("ERROR: select * from usuario where pin_hash = 'x'"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).startsWith("Error interno. Referencia: ").doesNotContain("select", "usuario");
        assertThat(pd.getProperties()).containsKey("referencia");
    }
}
