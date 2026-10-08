package com.parqueadero.backend.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Casos de cobertura: baja lógica, motivos de denegación, vigencia del código y recálculo del periodo al aprobar. */
class CoberturaIT extends PruebaIntegracion {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void bajaLogicaLiberaElCupoYConservaElHistorial() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        cortesia(u);
        mvc.perform(delete("/api/usuarios/" + u.id()).header("Authorization", bearerAdmin()))
                .andExpect(status().isNoContent());

        JsonNode cupo = null;
        for (JsonNode c : leer(mvc.perform(get("/api/cupos").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk()).andReturn())) {
            if (c.get("id").asLong() == u.cupoId()) {
                cupo = c;
            }
        }
        assertThat(cupo).isNotNull();
        assertThat(cupo.get("ocupado").asBoolean()).isFalse();
        assertThat(cupo.get("usuarioId").isNull()).isTrue();
        // La fila, el vehículo (inactivo) y el pago siguen en BD.
        assertThat(jdbc.queryForObject("SELECT dado_de_baja_en IS NOT NULL AND cupo_id IS NULL FROM usuario WHERE id = ?",
                Boolean.class, u.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehiculo WHERE usuario_id = ? AND NOT activo",
                Integer.class, u.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pago WHERE usuario_id = ?", Integer.class, u.id()))
                .isEqualTo(1);
    }

    @Test
    void motivosDeDenegacionSinCupoYUsuarioDeBaja() throws Exception {
        // Estados que la API no produce (se fuerzan en BD): el motivo debe seguir siendo el correcto.
        UsuarioPrueba sinCupo = crearUsuario(TipoVehiculo.CARRO);
        cortesia(sinCupo);
        jdbc.update("UPDATE usuario SET cupo_id = NULL WHERE id = ?", sinCupo.id());
        mvc.perform(post("/api/accesos/lecturas").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(Map.of("placa", sinCupo.placa()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.motivo").value("SIN_CUPO"));

        UsuarioPrueba deBaja = crearUsuario(TipoVehiculo.MOTO);
        cortesia(deBaja);
        jdbc.update("UPDATE usuario SET dado_de_baja_en = now(), cupo_id = NULL WHERE id = ?", deBaja.id());
        mvc.perform(post("/api/accesos/lecturas").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(Map.of("placa", deBaja.placa()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.motivo").value("USUARIO_DE_BAJA"));
    }

    @Test
    void codigoDeValidacionVenceALas72Horas() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        reloj.adelantar(Duration.ofHours(72).plusMinutes(1));
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", u.codigo(), "pin", PIN,
                                "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aprobarEnOtroDiaRecalculaElPeriodo() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        byte[] img = Arrays.copyOf(PNG, PNG.length + 8);
        img[PNG.length] = (byte) System.nanoTime();
        JsonNode pago = leer(mvc.perform(multipart("/api/pagos").file(comprobante(img, "p.png"))
                        .header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isCreated()).andReturn());
        LocalDate subida = LocalDate.parse(pago.get("periodoInicio").asText());

        reloj.adelantar(Duration.ofDays(3));
        mvc.perform(put("/api/pagos/" + pago.get("id").asLong() + "/aprobar").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"montoConfirmado\":80000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.periodoInicio").value(subida.plusDays(3).toString()));
    }
}
