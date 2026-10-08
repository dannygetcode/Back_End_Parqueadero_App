package com.parqueadero.backend.integracion;

import com.parqueadero.backend.entity.TipoVehiculo;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de autorización (RNF-01): para cada endpoint principal y cada tipo de llamante (anónimo, USUARIO, ADMIN,
 * SISTEMA con API key) se comprueba 401 sin credenciales, 403 con un rol no permitido y que un rol permitido pasa
 * la seguridad (la respuesta puede ser 2xx, 400 o 404 según el cuerpo, pero nunca 401/403).
 */
class AutorizacionIT extends PruebaIntegracion {

    enum Quien { ANONIMO, USUARIO, ADMIN, SISTEMA }

    record Fila(HttpMethod metodo, String ruta, String cuerpo, Set<Quien> permitidos) {
    }

    private static final Set<Quien> PUBLICO = EnumSet.allOf(Quien.class);
    private static final Set<Quien> A = EnumSet.of(Quien.ADMIN);
    private static final Set<Quien> U = EnumSet.of(Quien.USUARIO);
    private static final Set<Quien> AU = EnumSet.of(Quien.ADMIN, Quien.USUARIO);
    private static final Set<Quien> SA = EnumSet.of(Quien.SISTEMA, Quien.ADMIN);

    private static final List<Fila> MATRIZ = List.of(
            new Fila(HttpMethod.GET, "/api/ping", null, PUBLICO),
            new Fila(HttpMethod.GET, "/api/legal/aviso-privacidad", null, PUBLICO),
            new Fila(HttpMethod.POST, "/api/admin/login", "{}", PUBLICO),
            new Fila(HttpMethod.POST, "/api/auth/login", "{}", PUBLICO),
            new Fila(HttpMethod.POST, "/api/auth/activar", "{}", PUBLICO),
            new Fila(HttpMethod.GET, "/api/usuarios", null, A),
            new Fila(HttpMethod.POST, "/api/usuarios", "{}", A),
            new Fila(HttpMethod.GET, "/api/usuarios/999999", null, A),
            new Fila(HttpMethod.PUT, "/api/usuarios/999999/estado", "{}", A),
            new Fila(HttpMethod.POST, "/api/usuarios/999999/codigo", null, A),
            new Fila(HttpMethod.DELETE, "/api/usuarios/999999", null, A),
            new Fila(HttpMethod.GET, "/api/usuarios/yo", null, U),
            new Fila(HttpMethod.PUT, "/api/usuarios/yo/pin", "{}", U),
            new Fila(HttpMethod.GET, "/api/cupos", null, A),
            new Fila(HttpMethod.POST, "/api/cupos", "{}", A),
            new Fila(HttpMethod.GET, "/api/tarifas", null, AU),
            new Fila(HttpMethod.POST, "/api/tarifas", "{}", A),
            new Fila(HttpMethod.GET, "/api/pagos", null, A),
            new Fila(HttpMethod.GET, "/api/pagos/mios", null, U),
            new Fila(HttpMethod.GET, "/api/pagos/proximo-periodo", null, U),
            new Fila(HttpMethod.GET, "/api/pagos/999999", null, AU),
            new Fila(HttpMethod.GET, "/api/pagos/999999/comprobante", null, AU),
            new Fila(HttpMethod.PUT, "/api/pagos/999999/aprobar", "{}", A),
            new Fila(HttpMethod.PUT, "/api/pagos/999999/rechazar", "{}", A),
            new Fila(HttpMethod.POST, "/api/accesos/lecturas", "{}", SA),
            new Fila(HttpMethod.GET, "/api/accesos", null, A),
            new Fila(HttpMethod.GET, "/api/accesos/ocupacion", null, A),
            new Fila(HttpMethod.GET, "/api/accesos/mios", null, U),
            new Fila(HttpMethod.GET, "/api/puerta", null, AU),
            new Fila(HttpMethod.PUT, "/api/puerta", "{}", AU),
            new Fila(HttpMethod.GET, "/api/camaras", null, AU),
            new Fila(HttpMethod.POST, "/api/camaras", "{}", A),
            new Fila(HttpMethod.PUT, "/api/camaras/1", "{}", A));

    @Test
    void matrizDeAutorizacion() throws Exception {
        UsuarioPrueba usuario = crearUsuario(TipoVehiculo.CARRO);
        SoftAssertions soft = new SoftAssertions();
        for (Fila f : MATRIZ) {
            for (Quien q : Quien.values()) {
                MockHttpServletRequestBuilder req = request(f.metodo(), f.ruta());
                if (f.cuerpo() != null) {
                    req.contentType(MediaType.APPLICATION_JSON).content(f.cuerpo());
                }
                switch (q) {
                    case USUARIO -> req.header("Authorization", bearerUsuario(usuario.id()));
                    case ADMIN -> req.header("Authorization", bearerAdmin());
                    case SISTEMA -> req.header("X-Api-Key", API_KEY);
                    case ANONIMO -> { }
                }
                int st = mvc.perform(req).andReturn().getResponse().getStatus();
                String caso = f.metodo() + " " + f.ruta() + " como " + q + " -> " + st;
                if (f.permitidos().contains(q)) {
                    soft.assertThat(st).as(caso).isNotIn(401, 403);
                } else if (q == Quien.ANONIMO) {
                    soft.assertThat(st).as(caso).isEqualTo(401);
                } else {
                    soft.assertThat(st).as(caso).isEqualTo(403);
                }
            }
        }
        soft.assertAll();
    }

    @Test
    void credencialesInvalidasDan401() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer no.es.un.jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/accesos/lecturas").header("X-Api-Key", "clave-equivocada")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placa\":\"ABC123\"}"))
                .andExpect(status().isUnauthorized());
        // Token firmado con otro secreto.
        String ajeno = io.jsonwebtoken.Jwts.builder().setSubject("1").claim("rol", "ADMIN")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "otro-secreto-de-al-menos-32-bytes-000000000000".getBytes()))
                .compact();
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsYaNoEsPublico() throws Exception {
        mvc.perform(get("/uploads/cualquier.png")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsSoloParaOrigenesConfigurados() throws Exception {
        mvc.perform(options("/api/ping").header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
        mvc.perform(options("/api/ping").header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
