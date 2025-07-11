package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.UsuarioDTO;
import com.parqueadero.backend.dto.UsuarioRegistroDTO;
import com.parqueadero.backend.entity.EstadoUsuario;
import com.parqueadero.backend.entity.Usuario;
import com.parqueadero.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository repo;
    private static final SecureRandom RNG = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Override
    public String autenticar(String telefono, String pin) {
        Usuario u = repo.findByTelefono(telefono)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        if (!u.getVerificado() || !u.getPin().equals(pin)) {
            throw new RuntimeException("Credenciales inválidas");
        }
        return u.getTelefono();
    }

    private String generarCodigo() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    @Override
    public UsuarioDTO solicitarValidacion(String telefono) {
        String codigo = generarCodigo();
        Usuario u = repo.findByTelefono(telefono)
                .orElse(Usuario.builder()
                        .telefono(telefono)
                        .activo(true)
                        .verificado(false)
                        .build());
        u.setCodigoValidacion(codigo);
        Usuario saved = repo.save(u);
        return UsuarioDTO.builder()
                .id(saved.getId())
                .telefono(saved.getTelefono())
                .verificado(saved.getVerificado())
                .activo(saved.getActivo())
                .build();
    }

    @Override
    public boolean validarUsuario(String telefono, String codigo, String nuevoPin) {
        Usuario u = repo.findByTelefonoAndCodigoValidacion(telefono, codigo)
                .orElseThrow(() -> new EntityNotFoundException("Código o teléfono inválidos"));
        u.setVerificado(true);
        u.setPin(nuevoPin);
        repo.save(u);
        return true;
    }

    @Override
    public boolean login(String telefono, String pin) {
        Usuario u = repo.findByTelefono(telefono)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no registrado"));
        return u.getVerificado() && u.getPin().equals(pin);
    }

    @Override
    public List<UsuarioDTO> listarTodos() {
        return repo.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(u -> UsuarioDTO.builder()
                        .id(u.getId())
                        .telefono(u.getTelefono())
                        .verificado(u.getVerificado())
                        .activo(u.getActivo())
                        .nombre(u.getNombre())
                        .apellido(u.getApellido())
                        .placa(u.getPlaca())
                        .codigoValidacion(u.getCodigoValidacion())
                        .pin(u.getPin())
                        .estado(u.getEstado().name())
                        .build())
                .toList();
    }

    @Override
    public UsuarioDTO registrarUsuario(UsuarioRegistroDTO dto) {
        String codigo = generarCodigo();

        Usuario u = repo.findByTelefono(dto.getTelefono())
                .orElse(Usuario.builder()
                        .telefono(dto.getTelefono())
                        .nombre(dto.getNombre())
                        .apellido(dto.getApellido())
                        .placa(dto.getPlaca())
                        .verificado(false)
                        .activo(true)
                        .build());

        u.setCodigoValidacion(codigo);

        Usuario guardado = repo.save(u);

        return UsuarioDTO.builder()
                .id(guardado.getId())
                .telefono(guardado.getTelefono())
                .verificado(guardado.getVerificado())
                .activo(guardado.getActivo())
                .nombre(guardado.getNombre())
                .apellido(guardado.getApellido())
                .placa(guardado.getPlaca())
                .codigoValidacion(guardado.getCodigoValidacion())
                .build();
    }

    @Override
    public void actualizarUsuario(Long id, UsuarioDTO dto) {
        Usuario u = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        if (dto.getNombre() != null)
            u.setNombre(dto.getNombre());
        if (dto.getApellido() != null)
            u.setApellido(dto.getApellido());
        if (dto.getPlaca() != null)
            u.setPlaca(dto.getPlaca());
        if (dto.getTelefono() != null)
            u.setTelefono(dto.getTelefono());
        if (dto.getPin() != null)
            u.setPin(dto.getPin());
        if (dto.getEstado() != null)
            u.setEstado(EstadoUsuario.valueOf(dto.getEstado()));

        repo.save(u);
    }

    @Override
    public void eliminarUsuario(Long id) {
        if (!repo.existsById(id)) {
            throw new EntityNotFoundException("Usuario no encontrado");
        }
        repo.deleteById(id);
    }

    @Override
    public boolean verificarCodigo(String telefono, String codigo) {
        Usuario u = repo.findByTelefonoAndCodigoValidacion(telefono, codigo)
                .orElseThrow(() -> new EntityNotFoundException("Código o teléfono inválidos"));

        return true;
    }

    @Override
    public void cambiarEstado(Long id, String nuevoEstado) {
        Usuario u = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        u.setEstado(EstadoUsuario.valueOf(nuevoEstado.toUpperCase()));
        repo.save(u);
    }

    @Override
    public UsuarioDTO buscarPorTelefono(String telefono) {
        Usuario u = repo.findByTelefono(telefono)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return UsuarioDTO.builder()
                .id(u.getId())
                .telefono(u.getTelefono())
                .verificado(u.getVerificado())
                .activo(u.getActivo())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .placa(u.getPlaca())
                .codigoValidacion(u.getCodigoValidacion())
                .pin(u.getPin())
                .estado(u.getEstado().name())
                .build();
    }

}
