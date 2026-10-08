package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.EventoAcceso;
import com.parqueadero.backend.entity.ResultadoAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventoAccesoRepository extends JpaRepository<EventoAcceso, Long>, JpaSpecificationExecutor<EventoAcceso> {

    /** Último evento del vehículo, sea cual sea el resultado (anti-rebote). */
    Optional<EventoAcceso> findFirstByVehiculoIdOrderByOcurridoEnDescIdDesc(Long vehiculoId);

    /** Último evento con un resultado dado (inferencia ENTRADA/SALIDA: el último PERMITIDO). */
    Optional<EventoAcceso> findFirstByVehiculoIdAndResultadoOrderByOcurridoEnDescIdDesc(Long vehiculoId,
                                                                                       ResultadoAcceso resultado);

    Optional<EventoAcceso> findFirstByParqueaderoIdAndSimuladoFalseOrderByOcurridoEnDescIdDesc(Long parqueaderoId);

    List<EventoAcceso> findByUsuarioIdAndSimuladoFalseAndOcurridoEnGreaterThanEqualOrderByOcurridoEnDescIdDesc(
            Long usuarioId, Instant desde);

    /** Vehículos cuyo último evento PERMITIDO es ENTRADA: [vehiculo_id, ocurrido_en]. */
    @Query(value = """
            WITH ultimo AS (
                SELECT DISTINCT ON (e.vehiculo_id) e.vehiculo_id, e.tipo, e.ocurrido_en
                FROM evento_acceso e
                WHERE e.parqueadero_id = :parqueaderoId AND e.resultado = 'PERMITIDO'
                  AND e.vehiculo_id IS NOT NULL AND NOT e.simulado
                ORDER BY e.vehiculo_id, e.ocurrido_en DESC, e.id DESC
            )
            SELECT u.vehiculo_id, u.ocurrido_en FROM ultimo u WHERE u.tipo = 'ENTRADA'
            ORDER BY u.ocurrido_en
            """, nativeQuery = true)
    List<Object[]> vehiculosDentro(@Param("parqueaderoId") Long parqueaderoId);
}
