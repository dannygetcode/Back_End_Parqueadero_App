package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Parqueadero;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParqueaderoRepository extends JpaRepository<Parqueadero, Long> {
}
