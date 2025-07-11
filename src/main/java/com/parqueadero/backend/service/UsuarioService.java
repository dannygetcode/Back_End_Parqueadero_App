package com.parqueadero.backend.service;

import java.util.List;

import com.parqueadero.backend.dto.UsuarioDTO;
import com.parqueadero.backend.dto.UsuarioRegistroDTO;

public interface UsuarioService {
    UsuarioDTO solicitarValidacion(String telefono);
    boolean validarUsuario(String telefono, String codigo, String nuevoPin);
    boolean login(String telefono, String pin);
    String autenticar(String telefono, String pin);
    List<UsuarioDTO> listarTodos();
    UsuarioDTO registrarUsuario(UsuarioRegistroDTO dto);
    void actualizarUsuario(Long id, UsuarioDTO dto);
    void eliminarUsuario(Long id);
    boolean verificarCodigo(String telefono, String codigo);
    void cambiarEstado(Long id, String nuevoEstado);
    UsuarioDTO buscarPorTelefono(String telefono);

}
