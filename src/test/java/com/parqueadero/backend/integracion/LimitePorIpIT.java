package com.parqueadero.backend.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * N2 / ADR 0003: límite por IP en los endpoints de autenticación. Contexto propio con un límite de 3 peticiones por
 * minuto (las demás pruebas usan un límite alto porque MockMvc siempre llega desde 127.0.0.1).
 */
class LimitePorIpIT extends PruebaIntegracion {

    @DynamicPropertySource
    static void limiteBajo(DynamicPropertyRegistry r) {
        r.add("seguridad.limite-ip.max-peticiones", () -> 3);
        r.add("seguridad.limite-ip.ventana-segundos", () -> 60);
    }

    @Test
    void alSuperarElLimiteResponde429ConProblemDetailYSeRecuperaTrasLaVentana() throws Exception {
        String ip = "10.0.0.1";
        for (int i = 0; i < 3; i++) {
            loginAdmin(ip).andExpect(status().isUnauthorized());
        }
        loginAdmin(ip).andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail", containsString("Demasiados intentos")));

        // Otra IP no está afectada, y cada ruta lleva su propio contador.
        loginAdmin("10.0.0.2").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(desde(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", telefonoAleatorio(), "pin", "739104"))))
                .andExpect(status().isUnauthorized());

        reloj.adelantar(Duration.ofSeconds(61));
        loginAdmin(ip).andExpect(status().isUnauthorized());
    }

    @Test
    void elLimiteNoAfectaAOtrosEndpoints() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ping")
                    .with(desde("10.0.0.3"))).andExpect(status().isOk());
        }
    }

    private ResultActions loginAdmin(String ip) throws Exception {
        return mvc.perform(post("/api/admin/login").with(desde(ip)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("username", "no-existe-" + ip, "password", "mala-clave-123"))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor desde(String ip) {
        return req -> {
            req.setRemoteAddr(ip);
            return req;
        };
    }
}
