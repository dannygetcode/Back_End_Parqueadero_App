package com.parqueadero.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con su código HTTP. El {@code detail} va tal cual al cliente (ProblemDetail), así que
 * nunca debe incluir datos sensibles. {@code codigo} es un identificador estable opcional (p. ej. SIN_TARIFA_VIGENTE).
 */
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public NegocioException(HttpStatus status, String detail) {
        this(status, null, detail);
    }

    public NegocioException(HttpStatus status, String codigo, String detail) {
        super(detail);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public static NegocioException noEncontrado(String detail) {
        return new NegocioException(HttpStatus.NOT_FOUND, detail);
    }

    public static NegocioException conflicto(String detail) {
        return new NegocioException(HttpStatus.CONFLICT, detail);
    }

    public static NegocioException conflicto(String codigo, String detail) {
        return new NegocioException(HttpStatus.CONFLICT, codigo, detail);
    }

    public static NegocioException invalido(String detail) {
        return new NegocioException(HttpStatus.BAD_REQUEST, detail);
    }

    public static NegocioException prohibido(String detail) {
        return new NegocioException(HttpStatus.FORBIDDEN, detail);
    }

    public static NegocioException noAutenticado(String detail) {
        return new NegocioException(HttpStatus.UNAUTHORIZED, detail);
    }

    public static NegocioException bloqueado(String detail) {
        return new NegocioException(HttpStatus.LOCKED, detail);
    }

    public static NegocioException tipoNoSoportado(String detail) {
        return new NegocioException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, detail);
    }

    public static NegocioException demasiadoGrande(String detail) {
        return new NegocioException(HttpStatus.PAYLOAD_TOO_LARGE, detail);
    }
}
