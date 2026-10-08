package com.parqueadero.backend.service;

import java.util.Map;

public interface OcrService {

    /**
     * Envía el comprobante al microservicio OCR y devuelve lo que extrajo ({@code fecha}, {@code valor}).
     * Lanza excepción si el servicio no responde a tiempo o falla: quien llama decide (el pago se crea igual).
     */
    Map<String, Object> parse(String filename, byte[] fileBytes);
}
