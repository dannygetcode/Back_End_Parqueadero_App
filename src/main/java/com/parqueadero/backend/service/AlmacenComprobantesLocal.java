package com.parqueadero.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class AlmacenComprobantesLocal implements AlmacenComprobantes {

    private static final Logger log = LoggerFactory.getLogger(AlmacenComprobantesLocal.class);

    private final Path base;

    public AlmacenComprobantesLocal(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.base = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(byte[] contenido, String extension, LocalDate fecha) {
        String relativa = String.format("comprobantes/%04d/%02d/%s.%s",
                fecha.getYear(), fecha.getMonthValue(), UUID.randomUUID(), extension);
        Path destino = base.resolve(relativa).normalize();
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el comprobante", e);
        }
        return relativa;
    }

    @Override
    public Path abrir(String rutaRelativa) {
        if (rutaRelativa == null) {
            return null;
        }
        Path p = base.resolve(rutaRelativa).normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p)) {
            return null;
        }
        return p;
    }

    @Override
    public void eliminar(String rutaRelativa) {
        Path p = abrir(rutaRelativa);
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("No se pudo borrar el comprobante huérfano {}", rutaRelativa);
        }
    }
}
