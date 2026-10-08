package com.parqueadero.backend.dto;

import java.time.LocalDate;

public record PeriodoDTO(LocalDate inicio, LocalDate fin, Integer montoEsperado) {
}
