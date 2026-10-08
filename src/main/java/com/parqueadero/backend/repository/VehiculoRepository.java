package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    Optional<Vehiculo> findFirstByPlacaAndActivoTrueAndSimuladoFalse(String placa);

    boolean existsByPlacaAndActivoTrueAndSimuladoFalse(String placa);

    Optional<Vehiculo> findFirstByUsuarioIdAndActivoTrue(Long usuarioId);

    List<Vehiculo> findByIdIn(List<Long> ids);
}
