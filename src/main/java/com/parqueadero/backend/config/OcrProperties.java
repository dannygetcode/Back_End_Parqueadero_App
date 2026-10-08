package com.parqueadero.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ocr.service")
public class OcrProperties {

    /** URL base del microservicio OCR (p. ej. http://ocr:5000). */
    private String url;

    /** Tiempo máximo de conexión y de lectura. Si se supera, el pago se crea con ocr_estado = FALLIDO. */
    private Duration timeout = Duration.ofSeconds(10);

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
