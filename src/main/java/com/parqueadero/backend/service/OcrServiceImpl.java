package com.parqueadero.backend.service;

import com.parqueadero.backend.config.OcrProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class OcrServiceImpl implements OcrService {

    private final RestTemplate rest;
    private final String ocrUrl;

    public OcrServiceImpl(RestTemplateBuilder builder, OcrProperties props) {
        // Timeout de conexión y lectura (10 s por defecto, ADR 0005): si se supera, el pago queda con OCR FALLIDO.
        this.rest = builder
                .connectTimeout(props.getTimeout())
                .readTimeout(props.getTimeout())
                .build();
        this.ocrUrl = props.getUrl();
    }

    @Override
    public Map<String, Object> parse(String filename, byte[] fileBytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(ocrUrl + "/ocr", HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }
}
