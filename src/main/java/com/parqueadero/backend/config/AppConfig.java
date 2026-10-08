package com.parqueadero.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Configuración general: reloj inyectable (los tests lo sustituyen para probar vencimiento, bloqueo y
 * anti-rebote) y job programado (ADR 0006). La serialización de Page (via DTO) está en application.properties.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /** Zona de negocio: las fechas (periodos, "hoy") se calculan en hora de Colombia. */
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Bean
    Clock clock() {
        return Clock.system(ZONA);
    }
}
