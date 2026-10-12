package com.parqueadero.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "pronostico.service")
public class PronosticoProperties {

    /** URL base del servicio de pronóstico (p. ej. http://pronostico:8000). Obligatoria (PRONOSTICO_URL). */
    private String url;

    /** Tiempo máximo para conectar. */
    private Duration timeoutConexion = Duration.ofSeconds(2);

    /** Tiempo máximo de lectura de la respuesta. */
    private Duration timeoutLectura = Duration.ofSeconds(8);

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Duration getTimeoutConexion() {
        return timeoutConexion;
    }

    public void setTimeoutConexion(Duration timeoutConexion) {
        this.timeoutConexion = timeoutConexion;
    }

    public Duration getTimeoutLectura() {
        return timeoutLectura;
    }

    public void setTimeoutLectura(Duration timeoutLectura) {
        this.timeoutLectura = timeoutLectura;
    }
}
