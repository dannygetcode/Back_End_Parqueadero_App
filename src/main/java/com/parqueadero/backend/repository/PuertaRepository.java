package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Puerta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PuertaRepository extends JpaRepository<Puerta, Long> {

    /** La puerta principal del parqueadero (hoy hay una por sede). */
    Optional<Puerta> findFirstByParqueaderoIdOrderByIdAsc(Long parqueaderoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Puerta> findWithLockById(Long id);
}
