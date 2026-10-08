package com.parqueadero.backend.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alta por el admin -> activación con código, PIN y consentimiento -> login; y que nunca salen PIN ni código. */
class UsuarioFlujoIT extends PruebaIntegracion {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void altaActivacionYLogin() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        assertThat(u.codigo()).matches("^[A-Z0-9]{8}$");

        // Recién creado: VENCIDO, sin validar, placa normalizada (se envió con guion).
        mvc.perform(get("/api/usuarios/" + u.id()).header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.estado").value("VENCIDO"))
                .andExpect(jsonPath("$.usuario.validado").value(false))
                .andExpect(jsonPath("$.vehiculo.placa").value(u.placa()));

        // Sin token todavía: login falla de forma genérica.
        login(u.telefono(), PIN, 401);

        activar(u);
        MvcResult r = login(u.telefono(), PIN, 200);
        JsonNode token = leer(r);
        assertThat(token.get("rol").asText()).isEqualTo("USUARIO");
        UsuarioAutenticado principal = jwtService.validar(token.get("token").asText()).orElseThrow();
        assertThat(principal.id()).isEqualTo(u.id());

        mvc.perform(get("/api/usuarios/yo").header("Authorization", "Bearer " + token.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(u.id()))
                .andExpect(jsonPath("$.validado").value(true))
                .andExpect(jsonPath("$.cupo.id").value(u.cupoId()));

        // En BD solo hay hashes BCrypt y queda el consentimiento con su versión.
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT pin_hash, codigo_validacion_hash, consentimiento_version, consentimiento_datos_en "
                        + "FROM usuario WHERE id = ?", u.id());
        assertThat((String) fila.get("pin_hash")).startsWith("$2").doesNotContain(PIN);
        assertThat(fila.get("codigo_validacion_hash")).isNull();
        assertThat(fila.get("consentimiento_version")).isEqualTo("2026-10");
        assertThat(fila.get("consentimiento_datos_en")).isNotNull();

        // El código es de un solo uso.
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", u.codigo(), "pin", "739104",
                                "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ningunaRespuestaIncluyePinNiCodigo() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.MOTO);
        activar(u);
        String admin = bearerAdmin();
        String[] respuestas = {
                mvc.perform(get("/api/usuarios").header("Authorization", admin)).andReturn().getResponse().getContentAsString(),
                mvc.perform(get("/api/usuarios/" + u.id()).header("Authorization", admin)).andReturn().getResponse().getContentAsString(),
                mvc.perform(get("/api/usuarios/yo").header("Authorization", bearerUsuario(u.id()))).andReturn().getResponse().getContentAsString(),
                mvc.perform(put("/api/usuarios/" + u.id()).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ana María\"}"))
                        .andReturn().getResponse().getContentAsString(),
                mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "pin", PIN)))).andReturn().getResponse().getContentAsString()
        };
        for (String r : respuestas) {
            assertThat(r).doesNotContainIgnoringCase("\"pin")
                    .doesNotContainIgnoringCase("codigoValidacion")
                    .doesNotContainIgnoringCase("hash")
                    .doesNotContainIgnoringCase("password")
                    .doesNotContain(u.codigo())
                    .doesNotContain(PIN);
        }
    }

    @Test
    void validacionesDeActivacion() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        // PIN trivial -> 400
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", u.codigo(), "pin", "123456",
                                "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))))
                .andExpect(status().isBadRequest());
        // PIN de 4 dígitos -> 400
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", u.codigo(), "pin", "4829",
                                "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("pin"));
        // Sin consentimiento -> 400
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", u.codigo(), "pin", PIN,
                                "aceptaTratamientoDatos", false, "versionConsentimiento", "2026-10"))))
                .andExpect(status().isBadRequest());
        // Código equivocado -> 401 genérico
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", "ZZZZZZZZ", "pin", PIN,
                                "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reglasDelAlta() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        String admin = bearerAdmin();
        // Cupo ya asignado -> 409
        mvc.perform(post("/api/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", telefonoAleatorio(), "nombre", "B", "apellido", "C",
                                "cupoId", u.cupoId(), "vehiculo", Map.of("placa", placaAleatoria(TipoVehiculo.CARRO),
                                        "tipoVehiculo", "CARRO", "carroceria", "SUV", "color", "Azul")))))
                .andExpect(status().isConflict());
        // Carro en cupo de moto -> 400
        long cupoMoto = crearCupo(TipoVehiculo.MOTO);
        mvc.perform(post("/api/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", telefonoAleatorio(), "nombre", "B", "apellido", "C",
                                "cupoId", cupoMoto, "vehiculo", Map.of("placa", placaAleatoria(TipoVehiculo.CARRO),
                                        "tipoVehiculo", "CARRO", "carroceria", "SUV", "color", "Azul")))))
                .andExpect(status().isBadRequest());
        // Placa duplicada -> 409
        mvc.perform(post("/api/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", telefonoAleatorio(), "nombre", "B", "apellido", "C",
                                "cupoId", crearCupo(TipoVehiculo.CARRO), "vehiculo", Map.of("placa", u.placa(),
                                        "tipoVehiculo", "CARRO", "carroceria", "SUV", "color", "Azul")))))
                .andExpect(status().isConflict());
        // Teléfono no colombiano -> 400
        mvc.perform(post("/api/usuarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", "12345", "nombre", "B", "apellido", "C",
                                "cupoId", crearCupo(TipoVehiculo.CARRO), "vehiculo", Map.of("placa",
                                        placaAleatoria(TipoVehiculo.CARRO), "tipoVehiculo", "CARRO",
                                        "carroceria", "SUV", "color", "Azul")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspenderYReactivar() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        String admin = bearerAdmin();
        mvc.perform(put("/api/usuarios/" + u.id() + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"SUSPENDER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/usuarios/" + u.id() + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accion\":\"SUSPENDER\",\"motivo\":\"Mal parqueado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"));
        // Sin pagos, al reactivar queda VENCIDO (no se puede poner ACTIVO a mano).
        mvc.perform(put("/api/usuarios/" + u.id() + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"REACTIVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VENCIDO"));
    }

    @Test
    void tokenPrevioSeRechazaTrasCambiarPinYTrasBaja() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        activar(u);
        String viejo = bearerUsuario(u.id());
        mvc.perform(get("/api/usuarios/yo").header("Authorization", viejo)).andExpect(status().isOk());

        reloj.adelantar(java.time.Duration.ofSeconds(2));
        mvc.perform(put("/api/usuarios/yo/pin").header("Authorization", viejo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("pinActual", PIN, "pinNuevo", "739104"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/yo").header("Authorization", viejo)).andExpect(status().isUnauthorized());

        reloj.adelantar(java.time.Duration.ofSeconds(2));
        String nuevo = bearerUsuario(u.id());
        mvc.perform(get("/api/usuarios/yo").header("Authorization", nuevo)).andExpect(status().isOk());

        reloj.adelantar(java.time.Duration.ofSeconds(2));
        mvc.perform(delete("/api/usuarios/" + u.id()).header("Authorization", bearerAdmin()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/yo").header("Authorization", nuevo)).andExpect(status().isUnauthorized());
    }

    @Test
    void activarConVersionDeAvisoDistintaDa400() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.MOTO);
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", u.codigo(), "pin", PIN,
                                "aceptaTratamientoDatos", true, "versionConsentimiento", "1999-01"))))
                .andExpect(status().isBadRequest());
        activar(u);
    }

    @Test
    void enumInvalidoYJsonIlegibleDan400EnEspanol() throws Exception {
        long cupoId = crearCupo(TipoVehiculo.CARRO);
        mvc.perform(post("/api/usuarios").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"" + telefonoAleatorio() + "\",\"nombre\":\"Ana\",\"apellido\":\"P\","
                                + "\"cupoId\":" + cupoId + ",\"vehiculo\":{\"placa\":\"ABC123\",\"tipoVehiculo\":\"BICI\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("vehiculo.tipoVehiculo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(org.hamcrest.Matchers.containsString("CARRO")));
        mvc.perform(post("/api/usuarios").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición no es válido"));
    }

    private MvcResult login(String telefono, String pin, int esperado) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", telefono, "pin", pin))))
                .andExpect(status().is(esperado)).andReturn();
    }
}
