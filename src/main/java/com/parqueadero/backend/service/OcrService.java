package com.parqueadero.backend.service;

import java.util.Map;

public interface OcrService {
    Map<String, Object> parse(String filename, byte[] fileBytes) throws Exception;
}
