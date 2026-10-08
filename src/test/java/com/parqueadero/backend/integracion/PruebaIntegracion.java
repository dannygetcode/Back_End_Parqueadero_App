package com.parqueadero.backend.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parqueadero.backend.config.AppConfig;
import com.parqueadero.backend.config.Rol;
import com.parqueadero.backend.entity.TipoVehiculo;
import com.parqueadero.backend.repository.AdministradorRepository;
import com.parqueadero.backend.service.JwtService;
import com.parqueadero.backend.service.OcrService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: contexto completo + MockMvc contra PostgreSQL 15 real (Testcontainers), con
 * Flyway y ddl-auto=validate. Un solo contenedor y un solo contexto para todas las clases (se comparten datos: cada
 * prueba crea sus propios cupos, usuarios y placas aleatorias).
 */
@SpringBootTest(properties = {
        "jwt.secret=prueba-secreto-jwt-de-al-menos-32-bytes-0123456789",
        "admin.username=admin-pruebas",
        "admin.password=clave-admin-pruebas",
        "camara.api-key=clave-camara-pruebas-0123456789abcdef",
        "ocr.service.url=http://127.0.0.1:9",
        "cors.origenes=http://localhost:5500",
        // Todas las peticiones de MockMvc llegan desde 127.0.0.1: el límite por IP se prueba aparte (LimitePorIpIT).
        "seguridad.limite-ip.max-peticiones=100000"
})
@AutoConfigureMockMvc
@Import(PruebaIntegracion.RelojDePruebas.class)
public abstract class PruebaIntegracion {

    protected static final String API_KEY = "clave-camara-pruebas-0123456789abcdef";
    protected static final String PIN = "482915";

    /** PNG mínimo válido (firma + IHDR). */
    protected static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1,
            0, 0, 0, 1, 8, 2, 0, 0, 0};

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");
    static final Path UPLOADS;

    static {
        POSTGRES.start();
        try {
            UPLOADS = Files.createTempDirectory("parqueadero-uploads-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.upload.dir", UPLOADS::toString);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class RelojDePruebas {
        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable(AppConfig.ZONA);
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected RelojAjustable reloj;
    @Autowired
    protected AdministradorRepository administradorRepo;

    @MockitoBean
    protected OcrService ocrService;

    @BeforeEach
    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // ---------------------------------------------------------------------------------------------------------

    protected record UsuarioPrueba(long id, String telefono, String placa, String codigo, long cupoId) {
    }

    protected String bearerAdmin() {
        long id = administradorRepo.findByUsuario("admin-pruebas").orElseThrow().getId();
        return "Bearer " + jwtService.generarToken(id, Rol.ADMIN).token();
    }

    protected String bearerUsuario(long id) {
        return "Bearer " + jwtService.generarToken(id, Rol.USUARIO).token();
    }

    protected JsonNode leer(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected String cuerpo(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    protected long crearCupo(TipoVehiculo tipo) throws Exception {
        String codigo = "T" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
        MvcResult r = mvc.perform(post("/api/cupos").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("codigo", codigo, "tipoVehiculo", tipo.name()))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("id").asLong();
    }

    protected static String telefonoAleatorio() {
        return "3" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
    }

    protected static String placaAleatoria(TipoVehiculo tipo) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append((char) ('A' + rnd.nextInt(26)));
        }
        if (tipo == TipoVehiculo.CARRO) {
            sb.append(String.format("%03d", rnd.nextInt(1000)));
        } else {
            sb.append(String.format("%02d", rnd.nextInt(100))).append((char) ('A' + rnd.nextInt(26)));
        }
        return sb.toString();
    }

    /** Alta por el admin con cupo nuevo del tipo dado. El usuario queda VENCIDO y sin activar. */
    protected UsuarioPrueba crearUsuario(TipoVehiculo tipo) throws Exception {
        long cupoId = crearCupo(tipo);
        String telefono = telefonoAleatorio();
        String placa = placaAleatoria(tipo);
        Map<String, Object> vehiculo = tipo == TipoVehiculo.CARRO
                ? Map.of("placa", placa.substring(0, 3) + "-" + placa.substring(3), "tipoVehiculo", "CARRO",
                "carroceria", "SEDAN", "color", "Rojo")
                : Map.of("placa", placa, "tipoVehiculo", "MOTO", "color", "Negro");
        MvcResult r = mvc.perform(post("/api/usuarios").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", telefono, "nombre", "Ana", "apellido", "Pérez",
                                "cupoId", cupoId, "vehiculo", vehiculo))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode n = leer(r);
        return new UsuarioPrueba(n.at("/usuario/id").asLong(), telefono, placa, n.get("codigoValidacion").asText(), cupoId);
    }

    protected void activar(UsuarioPrueba u) throws Exception {
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", u.codigo(), "pin", PIN,
                                "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))))
                .andExpect(status().isNoContent());
    }

    /** Cortesía de 0 COP registrada por el admin: deja al usuario ACTIVO. */
    protected void cortesia(UsuarioPrueba u) throws Exception {
        mvc.perform(multipart("/api/pagos/manual").header("Authorization", bearerAdmin())
                        .param("usuarioId", String.valueOf(u.id()))
                        .param("montoConfirmado", "0")
                        .param("observacion", "Cortesía de prueba"))
                .andExpect(status().isCreated());
    }

    protected MockMultipartFile comprobante(byte[] contenido, String nombre) {
        return new MockMultipartFile("comprobante", nombre, "image/png", contenido);
    }
}
