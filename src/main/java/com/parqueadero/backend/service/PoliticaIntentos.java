package com.parqueadero.backend.service;

import com.parqueadero.backend.exception.NegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Bloqueo por intentos fallidos (ADR 0003, RF-07), común a usuarios y administrador: al llegar a
 * {@code seguridad.pin.max-intentos} fallos se bloquea {@code seguridad.pin.bloqueo-minutos} y el contador vuelve a 0.
 */
@Component
public class PoliticaIntentos {

    /** Estado del contador de una cuenta tras registrar un fallo. */
    public record Estado(int intentosFallidos, Instant bloqueadoHasta) {
    }

    private final Clock clock;
    private final int maxIntentos;
    private final long bloqueoMinutos;

    public PoliticaIntentos(Clock clock,
                            @Value("${seguridad.pin.max-intentos:5}") int maxIntentos,
                            @Value("${seguridad.pin.bloqueo-minutos:15}") long bloqueoMinutos) {
        this.clock = clock;
        this.maxIntentos = maxIntentos;
        this.bloqueoMinutos = bloqueoMinutos;
    }

    /** 423 si la cuenta sigue bloqueada (no se comprueba la credencial). */
    public void exigirNoBloqueado(Instant bloqueadoHasta) {
        if (estaBloqueado(bloqueadoHasta)) {
            throw NegocioException.bloqueado("Cuenta bloqueada temporalmente por intentos fallidos. Intente más tarde.");
        }
    }

    public boolean estaBloqueado(Instant bloqueadoHasta) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(clock.instant());
    }

    public Estado registrarFallo(int intentosActuales) {
        int intentos = intentosActuales + 1;
        if (intentos >= maxIntentos) {
            return new Estado(0, clock.instant().plus(bloqueoMinutos, ChronoUnit.MINUTES));
        }
        return new Estado(intentos, null);
    }
}
