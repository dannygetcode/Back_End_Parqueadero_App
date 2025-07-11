package com.parqueadero.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ocr.service")
public class OcrProperties {
    /**
     * URL base de tu microservicio OCR (p.e. http://localhost:5000)
     */
    private String url;

    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
}
