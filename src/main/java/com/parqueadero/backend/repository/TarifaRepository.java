package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.Tarifa;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TarifaRepository extends JpaRepository<Tarifa, Long> {

    /** Tarifa vigente para un tipo en una fecha: la de mayor vigenteDesde <= fecha. */
    Optional<Tarifa> findFirstByParqueaderoIdAndTipoVehiculoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            Long parqueaderoId, TipoVehiculo tipo, LocalDate fecha);

    List<Tarifa> findByParqueaderoIdOrderByTipoVehiculoAscVigenteDesdeDesc(Long parqueaderoId);

    boolean existsByParqueaderoIdAndTipoVehiculoAndVigenteDesde(Long parqueaderoId, TipoVehiculo tipo, LocalDate fecha);
}
