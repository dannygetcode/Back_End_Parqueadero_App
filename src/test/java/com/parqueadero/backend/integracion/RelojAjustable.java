package com.parqueadero.backend.integracion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** Reloj de pruebas: por defecto sigue al reloj real; se puede fijar o adelantar (bloqueo, anti-rebote, vencimiento). */
public class RelojAjustable extends Clock {

    private final ZoneId zona;
    private final AtomicReference<Duration> desfase = new AtomicReference<>(Duration.ZERO);

    public RelojAjustable(ZoneId zona) {
        this.zona = zona;
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        RelojAjustable otro = new RelojAjustable(zone);
        otro.desfase.set(desfase.get());
        return otro;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(desfase.get());
    }

    public void adelantar(Duration d) {
        desfase.updateAndGet(x -> x.plus(d));
    }

    /** Coloca el reloj en el instante dado (y sigue avanzando en tiempo real desde ahí). */
    public void fijar(Instant instante) {
        desfase.set(Duration.between(Instant.now(), instante));
    }

    public void reiniciar() {
        desfase.set(Duration.ZERO);
    }
}
