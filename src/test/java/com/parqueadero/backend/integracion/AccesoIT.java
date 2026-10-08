package com.parqueadero.backend.integracion;

import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR 0004 / RF-39: inferencia, anti-rebote, reglas de entrada, salida siempre permitida, puerta. */
class AccesoIT extends PruebaIntegracion {

    private static final Duration PASO = Duration.ofSeconds(61);

    @Test
    void inferenciaEntradaSalidaYAntirrebote() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        cortesia(u);

        camara(u.placa().toLowerCase()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.tipoInferido").value(true))
                .andExpect(jsonPath("$.resultado").value("PERMITIDO"))
                .andExpect(jsonPath("$.origen").value("CAMARA"))
                .andExpect(jsonPath("$.puertaAbierta").value(true))
                .andExpect(jsonPath("$.duplicado").value(false))
                .andExpect(jsonPath("$.usuarioId").value(u.id()));

        // Segunda lectura dentro de la ventana anti-rebote: mismo evento, duplicado.
        camara(u.placa()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.duplicado").value(true));

        // Está dentro: aparece en la ocupación.
        mvc.perform(get("/api/accesos/ocupacion").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiculosDentro[*].placa", hasItem(u.placa())));

        reloj.adelantar(PASO);
        camara(u.placa()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("SALIDA"))
                .andExpect(jsonPath("$.resultado").value("PERMITIDO"));

        reloj.adelantar(PASO);
        camara(u.placa()).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("ENTRADA"));
    }

    @Test
    void entradaRepetidaConVehiculoDentroSeDeniegaYQuedaRegistrada() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        cortesia(u);
        camara(u.placa()).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("ENTRADA"));

        reloj.adelantar(PASO);
        // El admin fuerza tipo=ENTRADA desde el panel con el vehículo ya dentro.
        mvc.perform(post("/api/accesos/lecturas").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("placa", u.placa(), "tipo", "ENTRADA"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultado").value("DENEGADO"))
                .andExpect(jsonPath("$.motivo").value("YA_DENTRO"))
                .andExpect(jsonPath("$.tipoInferido").value(false));

        mvc.perform(get("/api/accesos").param("placa", u.placa()).param("resultado", "DENEGADO")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].motivo").value("YA_DENTRO"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void reglasDeEntradaYSalidaSiemprePermitida() throws Exception {
        // Usuario VENCIDO (sin pagos): no entra.
        UsuarioPrueba vencido = crearUsuario(TipoVehiculo.MOTO);
        camara(vencido.placa()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.motivo").value("USUARIO_VENCIDO"));

        // El admin lo deja entrar a mano: obligatorio justificarlo; queda PERMITIDO con FORZADO_ADMIN.
        mvc.perform(put("/api/puerta").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("abierta", true, "placa", vencido.placa()))))
                .andExpect(status().isBadRequest());
        reloj.adelantar(PASO);
        mvc.perform(put("/api/puerta").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("abierta", true, "placa", vencido.placa(),
                                "observacion", "Viene a pagar en efectivo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abierta").value(true))
                .andExpect(jsonPath("$.evento.resultado").value("PERMITIDO"))
                .andExpect(jsonPath("$.evento.motivo").value("FORZADO_ADMIN"))
                .andExpect(jsonPath("$.evento.origen").value("MANUAL_ADMIN"));

        // Sale aunque esté vencido.
        reloj.adelantar(PASO);
        camara(vencido.placa()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("SALIDA"))
                .andExpect(jsonPath("$.resultado").value("PERMITIDO"))
                .andExpect(jsonPath("$.motivo").value("SALIDA_CON_DEUDA"));

        // Suspendido: no entra.
        UsuarioPrueba suspendido = crearUsuario(TipoVehiculo.CARRO);
        cortesia(suspendido);
        mvc.perform(put("/api/usuarios/" + suspendido.id() + "/estado").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accion\":\"SUSPENDER\",\"motivo\":\"Prueba\"}"))
                .andExpect(status().isOk());
        camara(suspendido.placa()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("USUARIO_SUSPENDIDO"));

        // Placa desconocida: se registra sin vehículo ni usuario.
        camara("ZZZ" + (100 + (int) (Math.random() * 899))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("PLACA_DESCONOCIDA"))
                .andExpect(jsonPath("$.usuarioId").doesNotExist());
    }

    @Test
    void validacionesDeLaLectura() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        // SISTEMA no puede indicar el tipo.
        mvc.perform(post("/api/accesos/lecturas").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("placa", u.placa(), "tipo", "SALIDA"))))
                .andExpect(status().isBadRequest());
        // ocurridoEn en el futuro o demasiado antiguo -> 400.
        mvc.perform(post("/api/accesos/lecturas").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("placa", u.placa(),
                                "ocurridoEn", OffsetDateTime.now().minusHours(2).toString()))))
                .andExpect(status().isBadRequest());
        // Cámara inexistente -> 409.
        mvc.perform(post("/api/accesos/lecturas").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("placa", u.placa(), "camaraId", 999999))))
                .andExpect(status().isConflict());
    }

    @Test
    void puertaUsuarioYPrivacidadDelUltimoEvento() throws Exception {
        // La BD es compartida y otras pruebas dejan eventos "en el futuro" (adelantan el reloj): esta prueba se sitúa
        // después para que su evento sea el último del parqueadero.
        reloj.adelantar(Duration.ofDays(2));
        UsuarioPrueba a = crearUsuario(TipoVehiculo.CARRO);
        UsuarioPrueba b = crearUsuario(TipoVehiculo.CARRO);
        cortesia(a);

        // A abre desde la app: la placa sale de su vehículo, origen APP_USUARIO.
        mvc.perform(put("/api/puerta").header("Authorization", bearerUsuario(a.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":true,\"placa\":\"OTRA99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento.placaLeida").value(a.placa()))
                .andExpect(jsonPath("$.evento.origen").value("APP_USUARIO"))
                .andExpect(jsonPath("$.abierta").value(true));

        mvc.perform(get("/api/puerta").header("Authorization", bearerUsuario(a.id())))
                .andExpect(jsonPath("$.ultimoEvento.placaLeida").value(a.placa()));
        mvc.perform(get("/api/puerta").header("Authorization", bearerUsuario(b.id())))
                .andExpect(jsonPath("$.ultimoEvento").doesNotExist());

        // B está VENCIDO: su apertura se deniega con 403 y queda registrada.
        mvc.perform(put("/api/puerta").header("Authorization", bearerUsuario(b.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.evento.motivo").value("USUARIO_VENCIDO"));

        // Solo el admin cierra.
        mvc.perform(put("/api/puerta").header("Authorization", bearerUsuario(a.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/puerta").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"abierta\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abierta").value(false));
    }

    @Test
    void antirreboteNoReutilizaEventoDeOtroOrigenNiAnteriorAUnCambioDelUsuario() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        // VENCIDO: la cámara recibe una denegación.
        camara(u.placa()).andExpect(status().isForbidden()).andExpect(jsonPath("$.motivo").value("USUARIO_VENCIDO"));
        // El estado cambia (cortesía -> ACTIVO) dentro de la ventana: se evalúa de nuevo, no es duplicado.
        cortesia(u);
        camara(u.placa()).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("PERMITIDO"))
                .andExpect(jsonPath("$.duplicado").value(false));
        // Mismo vehículo, otro origen (apertura manual del admin) dentro de la ventana: se evalúa de nuevo.
        mvc.perform(put("/api/puerta").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("abierta", true, "placa", u.placa(), "tipo", "SALIDA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento.origen").value("MANUAL_ADMIN"))
                .andExpect(jsonPath("$.evento.duplicado").value(false));
    }

    private ResultActions camara(String placa) throws Exception {
        return mvc.perform(post("/api/accesos/lecturas").header("X-Api-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("placa", placa, "camaraId", 1))));
    }
}
