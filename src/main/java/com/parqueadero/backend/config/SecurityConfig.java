package com.parqueadero.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/camaras/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/camaras/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/camaras/**").permitAll()
                        .requestMatchers(
                                "/api/ping",
                                "/uploads/**",
                                "/api/usuarios/solicitar-validacion",
                                "/api/usuarios/validar",
                                "/api/usuarios/login",
                                "/api/pagos/**",
                                "/api/admin/login",
                                "/api/usuarios/verificar-codigo",
                                "/api/usuarios/registro")
                        .permitAll()
                        .requestMatchers("/api/usuarios/**").authenticated()
                        .requestMatchers("/api/puerta/**").authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                List.of("http://127.0.0.1:5500",
                        "http://localhost:5500",
                        "https://192.168.1.6:8080", 
                        "https://192.168.1.2:8080", 
                        "http://localhost:3000", 
                        "http://10.0.2.2:8080",
                        "http://192.168.1.3:8080"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
