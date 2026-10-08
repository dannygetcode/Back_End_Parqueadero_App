package com.parqueadero.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parqueadero.backend.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Autorización por rol (ADR 0003, tabla de endpoints en docs/arquitectura/fase-1-modelo.md §5).
 * La comprobación de dueño (pago o evento de otro usuario) se hace en los servicios y responde 404.
 *
 * CSRF desactivado a propósito: API stateless con Bearer/API key y sin cookies de sesión. La protección CSRF
 * irá en el BFF del panel Next.js (fase posterior).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN = Rol.ADMIN.name();
    private static final String USUARIO = Rol.USUARIO.name();
    private static final String SISTEMA = Rol.SISTEMA.name();

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtService jwtService,
                                    ObjectMapper objectMapper,
                                    @Value("${camara.api-key:}") String camaraApiKey) throws Exception {
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, e) -> escribirProblema(res, objectMapper,
                                HttpStatus.UNAUTHORIZED, "Autenticación requerida"))
                        .accessDeniedHandler((req, res, e) -> escribirProblema(res, objectMapper,
                                HttpStatus.FORBIDDEN, "No tiene permiso para esta operación")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Públicos
                        .requestMatchers(HttpMethod.POST, "/api/admin/login", "/api/auth/login", "/api/auth/activar")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ping", "/api/legal/aviso-privacidad").permitAll()
                        // Simulador de cámara (SISTEMA) y panel (ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/accesos/lecturas").hasAnyRole(SISTEMA, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/accesos/mios").hasRole(USUARIO)
                        .requestMatchers("/api/accesos", "/api/accesos/**").hasRole(ADMIN)
                        // Usuarios
                        .requestMatchers("/api/usuarios/yo", "/api/usuarios/yo/**").hasRole(USUARIO)
                        .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole(ADMIN)
                        // Cupos y tarifas
                        .requestMatchers("/api/cupos", "/api/cupos/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/tarifas").hasAnyRole(ADMIN, USUARIO)
                        .requestMatchers("/api/tarifas", "/api/tarifas/**").hasRole(ADMIN)
                        // Pagos
                        .requestMatchers(HttpMethod.GET, "/api/pagos/proximo-periodo", "/api/pagos/mios").hasRole(USUARIO)
                        .requestMatchers(HttpMethod.POST, "/api/pagos").hasRole(USUARIO)
                        .requestMatchers(HttpMethod.GET, "/api/pagos/{id}", "/api/pagos/{id}/comprobante")
                        .hasAnyRole(ADMIN, USUARIO)
                        .requestMatchers("/api/pagos", "/api/pagos/**").hasRole(ADMIN)
                        // Puerta y cámaras
                        .requestMatchers("/api/puerta").hasAnyRole(ADMIN, USUARIO)
                        .requestMatchers(HttpMethod.GET, "/api/camaras").hasAnyRole(ADMIN, USUARIO)
                        .requestMatchers("/api/camaras", "/api/camaras/**").hasRole(ADMIN)
                        .anyRequest().authenticated())
                .addFilterBefore(new ApiKeyAuthenticationFilter(camaraApiKey), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new JwtAuthenticationFilter(jwtService), ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /** Orígenes desde CORS_ORIGENES (lista separada por comas). Sin comodín ni IPs en el código. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.origenes:http://localhost:5500,http://localhost:3000}") String origenes) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("*"))
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", ApiKeyAuthenticationFilter.CABECERA));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void escribirProblema(HttpServletResponse res, ObjectMapper om, HttpStatus status, String detalle)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        om.writeValue(res.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detalle));
    }
}
