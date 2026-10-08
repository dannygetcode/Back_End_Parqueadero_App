package com.parqueadero.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Falla el arranque con secretos débiles o de ejemplo (N5, RNF-07). El mensaje nombra la variable, nunca el valor.
 * <ul>
 *   <li>Ninguna credencial puede valer {@code change-me} (el marcador de {@code .env.example}).</li>
 *   <li>{@code JWT_SECRET}: 32 bytes o más (además lo exige {@code Keys.hmacShaKeyFor}).</li>
 *   <li>{@code CAMARA_API_KEY}: si se define, 32 bytes o más. Vacía deja el simulador deshabilitado (el filtro no
 *       autentica a nadie).</li>
 *   <li>{@code ADMIN_PASSWORD}: si se define, 12 caracteres o más. Puede quitarse del entorno una vez creado el admin
 *       (si la tabla está vacía y falta, el arranque ya falla en {@code InicializadorAdministrador}).</li>
 * </ul>
 */
@Component
public class ValidacionSecretos {

    static final String MARCADOR = "change-me";
    static final int MIN_BYTES_CLAVE = 32;
    static final int MIN_CARACTERES_ADMIN = 12;

    public ValidacionSecretos(@Value("${jwt.secret:}") String jwtSecret,
                              @Value("${camara.api-key:}") String camaraApiKey,
                              @Value("${admin.username:}") String adminUsuario,
                              @Value("${admin.password:}") String adminPassword,
                              @Value("${spring.datasource.username:}") String dbUsuario,
                              @Value("${spring.datasource.password:}") String dbPassword) {
        List<String> errores = validar(jwtSecret, camaraApiKey, adminUsuario, adminPassword, dbUsuario, dbPassword);
        if (!errores.isEmpty()) {
            throw new IllegalStateException("Configuración insegura: " + String.join("; ", errores));
        }
    }

    static List<String> validar(String jwtSecret, String camaraApiKey, String adminUsuario, String adminPassword,
                                String dbUsuario, String dbPassword) {
        List<String> errores = new ArrayList<>();
        marcador(errores, "JWT_SECRET", jwtSecret);
        marcador(errores, "CAMARA_API_KEY", camaraApiKey);
        marcador(errores, "ADMIN_USERNAME", adminUsuario);
        marcador(errores, "ADMIN_PASSWORD", adminPassword);
        marcador(errores, "DB_USER", dbUsuario);
        marcador(errores, "DB_PASSWORD", dbPassword);
        if (bytes(jwtSecret) < MIN_BYTES_CLAVE) {
            errores.add("JWT_SECRET debe tener al menos " + MIN_BYTES_CLAVE + " bytes");
        }
        if (!vacio(camaraApiKey) && bytes(camaraApiKey) < MIN_BYTES_CLAVE) {
            errores.add("CAMARA_API_KEY debe tener al menos " + MIN_BYTES_CLAVE + " bytes (o quedar vacía para "
                    + "deshabilitar el simulador)");
        }
        if (!vacio(adminPassword) && adminPassword.length() < MIN_CARACTERES_ADMIN) {
            errores.add("ADMIN_PASSWORD debe tener al menos " + MIN_CARACTERES_ADMIN + " caracteres");
        }
        return errores;
    }

    private static void marcador(List<String> errores, String variable, String valor) {
        if (valor != null && MARCADOR.equalsIgnoreCase(valor.trim())) {
            errores.add(variable + " tiene el valor de ejemplo '" + MARCADOR + "'");
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static int bytes(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }
}
