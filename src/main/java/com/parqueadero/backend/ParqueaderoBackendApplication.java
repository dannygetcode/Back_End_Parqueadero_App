package com.parqueadero.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Sin UserDetailsService: la autenticación es por JWT (admin/usuario) y API key (simulador). Se excluye la
 * autoconfiguración que crearía un usuario en memoria con contraseña generada (y la imprimiría en el log).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ParqueaderoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ParqueaderoBackendApplication.class, args);
	}

}
