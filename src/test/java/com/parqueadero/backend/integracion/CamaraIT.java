package com.parqueadero.backend.integracion;

import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** N4 / RF-45: el USUARIO no recibe la url de las cámaras y la url no admite credenciales (userinfo). */
class CamaraIT extends PruebaIntegracion {

    @Test
    void elUsuarioNoVeLaUrlYElAdminSi() throws Exception {
        String url = "http://camara-entrada.local:8554/stream";
        mvc.perform(post("/api/camaras").header("Authorization", bearerAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "Entrada IT", "url", url))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(url));

        mvc.perform(get("/api/camaras").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].url", hasItem(url)));

        UsuarioPrueba u = crearUsuario(TipoVehiculo.MOTO);
        String respuesta = mvc.perform(get("/api/camaras").header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*]", everyItem(not(hasKey("url")))))
                .andExpect(jsonPath("$[*]", everyItem(not(hasKey("simulada")))))
                .andExpect(jsonPath("$[*].nombre", hasItem("Entrada IT")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(respuesta).doesNotContain("camara-entrada.local");
    }

    @Test
    void urlConUsuarioOContrasenaSeRechaza() throws Exception {
        for (String url : new String[]{"http://admin:clave@camara.local/stream", "https://admin@camara.local",
                "rtsp://camara.local/stream", "http://"}) {
            mvc.perform(post("/api/camaras").header("Authorization", bearerAdmin()).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(Map.of("nombre", "Con credenciales", "url", url))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("url"));
        }
        mvc.perform(post("/api/camaras").header("Authorization", bearerAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "Con arroba en la ruta", "url", "https://camara.local/a@b"))))
                .andExpect(status().isCreated());
    }
}
