package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.PaymentDTO;
import com.parqueadero.backend.entity.Payment;
import com.parqueadero.backend.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final OcrService ocr;
    private final PaymentRepository repo;
    @Value("${app.upload.dir}")
    private String uploadDir;

    // Después:
    private final List<DateTimeFormatter> SUPPORTED_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", new Locale("es", "ES")),
            DateTimeFormatter.ofPattern("d 'De' MMMM 'De' uuuu", new Locale("es", "ES")),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d MMMM uuuu", new Locale("es", "ES")));

    private LocalDate parseFechaOCR(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String texto = raw.trim().toLowerCase();

        // Intento 1: ISO (YYYY-MM-DD) — si el microservicio ya lo envía así
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException ignored) {
        }

        // Intento 2: "d de MMMM de uuuu" (6 de junio de 2025)
        DateTimeFormatter fmt1 = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d 'de' MMMM 'de' uuuu")
                .toFormatter(new Locale("es", "ES"));
        try {
            return LocalDate.parse(texto, fmt1);
        } catch (DateTimeParseException ignored) {

        }

        // Intento 3: "d MMM uuuu" (16 Jun 2025)
        DateTimeFormatter fmt2 = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
        try {
            return LocalDate.parse(texto, fmt2);
        } catch (DateTimeParseException ignored) {
        }

        // Intento 4: dd/MM/yyyy o dd-MM-yyyy
        DateTimeFormatter fmt3 = DateTimeFormatter.ofPattern("d/M/uuuu");
        try {
            return LocalDate.parse(texto.replace('-', '/'), fmt3);
        } catch (DateTimeParseException ignored) {
        }

        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATS) {
            try {
                return LocalDate.parse(texto, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Si llegamos aquí, no se pudo parsear
        return null;
    }

    public PaymentServiceImpl(OcrService ocr, PaymentRepository repo) {
        this.ocr = ocr;
        this.repo = repo;
    }

    @Override
    public PaymentDTO create(Long userId,
            LocalDate serviceStart,
            LocalDate serviceEnd,
            String placa,
            MultipartFile image) throws Exception {

        LocalDate paymentDate = null;
        Long amount = null;
        Map<String, String> ocrData = null;
        String imagePath = null;

        if (image != null && !image.isEmpty()) {
            byte[] bytes = image.getBytes();

            Map<String, Object> ocrResult = ocr.parse(image.getOriginalFilename(), bytes);

            Object fechaRaw = ocrResult.get("fecha");
            paymentDate = parseFechaOCR(fechaRaw != null ? fechaRaw.toString() : null);
            if (paymentDate == null) {
                throw new RuntimeException("No se pudo extraer una fecha válida desde el OCR.");
            }

            Object valorRaw = ocrResult.get("valor");
            if (valorRaw == null) {
                throw new RuntimeException("No se pudo extraer el valor desde el OCR.");
            }
            amount = Long.parseLong(valorRaw.toString());

            String filename = System.currentTimeMillis() + "_" + image.getOriginalFilename();
            Path filePath = Paths.get(uploadDir, filename);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);

            // Guardamos ruta y datos para el DTO
            imagePath = "/uploads/" + filename;
            ocrData = Map.of(
                    "fecha", ocrResult.get("fecha").toString(),
                    "valor", ocrResult.get("valor").toString());
        }

        Payment payment = Payment.builder()
                .userId(userId)
                .placa(placa)
                .paymentDate(paymentDate) // puede ser null o la fecha OCR
                .serviceStart(serviceStart)
                .serviceEnd(serviceEnd)
                .amount(amount) // puede ser null o el valor OCR
                .ocrData(ocrData) // puede ser null
                .imagePath(imagePath) // puede ser null
                .build();

        Payment saved = repo.save(payment);
        return toDTO(saved);
    }

    @Override
    public List<PaymentDTO> listAll() {
        return repo.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentDTO getById(Long id) {
        Payment p = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado con id: " + id));
        return toDTO(p);
    }

    @Override
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new RuntimeException("No existe pago con id: " + id);
        }
        repo.deleteById(id);
    }

    @Override
    public PaymentDTO update(Long id, PaymentDTO dto) {
        Payment p = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado con id: " + id));

        if (dto.getPlaca() != null) {
            p.setPlaca(dto.getPlaca());
        }
        if (dto.getPaymentDate() != null) {
            p.setPaymentDate(dto.getPaymentDate());
        }
        if (dto.getAmount() != null) {
            p.setAmount(dto.getAmount());
        }
        if (dto.getServiceStart() != null) {
            p.setServiceStart(dto.getServiceStart());
        }
        if (dto.getServiceEnd() != null) {
            p.setServiceEnd(dto.getServiceEnd());
        }

        Payment saved = repo.save(p);
        return toDTO(saved);
    }

    private PaymentDTO toDTO(Payment p) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(p.getId());
        dto.setUserId(p.getUserId());
        dto.setPaymentDate(p.getPaymentDate());
        dto.setServiceStart(p.getServiceStart());
        dto.setServiceEnd(p.getServiceEnd());
        dto.setOcrData(p.getOcrData());
        dto.setImageUrl(p.getImagePath());
        dto.setPlaca(p.getPlaca());
        dto.setAmount(p.getAmount());
        return dto;
    }
}
