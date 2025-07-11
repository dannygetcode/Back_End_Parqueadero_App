package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Puerta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PuertaRepository extends JpaRepository<Puerta, Long> {
}
