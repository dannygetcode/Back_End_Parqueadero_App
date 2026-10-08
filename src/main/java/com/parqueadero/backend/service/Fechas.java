package com.parqueadero.backend.service;

import com.parqueadero.backend.config.AppConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Conversión de instantes (timestamptz) a la zona de negocio para las respuestas (RNF-28). */
public final class Fechas {

    private Fechas() {
    }

    public static OffsetDateTime local(Instant instante) {
        return instante == null ? null : instante.atZone(AppConfig.ZONA).toOffsetDateTime();
    }

    public static Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(AppConfig.ZONA).toInstant();
    }
}
