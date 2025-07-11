package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.PaymentDTO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface PaymentService {
    PaymentDTO create(Long userId, LocalDate start, LocalDate end, String placa,  MultipartFile image) throws Exception;
    List<PaymentDTO> listAll();
    PaymentDTO getById(Long id);
    void delete(Long id);
    PaymentDTO update(Long id, PaymentDTO dto) throws Exception;
}
