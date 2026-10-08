package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Cupo;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CupoRepository extends JpaRepository<Cupo, Long> {

    List<Cupo> findByParqueaderoIdOrderByCodigoAsc(Long parqueaderoId);

    boolean existsByParqueaderoIdAndCodigo(Long parqueaderoId, String codigo);

    long countByParqueaderoIdAndTipoVehiculoAndActivoTrue(Long parqueaderoId, TipoVehiculo tipo);
}
