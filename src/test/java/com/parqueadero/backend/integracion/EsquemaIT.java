package com.parqueadero.backend.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway aplica V1 y V2 sobre PostgreSQL 15 real y Hibernate valida las entidades (ddl-auto=validate). */
class EsquemaIT extends PruebaIntegracion {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAplicaV1aV3() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versiones).containsExactly("1", "2", "3");
    }

    @Test
    void semillasDeReferencia() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM cupo WHERE codigo IN ('C1','C2','C3','C4','C5','C6') AND tipo_vehiculo='CARRO'",
                Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cupo WHERE codigo='M1' AND tipo_vehiculo='MOTO'",
                Integer.class)).isEqualTo(1);
        List<Map<String, Object>> tarifas = jdbc.queryForList(
                "SELECT tipo_vehiculo, valor_mensual, vigente_desde::text AS desde FROM tarifa "
                        + "WHERE vigente_desde <= DATE '2026-01-01' ORDER BY tipo_vehiculo, vigente_desde");
        assertThat(tarifas).extracting(t -> t.get("tipo_vehiculo") + ":" + t.get("valor_mensual") + ":" + t.get("desde"))
                .containsExactly("CARRO:64000:2025-01-01", "CARRO:80000:2026-01-01",
                        "MOTO:8000:2025-01-01", "MOTO:10000:2026-01-01");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM puerta", Integer.class)).isEqualTo(1);
    }

    @Test
    void administradorInicialConHashBCrypt() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM administrador WHERE usuario='admin-pruebas'",
                String.class);
        assertThat(hash).startsWith("$2").doesNotContain("clave-admin-pruebas");
    }
}
