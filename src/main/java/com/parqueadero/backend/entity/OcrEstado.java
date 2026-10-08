package com.parqueadero.backend.entity;

/** Resultado del OCR del comprobante. NO_APLICA: pago sin comprobante. */
public enum OcrEstado {
    EXITOSO, PARCIAL, FALLIDO, NO_APLICA
}
