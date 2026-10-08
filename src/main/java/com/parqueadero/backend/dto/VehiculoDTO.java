package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.Carroceria;
import com.parqueadero.backend.entity.TipoVehiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Vehículo (entrada y salida). La placa se normaliza en el servicio (mayúsculas, sin espacios ni guiones) y se
 * valida por tipo: CARRO AAA999, MOTO AAA99 o AAA99A. Carrocería obligatoria para CARRO y ausente para MOTO.
 */
public record VehiculoDTO(
        Long id,
        @NotBlank(message = "es obligatoria") @Size(max = 10, message = "máximo 10 caracteres") String placa,
        @NotNull(message = "es obligatorio") TipoVehiculo tipoVehiculo,
        Carroceria carroceria,
        @NotBlank(message = "es obligatorio") @Size(max = 30, message = "máximo 30 caracteres") String color,
        @Size(max = 40, message = "máximo 40 caracteres") String marca) {
}
