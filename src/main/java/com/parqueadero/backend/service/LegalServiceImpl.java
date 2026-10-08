package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.AvisoPrivacidadDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Texto estático versionado en src/main/resources/legal/aviso-privacidad-{version}.txt. Una versión nueva del texto
 * es un archivo nuevo + cambiar legal.aviso-privacidad.version; las aceptaciones antiguas conservan su versión.
 */
@Service
public class LegalServiceImpl implements LegalService {

    private final AvisoPrivacidadDTO aviso;

    public LegalServiceImpl(@Value("${legal.aviso-privacidad.version:2026-10}") String version) {
        ClassPathResource recurso = new ClassPathResource("legal/aviso-privacidad-" + version + ".txt");
        try {
            this.aviso = new AvisoPrivacidadDTO(version, recurso.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró el aviso de privacidad versión " + version, e);
        }
    }

    @Override
    public AvisoPrivacidadDTO avisoPrivacidad() {
        return aviso;
    }
}
