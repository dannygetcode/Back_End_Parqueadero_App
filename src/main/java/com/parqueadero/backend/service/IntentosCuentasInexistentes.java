package com.parqueadero.backend.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Respuesta uniforme para cuentas que no existen (ADR 0003): un teléfono o usuario de admin desconocido se comporta
 * igual que uno real (401 genérico, y 423 tras {@code seguridad.pin.max-intentos} fallos durante
 * {@code seguridad.pin.bloqueo-minutos}), para que la diferencia 401/423 no revele qué cuentas existen.
 *
 * El contador vive en memoria (no se crean filas por identificadores inventados) y se pierde al reiniciar; las
 * entradas se descartan cuando han pasado {@code bloqueo-minutos} sin fallos. Está acotado a {@link #MAX_CLAVES}.
 */
@Component
public class IntentosCuentasInexistentes {

    static final int MAX_CLAVES = 10_000;

    private record Entrada(int intentos, Instant bloqueadoHasta, Instant ultimoFallo) {
    }

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final PoliticaIntentos politica;
    private final Clock clock;

    public IntentosCuentasInexistentes(PoliticaIntentos politica, Clock clock) {
        this.politica = politica;
        this.clock = clock;
    }

    /** 423 si el identificador inexistente está "bloqueado", igual que una cuenta real. */
    public void exigirNoBloqueado(String clave) {
        Entrada e = entradas.get(clave);
        politica.exigirNoBloqueado(e == null ? null : e.bloqueadoHasta());
    }

    public void registrarFallo(String clave) {
        Instant ahora = clock.instant();
        if (entradas.size() >= MAX_CLAVES) {
            Instant limite = ahora.minus(politica.getBloqueoMinutos(), ChronoUnit.MINUTES);
            entradas.values().removeIf(e -> e.ultimoFallo().isBefore(limite) && !politica.estaBloqueado(e.bloqueadoHasta()));
            if (entradas.size() >= MAX_CLAVES) {
                entradas.clear();
            }
        }
        entradas.compute(clave, (k, e) -> {
            PoliticaIntentos.Estado nuevo = politica.registrarFallo(e == null ? 0 : e.intentos());
            return new Entrada(nuevo.intentosFallidos(), nuevo.bloqueadoHasta(), ahora);
        });
    }
}
