package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Camara;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CamaraRepository extends JpaRepository<Camara, Long> {
    List<Camara> findAllByOrderByIdAsc();
}
