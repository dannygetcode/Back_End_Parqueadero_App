package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.ActivacionDTO;
import com.parqueadero.backend.dto.AdminLoginRequest;
import com.parqueadero.backend.dto.LoginUsuarioDTO;
import com.parqueadero.backend.dto.TokenDTO;

/** Login del admin y del usuario, activación de la cuenta y bloqueo por intentos (ADR 0003). */
public interface AuthService {

    TokenDTO loginAdmin(AdminLoginRequest request);

    TokenDTO loginUsuario(LoginUsuarioDTO request);

    void activar(ActivacionDTO request);

    /** Crea el administrador inicial si la tabla está vacía. Solo guarda el hash BCrypt. */
    void crearAdministradorInicialSiFalta(String usuario, String password);
}
