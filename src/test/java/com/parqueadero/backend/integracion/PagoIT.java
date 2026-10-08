package com.parqueadero.backend.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PagoIT extends PruebaIntegracion {

    @Test
    void subidaConOcrExitosoPeriodoPropuestoYUnSoloPendiente() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        when(ocrService.parse(anyString(), any())).thenReturn(Map.of("fecha", "2026-10-01", "valor", 80000));

        LocalDate hoy = LocalDate.now(reloj);
        JsonNode pago = leer(mvc.perform(multipart("/api/pagos").file(comprobante(png(1), "pago.png"))
                        .header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.ocrEstado").value("EXITOSO"))
                .andExpect(jsonPath("$.montoOcr").value(80000))
                .andExpect(jsonPath("$.fechaPagoOcr").value("2026-10-01"))
                .andExpect(jsonPath("$.usuarioId").value(u.id()))
                .andExpect(jsonPath("$.registradoPor").value("USUARIO"))
                .andReturn());
        // Sin pagos aprobados, el periodo propuesto empieza hoy; monto = tarifa CARRO vigente.
        assertThat(pago.get("periodoInicio").asText()).isEqualTo(hoy.toString());
        assertThat(pago.get("montoEsperado").asInt()).isEqualTo(hoy.getYear() >= 2026 ? 80000 : 64000);
        assertThat(pago.get("comprobanteUrl").asText()).isEqualTo("/api/pagos/" + pago.get("id").asLong() + "/comprobante");

        // Segundo envío con uno pendiente -> 409.
        mvc.perform(multipart("/api/pagos").file(comprobante(png(2), "otro.png"))
                        .header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isConflict());
    }

    /**
     * N3: el OCR se llama sin transacción abierta y, si dos subidas simultáneas pasan la comprobación previa, el índice
     * uq_pago_pendiente_usuario deja entrar solo una: la otra recibe 409 y su archivo no queda en disco.
     */
    @Test
    void subidasSimultaneasUnaGanaYLaOtraRecibe409SinTransaccionDuranteElOcr() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        AtomicBoolean ocrEnTransaccion = new AtomicBoolean(false);
        CountDownLatch ambasEnOcr = new CountDownLatch(2);
        when(ocrService.parse(anyString(), any())).thenAnswer(inv -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                ocrEnTransaccion.set(true);
            }
            ambasEnOcr.countDown();
            ambasEnOcr.await(10, TimeUnit.SECONDS);
            return Map.of("valor", 80000);
        });
        long antes = archivosSubidos();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                byte[] png = png(100 + i);
                fs.add(pool.submit(() -> mvc.perform(multipart("/api/pagos").file(comprobante(png, "p.png"))
                        .header("Authorization", bearerUsuario(u.id()))).andReturn().getResponse().getStatus()));
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> f : fs) {
                estados.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(estados).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(ocrEnTransaccion).isFalse();
        assertThat(archivosSubidos()).isEqualTo(antes + 1);
        mvc.perform(get("/api/pagos/mios").header("Authorization", bearerUsuario(u.id())))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void ocrCaidoNoImpideCrearElPago() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.MOTO);
        when(ocrService.parse(anyString(), any())).thenThrow(new ResourceAccessException("timeout"));
        mvc.perform(multipart("/api/pagos").file(comprobante(png(3), "m.png"))
                        .header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocrEstado").value("FALLIDO"))
                .andExpect(jsonPath("$.montoOcr").doesNotExist())
                .andExpect(jsonPath("$.montoEsperado").value(LocalDate.now(reloj).getYear() >= 2026 ? 10000 : 8000));
    }

    @Test
    void tipoDeArchivoInvalidoSeRechazaYNoQuedaEnDisco() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        long antes = archivosSubidos();
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};
        mvc.perform(multipart("/api/pagos")
                        .file(comprobante(exe, "../../comprobante.png"))
                        .header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        byte[] pdf = "%PDF-1.4 hola".getBytes();
        mvc.perform(multipart("/api/pagos").file(comprobante(pdf, "c.png")).header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isUnsupportedMediaType());
        byte[] grande = new byte[6 * 1024 * 1024];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        mvc.perform(multipart("/api/pagos").file(comprobante(grande, "g.png")).header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/pagos").header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isBadRequest());
        assertThat(archivosSubidos()).isEqualTo(antes);
    }

    @Test
    void idorUnUsuarioNoVePagosNiComprobantesDeOtro() throws Exception {
        UsuarioPrueba a = crearUsuario(TipoVehiculo.CARRO);
        UsuarioPrueba b = crearUsuario(TipoVehiculo.CARRO);
        byte[] esperado = png(4);
        long pagoA = leer(mvc.perform(multipart("/api/pagos").file(comprobante(esperado, "a.png"))
                        .header("Authorization", bearerUsuario(a.id())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        mvc.perform(get("/api/pagos/" + pagoA).header("Authorization", bearerUsuario(b.id())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/pagos/" + pagoA + "/comprobante").header("Authorization", bearerUsuario(b.id())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/pagos/mios").header("Authorization", bearerUsuario(b.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // El dueño y el admin sí lo ven, con cabeceras de no caché.
        byte[] recibido = mvc.perform(get("/api/pagos/" + pagoA + "/comprobante").header("Authorization", bearerUsuario(a.id())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(recibido).isEqualTo(esperado);
        mvc.perform(get("/api/pagos/" + pagoA + "/comprobante").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pagos/" + pagoA).header("Authorization", bearerUsuario(a.id())))
                .andExpect(status().isOk());
    }

    @Test
    void aprobarActivaAlUsuarioYRechazarExigeMotivo() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        long pago = leer(mvc.perform(multipart("/api/pagos").file(comprobante(png(5), "p.png"))
                        .header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        String admin = bearerAdmin();

        mvc.perform(put("/api/pagos/" + pago + "/rechazar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/pagos/" + pago + "/aprobar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/pagos/" + pago + "/aprobar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"montoConfirmado\":80000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.periodoInicio").value(LocalDate.now(reloj).toString()));
        mvc.perform(get("/api/usuarios/yo").header("Authorization", bearerUsuario(u.id())))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        // Ya no está pendiente.
        mvc.perform(put("/api/pagos/" + pago + "/rechazar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Comprobante ilegible\"}"))
                .andExpect(status().isConflict());

        // El siguiente periodo continúa sin hueco.
        LocalDate finPrimero = LocalDate.parse(leer(mvc.perform(get("/api/pagos/" + pago)
                .header("Authorization", admin)).andReturn()).get("periodoFin").asText());
        mvc.perform(get("/api/pagos/proximo-periodo").header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value(finPrimero.plusDays(1).toString()));
    }

    @Test
    void pagoManualSoloCortesiaDeCero() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        String admin = bearerAdmin();
        mvc.perform(multipart("/api/pagos/manual").header("Authorization", admin)
                        .param("usuarioId", String.valueOf(u.id())).param("montoConfirmado", "80000")
                        .param("observacion", "Efectivo"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/pagos/manual").header("Authorization", admin)
                        .param("usuarioId", String.valueOf(u.id())).param("montoConfirmado", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/pagos/manual").header("Authorization", admin)
                        .param("usuarioId", String.valueOf(u.id())).param("montoConfirmado", "0")
                        .param("observacion", "Cortesía inauguración"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.ocrEstado").value("NO_APLICA"))
                .andExpect(jsonPath("$.registradoPor").value("ADMIN"));
        mvc.perform(get("/api/usuarios/" + u.id()).header("Authorization", admin))
                .andExpect(jsonPath("$.usuario.estado").value("ACTIVO"));
    }

    @Test
    void listadoAdminPaginadoConMaximo100() throws Exception {
        mvc.perform(get("/api/pagos?size=500").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    /** PNG distinto en cada llamada (sha256 distinto) para no chocar con la detección de duplicados. */
    private static byte[] png(int semilla) {
        byte[] b = Arrays.copyOf(PNG, PNG.length + 16);
        long ruido = System.nanoTime() ^ semilla;
        for (int i = 0; i < 8; i++) {
            b[PNG.length + i] = (byte) (ruido >>> (8 * i));
        }
        b[PNG.length + 8] = (byte) semilla;
        return b;
    }

    private static long archivosSubidos() throws Exception {
        if (!Files.exists(UPLOADS)) {
            return 0;
        }
        try (Stream<java.nio.file.Path> s = Files.walk(UPLOADS)) {
            return s.filter(Files::isRegularFile).count();
        }
    }
}
