package com.parqueadero.backend.analitica;

import com.parqueadero.backend.entity.TipoVehiculo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Empareja cada ENTRADA con la SALIDA siguiente del mismo vehículo. Una entrada sin salida dentro de 24 h se marca
 * censurada y se corta a las 24 h (o a la siguiente entrada); si es reciente y no hay más eventos, sigue abierta
 * hasta "ahora" y no es censurada.
 */
final class Intervalos {

    static final Duration MAXIMO = Duration.ofHours(24);

    private Intervalos() {
    }

    record Evento(long vehiculoId, long usuarioId, TipoVehiculo tipo, boolean entrada, Instant en) {
    }

    record Intervalo(long vehiculoId, long usuarioId, TipoVehiculo tipo, Instant ini, Instant fin, boolean censurada) {
    }

    /** Los eventos deben venir ordenados por vehículo y fecha. El resultado conserva ese orden. */
    static List<Intervalo> construir(List<Evento> eventos, Instant ahora) {
        List<Intervalo> out = new ArrayList<>();
        for (int i = 0; i < eventos.size(); i++) {
            Evento e = eventos.get(i);
            if (!e.entrada()) {
                continue;
            }
            Evento sig = i + 1 < eventos.size() && eventos.get(i + 1).vehiculoId() == e.vehiculoId()
                    ? eventos.get(i + 1) : null;
            Instant tope = e.en().plus(MAXIMO);
            if (sig != null && !sig.entrada() && !sig.en().isAfter(tope)) {
                out.add(new Intervalo(e.vehiculoId(), e.usuarioId(), e.tipo(), e.en(), sig.en(), false));
            } else if (sig == null && tope.isAfter(ahora)) {
                out.add(new Intervalo(e.vehiculoId(), e.usuarioId(), e.tipo(), e.en(),
                        ahora.isAfter(e.en()) ? ahora : e.en(), false));
            } else {
                Instant fin = sig != null && sig.entrada() && sig.en().isBefore(tope) ? sig.en() : tope;
                out.add(new Intervalo(e.vehiculoId(), e.usuarioId(), e.tipo(), e.en(), fin, true));
            }
        }
        return out;
    }
}
