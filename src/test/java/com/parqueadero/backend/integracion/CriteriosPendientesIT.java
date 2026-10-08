package com.parqueadero.backend.integracion;

import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación que no tenían prueba: HU-A01 (moto con carrocería), HU-A06, RF-26 y RF-09/HU-U03. */
class CriteriosPendientesIT extends PruebaIntegracion {

    @Test
    void motoConCarroceriaDa400() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", bearerAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", telefonoAleatorio(), "nombre", "B", "apellido", "C",
                                "cupoId", crearCupo(TipoVehiculo.MOTO), "vehiculo", Map.of("placa",
                                        placaAleatoria(TipoVehiculo.MOTO), "tipoVehiculo", "MOTO",
                                        "carroceria", "SEDAN", "color", "Negro")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reinicioDePinPorElAdmin() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        activar(u);
        for (int i = 0; i < 5; i++) {
            login(u.telefono(), "739104").andExpect(status().isUnauthorized());
        }
        login(u.telefono(), PIN).andExpect(status().isLocked());

        String nuevo = leer(mvc.perform(post("/api/usuarios/" + u.id() + "/codigo").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").isString())
                .andExpect(jsonPath("$.expiraEn").isString())
                .andReturn()).get("codigo").asText();

        // El bloqueo se levanta y el PIN anterior sigue valiendo hasta usar el código nuevo.
        login(u.telefono(), PIN).andExpect(status().isOk());
        // El código anterior ya no activa.
        activarCon(u.telefono(), u.codigo(), "381946").andExpect(status().isUnauthorized());
        activarCon(u.telefono(), nuevo, "381946").andExpect(status().isNoContent());
        login(u.telefono(), PIN).andExpect(status().isUnauthorized());
        login(u.telefono(), "381946").andExpect(status().isOk());
    }

    @Test
    void mismoComprobanteDelMismoUsuarioDa409SalvoQueElAnteriorEsteRechazado() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        byte[] png = Arrays.copyOf(PNG, PNG.length + 8);
        long semilla = System.nanoTime();
        for (int i = 0; i < 8; i++) {
            png[PNG.length + i] = (byte) (semilla >>> (8 * i));
        }
        long pago = leer(subir(u, png).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(put("/api/pagos/" + pago + "/aprobar").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"montoConfirmado\":80000}"))
                .andExpect(status().isOk());
        // Ya no hay pendiente: el 409 es por el SHA-256 repetido.
        subir(u, png).andExpect(status().isConflict());

        UsuarioPrueba otro = crearUsuario(TipoVehiculo.CARRO);
        long ajeno = leer(subir(otro, png).andExpect(status().isCreated())
                .andExpect(jsonPath("$.posibleDuplicado").value(true)).andReturn()).get("id").asLong();
        mvc.perform(put("/api/pagos/" + ajeno + "/rechazar").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Comprobante de otra persona\"}"))
                .andExpect(status().isOk());
        // Rechazado: puede volver a enviarlo (no cuenta como duplicado propio).
        subir(otro, png).andExpect(status().isCreated());
    }

    @Test
    void usuarioSuspendidoNoPuedeSubirComprobante() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.MOTO);
        mvc.perform(put("/api/usuarios/" + u.id() + "/estado").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"SUSPENDER\",\"motivo\":\"Prueba\"}"))
                .andExpect(status().isOk());
        subir(u, PNG).andExpect(status().isForbidden());
    }

    private ResultActions subir(UsuarioPrueba u, byte[] png) throws Exception {
        return mvc.perform(multipart("/api/pagos").file(comprobante(png, "c.png"))
                .header("Authorization", bearerUsuario(u.id())));
    }

    private ResultActions login(String telefono, String pin) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("telefono", telefono, "pin", pin))));
    }

    private ResultActions activarCon(String telefono, String codigo, String pin) throws Exception {
        return mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("telefono", telefono, "codigo", codigo, "pin", pin,
                        "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))));
    }
}
