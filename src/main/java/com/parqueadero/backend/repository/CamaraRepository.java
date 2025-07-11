package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Camara;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CamaraRepository extends JpaRepository<Camara, Long> {
}
