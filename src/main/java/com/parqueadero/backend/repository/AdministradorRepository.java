package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Administrador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
    Optional<Administrador> findByUsuario(String usuario);

    /** SELECT ... FOR UPDATE: el login serializa los intentos de la misma cuenta (contador de fallos atómico). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Administrador a where a.usuario = :usuario")
    Optional<Administrador> bloquearPorUsuario(@Param("usuario") String usuario);

    /** Versión de credenciales (revocación de JWT). Vacío si no existe o nunca se fijó. */
    @Query("select a.credencialesCambiadasEn from Administrador a where a.id = :id")
    Optional<java.time.Instant> credencialesCambiadasEn(@Param("id") Long id);
}
