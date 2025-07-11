package com.parqueadero.backend.dto;

import lombok.Data;

@Data
public class UsuarioRegistroDTO {
    private String nombre;
    private String apellido;
    private String placa;
    private String telefono;
}