package com.parqueadero.backend.service;

import com.parqueadero.backend.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Vencimiento automático (ADR 0006): cada día a las 00:05 (America/Bogota) y al arrancar, para ponerse al día si el
 * servidor estuvo apagado. Idempotente. Asume una sola instancia (con varias réplicas haría falta ShedLock).
 */
@Component
public class VencimientoJob {

    private static final Logger log = LoggerFactory.getLogger(VencimientoJob.class);

    private final UsuarioService usuarioService;
    private final Clock clock;

    public VencimientoJob(UsuarioService usuarioService, Clock clock) {
        this.usuarioService = usuarioService;
        this.clock = clock;
    }

    @Scheduled(cron = "${usuarios.vencimiento.cron:0 5 0 * * *}", zone = "America/Bogota")
    public void diario() {
        ejecutar();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        ejecutar();
    }

    public int ejecutar() {
        int vencidos = usuarioService.actualizarVencimientos(LocalDate.now(clock.withZone(AppConfig.ZONA)));
        if (vencidos > 0) {
            log.info("Vencimiento automático: {} usuario(s) pasaron a VENCIDO", vencidos);
        }
        return vencidos;
    }
}
