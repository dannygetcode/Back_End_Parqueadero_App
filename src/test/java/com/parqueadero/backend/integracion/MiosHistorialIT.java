package com.parqueadero.backend.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Historial propio (mios) incluye lo simulado del propio usuario (demo de la app); los listados de admin no.
 */
class MiosHistorialIT extends PruebaIntegracion {

    @Autowired
    JdbcTemplate jdbc;

    private long vehiculoDe(UsuarioPrueba u) {
        return jdbc.queryForObject("SELECT id FROM vehiculo WHERE usuario_id = ?", Long.class, u.id());
    }

    private void pagoSim(UsuarioPrueba u, boolean sim) {
        int desplazamiento = sim ? 0 : 40;
        jdbc.update("""
                INSERT INTO pago (usuario_id, tarifa_id, periodo_inicio, periodo_fin, monto_esperado, ocr_estado,
                                  monto_confirmado, estado, registrado_por, revisado_en, simulado)
                VALUES (?, (SELECT id FROM tarifa WHERE tipo_vehiculo = 'CARRO' AND vigente_desde = DATE '2025-01-01'),
                        DATE '2024-01-10' + ?, DATE '2024-02-09' + ?, 64000, 'NO_APLICA', 64000, 'APROBADO', 'ADMIN', now(), ?)
                """, u.id(), desplazamiento, desplazamiento, sim);
    }

    private void eventoHace(UsuarioPrueba u, String tipo, int dias, boolean sim) {
        jdbc.update("""
                INSERT INTO evento_acceso (parqueadero_id, placa_leida, vehiculo_id, usuario_id, tipo, resultado, origen,
                                           ocurrido_en, simulado)
                VALUES (1, ?, ?, ?, ?, 'PERMITIDO', 'CAMARA', now() - make_interval(days => ?), ?)
                """, u.placa(), vehiculoDe(u), u.id(), tipo, dias, sim);
    }

    @Test
    void miosIncluyeElHistorialSimuladoPropioPeroNoElDeOtro() throws Exception {
        UsuarioPrueba a = crearUsuario(TipoVehiculo.CARRO);
        UsuarioPrueba b = crearUsuario(TipoVehiculo.CARRO);
        pagoSim(a, true);
        pagoSim(b, true);
        eventoHace(a, "ENTRADA", 5, true);
        eventoHace(a, "SALIDA", 5, true);
        eventoHace(b, "ENTRADA", 5, true);

        mvc.perform(get("/api/pagos/mios").header("Authorization", bearerUsuario(a.id())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/accesos/mios").header("Authorization", bearerUsuario(a.id())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].placa", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(a.placa()))));
        mvc.perform(get("/api/accesos/mios").header("Authorization", bearerUsuario(b.id())))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void diasRespetaElRangoYRechazaLoQueEstaFuera() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        eventoHace(u, "ENTRADA", 10, true);
        eventoHace(u, "SALIDA", 10, true);
        eventoHace(u, "ENTRADA", 100, true);
        eventoHace(u, "SALIDA", 100, true);
        eventoHace(u, "ENTRADA", 400, true);
        String token = bearerUsuario(u.id());

        mvc.perform(get("/api/accesos/mios").header("Authorization", token))  // 30 por defecto
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/accesos/mios").param("dias", "5").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/accesos/mios").param("dias", "120").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/accesos/mios").param("dias", "365").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/accesos/mios").param("dias", "1").header("Authorization", token))
                .andExpect(status().isOk());
        for (String malo : new String[]{"0", "-3", "366", "abc"}) {
            mvc.perform(get("/api/accesos/mios").param("dias", malo).header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        JsonNode err = leer(mvc.perform(get("/api/accesos/mios").param("dias", "366")
                .header("Authorization", token)).andReturn());
        assertThat(err.get("detail").asText()).contains("dias").contains("365");
    }

    @Test
    void losListadosDeAdminSiguenExcluyendoSimulados() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        pagoSim(u, true);
        eventoHace(u, "ENTRADA", 2, true);
        eventoHace(u, "SALIDA", 2, true);
        pagoSim(u, false);
        eventoHace(u, "ENTRADA", 1, false);
        String admin = bearerAdmin();

        mvc.perform(get("/api/pagos").param("usuarioId", String.valueOf(u.id())).header("Authorization", admin))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/accesos").param("placa", u.placa()).header("Authorization", admin))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        // y el propio usuario ve ambos
        mvc.perform(get("/api/pagos/mios").header("Authorization", bearerUsuario(u.id())))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
