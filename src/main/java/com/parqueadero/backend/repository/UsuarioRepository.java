package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByTelefono(String telefono);
    Optional<Usuario> findByTelefonoAndCodigoValidacion(String telefono, String codigo);
}
