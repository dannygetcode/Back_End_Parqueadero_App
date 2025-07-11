package com.parqueadero.backend.service;

import com.parqueadero.backend.config.OcrProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
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
        this.rest = builder.build();
        this.ocrUrl = props.getUrl();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<String, Object> parse(String filename, byte[] fileBytes) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = rest.postForEntity(ocrUrl + "/ocr", request, Map.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("OCR service error: " + response.getStatusCode());
        }

        return response.getBody();
    }

}
