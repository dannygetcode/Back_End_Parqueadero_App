package com.parqueadero.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidacionSecretosTest {

    private static final String CLAVE_32 = "0123456789abcdef0123456789abcdef";

    @Test
    void configuracionValidaNoDaErrores() {
        assertThat(ValidacionSecretos.validar(CLAVE_32, CLAVE_32, "admin", "clave-larga-12", "app", "otra")).isEmpty();
        // CAMARA_API_KEY y ADMIN_PASSWORD vacías se admiten (simulador deshabilitado / admin ya creado).
        assertThat(ValidacionSecretos.validar(CLAVE_32, "", "admin", "", "app", "otra")).isEmpty();
    }

    @Test
    void rechazaClavesCortasYMarcadores() {
        assertThat(ValidacionSecretos.validar(CLAVE_32, "corta", "admin", "clave-larga-12", "app", "x"))
                .singleElement().asString().contains("CAMARA_API_KEY");
        assertThat(ValidacionSecretos.validar(CLAVE_32, CLAVE_32, "admin", "11-caracter", "app", "x"))
                .singleElement().asString().contains("ADMIN_PASSWORD");
        assertThat(ValidacionSecretos.validar("corto", CLAVE_32, "admin", "clave-larga-12", "app", "x"))
                .singleElement().asString().contains("JWT_SECRET");
        assertThat(ValidacionSecretos.validar(CLAVE_32, CLAVE_32, "change-me", "clave-larga-12", "change-me", "CHANGE-ME"))
                .hasSize(3)
                .allSatisfy(e -> assertThat(e).contains("change-me"));
    }

    @Test
    void elMensajeNoIncluyeLosValores() {
        String secreto = "secreto-muy-corto";
        assertThatThrownBy(() -> new ValidacionSecretos(secreto, secreto, "admin", "corta", "app", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secreto)
                .hasMessageNotContaining("corta");
    }
}
