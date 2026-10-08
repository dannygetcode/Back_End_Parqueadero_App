package com.parqueadero.backend.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Todas las respuestas de error son ProblemDetail (RFC 9457) con mensajes en español, sin trazas ni SQL.
 * Las excepciones estándar de Spring MVC (400, 404, 405, 413, 415...) las resuelve la clase base.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail negocio(NegocioException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        if (ex.getCodigo() != null) {
            pd.setProperty("codigo", ex.getCodigo());
        }
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail restriccion(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        pd.setProperty("errores", ex.getConstraintViolations().stream()
                .map(v -> Map.of("campo", String.valueOf(v.getPropertyPath()), "mensaje", v.getMessage()))
                .toList());
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoArgumento(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Valor inválido para '" + ex.getName() + "'");
    }

    /** Carreras contra los índices únicos parciales (cupo, placa, teléfono, pago pendiente). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex) {
        log.info("Violación de integridad: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La operación entra en conflicto con datos existentes");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tiene permiso para esta operación");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception ex) {
        String id = UUID.randomUUID().toString();
        log.error("Error no controlado [{}]", id, ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno. Referencia: " + id);
        pd.setProperty("referencia", id);
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(this::error)
                .toList();
        List<Map<String, String>> globales = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> Map.of("campo", e.getObjectName(), "mensaje", String.valueOf(e.getDefaultMessage())))
                .toList();
        pd.setProperty("errores", errores.isEmpty() ? globales : errores);
        return ResponseEntity.badRequest().body(pd);
    }

    /** JSON ilegible o con un valor inválido (p. ej. un enum desconocido): 400 en español, con el campo si se conoce. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es válido");
        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String campo = mie.getPath().stream()
                    .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                    .reduce((a, b) -> b.startsWith("[") ? a + b : a + "." + b).orElse("");
            String mensaje = "Valor inválido";
            if (mie instanceof InvalidFormatException ife && ife.getTargetType() != null
                    && ife.getTargetType().isEnum()) {
                mensaje += "; valores permitidos: " + String.join(", ",
                        Arrays.stream(ife.getTargetType().getEnumConstants()).map(Object::toString).toList());
            }
            pd.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
        }
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NonNull HandlerMethodValidationException ex,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        pd.setProperty("errores", ex.getAllErrors().stream()
                .map(e -> Map.of("campo", e instanceof FieldError fe ? fe.getField() : "parametro",
                        "mensaje", String.valueOf(e.getDefaultMessage())))
                .toList());
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(@NonNull MaxUploadSizeExceededException ex,
                                                                          @NonNull HttpHeaders headers,
                                                                          @NonNull HttpStatusCode status,
                                                                          @NonNull WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido (5 MB)");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(pd);
    }

    private Map<String, String> error(FieldError fe) {
        return Map.of("campo", fe.getField(), "mensaje", String.valueOf(fe.getDefaultMessage()));
    }
}
