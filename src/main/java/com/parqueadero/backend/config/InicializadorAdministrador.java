package com.parqueadero.backend.config;

import com.parqueadero.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, si la tabla administrador está vacía, crea el admin con ADMIN_USERNAME/ADMIN_PASSWORD guardando solo
 * el hash BCrypt. Si ya hay uno, las variables se ignoran (cambiar la contraseña requiere actualizar la fila).
 * Si la tabla está vacía y faltan las variables, la aplicación no arranca.
 */
@Component
public class InicializadorAdministrador implements ApplicationRunner {

    private final AuthService authService;
    private final String usuario;
    private final String password;

    public InicializadorAdministrador(AuthService authService,
                                      @Value("${admin.username:}") String usuario,
                                      @Value("${admin.password:}") String password) {
        this.authService = authService;
        this.usuario = usuario;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.crearAdministradorInicialSiFalta(usuario, password);
    }
}
