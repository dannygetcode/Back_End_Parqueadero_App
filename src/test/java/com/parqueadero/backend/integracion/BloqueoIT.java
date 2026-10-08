package com.parqueadero.backend.integracion;

import com.parqueadero.backend.entity.Administrador;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF-07: 5 fallos seguidos -> bloqueo de 15 min (423 aunque la credencial sea correcta); luego vuelve a funcionar. */
class BloqueoIT extends PruebaIntegracion {

    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void usuarioSeBloqueaTrasCincoFallosYSeDesbloqueaALos15Minutos() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        activar(u);
        for (int i = 0; i < 5; i++) {
            loginUsuario(u.telefono(), "739104").andExpect(status().isUnauthorized());
        }
        loginUsuario(u.telefono(), PIN).andExpect(status().isLocked());

        reloj.adelantar(Duration.ofMinutes(14));
        loginUsuario(u.telefono(), PIN).andExpect(status().isLocked());

        reloj.adelantar(Duration.ofMinutes(2));
        loginUsuario(u.telefono(), PIN).andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
    }

    /**
     * N1: 20 intentos fallidos simultáneos. Con el contador atómico (SELECT ... FOR UPDATE) exactamente 5 se evalúan
     * (401) y bloquean la cuenta; los otros 15 ya la encuentran bloqueada (423). Sin el bloqueo de fila, varios hilos
     * leían el mismo contador y se colaban más de 5 comprobaciones del PIN.
     */
    @Test
    void intentosFallidosSimultaneosNoPierdenIncrementos() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        activar(u);
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < hilos; i++) {
                resultados.add(pool.submit(() -> {
                    salida.await();
                    return loginUsuario(u.telefono(), "739104").andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            Map<Integer, Integer> porEstado = new HashMap<>();
            for (Future<Integer> f : resultados) {
                porEstado.merge(f.get(60, TimeUnit.SECONDS), 1, Integer::sum);
            }
            assertThat(porEstado).containsEntry(401, 5).containsEntry(423, 15).hasSize(2);
        } finally {
            pool.shutdownNow();
        }
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT intentos_fallidos, bloqueado_hasta FROM usuario WHERE id = ?", u.id());
        assertThat(fila.get("bloqueado_hasta")).isNotNull();
        assertThat(((Number) fila.get("intentos_fallidos")).intValue()).isZero();
        loginUsuario(u.telefono(), PIN).andExpect(status().isLocked());
    }

    @Test
    void unAccesoCorrectoReiniciaElContador() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        activar(u);
        for (int i = 0; i < 4; i++) {
            loginUsuario(u.telefono(), "739104").andExpect(status().isUnauthorized());
        }
        loginUsuario(u.telefono(), PIN).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            loginUsuario(u.telefono(), "739104").andExpect(status().isUnauthorized());
        }
        loginUsuario(u.telefono(), PIN).andExpect(status().isOk());
    }

    @Test
    void codigoDeActivacionTambienCuentaIntentos() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.MOTO);
        for (int i = 0; i < 5; i++) {
            activarCon(u, "XXXXXXXX").andExpect(status().isUnauthorized());
        }
        activarCon(u, u.codigo()).andExpect(status().isLocked());
    }

    @Test
    void administradorSeBloqueaIgual() throws Exception {
        // Un segundo administrador solo para esta prueba, para no bloquear al que usan las demás.
        Administrador a = new Administrador();
        String usuario = "adm-" + UUID.randomUUID().toString().substring(0, 8);
        a.setUsuario(usuario);
        a.setPasswordHash(encoder.encode("clave-correcta"));
        administradorRepo.save(a);

        loginAdmin(usuario, "clave-correcta").andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMIN"));
        for (int i = 0; i < 5; i++) {
            loginAdmin(usuario, "mala").andExpect(status().isUnauthorized());
        }
        loginAdmin(usuario, "clave-correcta").andExpect(status().isLocked());
        reloj.adelantar(Duration.ofMinutes(16));
        loginAdmin(usuario, "clave-correcta").andExpect(status().isOk());
    }

    /** N2: un teléfono o un admin inexistente responde igual que uno real: 5 x 401 y después 423. */
    @Test
    void cuentasInexistentesSeComportanIgualQueLasReales() throws Exception {
        String telefono = telefonoAleatorio();
        for (int i = 0; i < 3; i++) {
            loginUsuario(telefono, "739104").andExpect(status().isUnauthorized());
        }
        // Login y activación comparten el contador, como en la fila de un usuario real.
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(Map.of("telefono", telefono, "codigo", "XXXXXXXX", "pin", PIN,
                                    "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))))
                    .andExpect(status().isUnauthorized());
        }
        loginUsuario(telefono, PIN).andExpect(status().isLocked());
        reloj.adelantar(Duration.ofMinutes(16));
        loginUsuario(telefono, PIN).andExpect(status().isUnauthorized());

        String admin = "no-existe-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            loginAdmin(admin, "mala").andExpect(status().isUnauthorized());
        }
        loginAdmin(admin, "mala").andExpect(status().isLocked());
    }

    /** Una cuenta creada pero sin activar también acumula fallos de login (y se bloquea) en su fila. */
    @Test
    void loginDeCuentaSinActivarCuentaComoFallo() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.MOTO);
        for (int i = 0; i < 5; i++) {
            loginUsuario(u.telefono(), PIN).andExpect(status().isUnauthorized());
        }
        loginUsuario(u.telefono(), PIN).andExpect(status().isLocked());
    }

    private org.springframework.test.web.servlet.ResultActions loginUsuario(String tel, String pin) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("telefono", tel, "pin", pin))));
    }

    private org.springframework.test.web.servlet.ResultActions activarCon(UsuarioPrueba u, String codigo) throws Exception {
        return mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("telefono", u.telefono(), "codigo", codigo, "pin", PIN,
                        "aceptaTratamientoDatos", true, "versionConsentimiento", "2026-10"))));
    }

    private org.springframework.test.web.servlet.ResultActions loginAdmin(String usuario, String password) throws Exception {
        return mvc.perform(post("/api/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("username", usuario, "password", password))));
    }
}
