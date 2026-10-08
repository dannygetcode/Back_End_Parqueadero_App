package com.parqueadero.backend.service;

import com.parqueadero.backend.entity.OcrEstado;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Convierte la respuesta del servicio OCR ({fecha, valor}) en monto, fecha y estado (RF-25). */
public final class InterpreteOcr {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d 'de' MMMM 'de' uuuu").toFormatter(ES),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(ES),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu").toFormatter(Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"));

    private InterpreteOcr() {
    }

    public record Resultado(OcrEstado estado, Integer monto, LocalDate fecha, Map<String, Object> datos) {
    }

    public static Resultado fallido() {
        return new Resultado(OcrEstado.FALLIDO, null, null, null);
    }

    public static Resultado interpretar(Map<String, Object> respuesta) {
        if (respuesta == null || respuesta.isEmpty()) {
            return fallido();
        }
        Integer monto = monto(respuesta.get("valor"));
        LocalDate fecha = fecha(respuesta.get("fecha"));
        OcrEstado estado = (monto != null && fecha != null) ? OcrEstado.EXITOSO
                : (monto != null || fecha != null) ? OcrEstado.PARCIAL
                : OcrEstado.FALLIDO;
        // Solo se guardan los campos extraídos, nunca el texto completo del comprobante.
        Map<String, Object> datos = new LinkedHashMap<>();
        if (respuesta.get("fecha") != null) {
            datos.put("fecha", String.valueOf(respuesta.get("fecha")));
        }
        if (respuesta.get("valor") != null) {
            datos.put("valor", String.valueOf(respuesta.get("valor")));
        }
        return new Resultado(estado, monto, fecha, datos.isEmpty() ? null : datos);
    }

    static Integer monto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Number n) {
            long l = n.longValue();
            return (l >= 0 && l <= Integer.MAX_VALUE) ? (int) l : null;
        }
        String digitos = valor.toString().replaceAll("[^0-9]", "");
        if (digitos.isEmpty() || digitos.length() > 9) {
            return null;
        }
        return Integer.parseInt(digitos);
    }

    static LocalDate fecha(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return null;
        }
        String texto = valor.toString().trim().toLowerCase(ES);
        for (DateTimeFormatter f : FORMATOS) {
            try {
                return LocalDate.parse(texto, f);
            } catch (DateTimeParseException ignored) {
                // probar el siguiente formato
            }
        }
        return null;
    }
}
