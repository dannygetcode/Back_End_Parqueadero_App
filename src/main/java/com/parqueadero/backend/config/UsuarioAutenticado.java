package com.parqueadero.backend.config;

/**
 * Principal de la petición. {@code id} es el id del usuario o del administrador (sub del JWT);
 * es null para SISTEMA (simulador de cámara con API key).
 */
public record UsuarioAutenticado(Long id, Rol rol) {

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }

    public boolean esUsuario() {
        return rol == Rol.USUARIO;
    }
}
