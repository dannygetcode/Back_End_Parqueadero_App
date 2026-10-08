package com.parqueadero.backend.integracion;

import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proxy /api/analitica/pronostico/* contra un servidor simulado. La matriz de roles está en AutorizacionIT. */
class PronosticoIT extends PruebaIntegracion {

    @BeforeEach
    @AfterEach
    void restablecer() {
        PRONOSTICO.modo(PronosticoSimulado.Modo.OK);
    }

    @Test
    void devuelveElJsonDelServicioTalCual() throws Exception {
        mvc.perform(get("/api/analitica/pronostico/vacancia").param("incluirSimulados", "true")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(PronosticoSimulado.CUERPO));
        assertThat(PRONOSTICO.ultimaConsulta()).isEqualTo("/pronostico/vacancia?incluirSimulados=true");
    }

    @Test
    void ocupacionYLlegadasUsanSusRutasYValoresPorDefecto() throws Exception {
        mvc.perform(get("/api/analitica/pronostico/ocupacion").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
        assertThat(PRONOSTICO.ultimaConsulta())
                .isEqualTo("/pronostico/ocupacion?horizonteDias=7&incluirSimulados=false");
        mvc.perform(get("/api/analitica/pronostico/llegadas").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
        assertThat(PRONOSTICO.ultimaConsulta()).isEqualTo("/pronostico/llegadas?incluirSimulados=false");
    }

    @Test
    void soloReenviaParametrosDeLaListaBlanca() throws Exception {
        mvc.perform(get("/api/analitica/pronostico/vacancia").param("incluirSimulados", "true")
                        .param("cualquierOtro", "x").param("usuarioId", "1").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
        assertThat(PRONOSTICO.ultimaConsulta()).isEqualTo("/pronostico/vacancia?incluirSimulados=true");
    }

    @Test
    void parametrosInvalidosDan400() throws Exception {
        for (String valor : new String[]{"0", "15", "-3", "abc", "7;x"}) {
            mvc.perform(get("/api/analitica/pronostico/ocupacion").param("horizonteDias", valor)
                            .header("Authorization", bearerAdmin()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/analitica/pronostico/ocupacion").param("horizonteDias", "14")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/analitica/pronostico/vacancia").param("incluirSimulados", "quiza")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void si500DelServicioResponde503LimpioSinDetalles() throws Exception {
        PRONOSTICO.modo(PronosticoSimulado.Modo.ERROR_500);
        mvc.perform(get("/api/analitica/pronostico/llegadas").header("Authorization", bearerAdmin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PRONOSTICO_NO_DISPONIBLE"))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("127.0.0.1"))));
    }

    @Test
    void siSeCortaLaConexionResponde503() throws Exception {
        PRONOSTICO.modo(PronosticoSimulado.Modo.CORTAR_CONEXION);
        mvc.perform(get("/api/analitica/pronostico/vacancia").header("Authorization", bearerAdmin()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void autorizacion() throws Exception {
        mvc.perform(get("/api/analitica/pronostico/vacancia")).andExpect(status().isUnauthorized());
        long usuarioId = crearUsuario(TipoVehiculo.CARRO).id();
        mvc.perform(get("/api/analitica/pronostico/vacancia").header("Authorization", bearerUsuario(usuarioId)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analitica/pronostico/vacancia").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
    }
}
