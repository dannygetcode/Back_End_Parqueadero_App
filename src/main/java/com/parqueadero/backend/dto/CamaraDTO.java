package com.parqueadero.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CamaraDTO {
    private Long id;
    private String nombre;
    private String url;
    private Boolean activa;
}


