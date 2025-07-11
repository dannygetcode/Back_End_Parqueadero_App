package com.parqueadero.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioDTO {
    private Long id;
    private String telefono;
    private Boolean verificado;
    private Boolean activo;
    private String nombre;
    private String apellido;
    private String placa;
    private String codigoValidacion;
    private String pin;
    private String estado;
}
