package com.parqueadero.backend.integracion;

import com.parqueadero.backend.entity.Administrador;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF-07: 5 fallos seguidos -> bloqueo de 15 min (423 aunque la credencial sea correcta); luego vuelve a funcionar. */
class BloqueoIT extends PruebaIntegracion {

    @Autowired
    PasswordEncoder encoder;

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
