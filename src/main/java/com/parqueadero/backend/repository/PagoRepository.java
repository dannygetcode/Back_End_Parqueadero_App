package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.EstadoPago;
import com.parqueadero.backend.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long>, JpaSpecificationExecutor<Pago> {

    /** Último periodo aprobado (cálculo del siguiente periodo y de la vigencia). */
    Optional<Pago> findFirstByUsuarioIdAndEstadoAndSimuladoFalseOrderByPeriodoFinDesc(Long usuarioId, EstadoPago estado);

    Optional<Pago> findFirstByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    boolean existsByUsuarioIdAndEstadoAndSimuladoFalse(Long usuarioId, EstadoPago estado);

    boolean existsByUsuarioIdAndComprobanteSha256AndEstadoNot(Long usuarioId, String sha256, EstadoPago estado);

    boolean existsByComprobanteSha256AndUsuarioIdNotAndEstadoNot(String sha256, Long usuarioId, EstadoPago estado);

    List<Pago> findByUsuarioIdAndSimuladoFalseOrderByCreadoEnDesc(Long usuarioId);

    /** Regla de estado (ADR 0006): ACTIVO si hay un pago aprobado con periodo_fin + gracia >= hoy. */
    @Query("""
            select count(p) > 0 from Pago p
            where p.usuario.id = :usuarioId and p.estado = com.parqueadero.backend.entity.EstadoPago.APROBADO
              and p.periodoFin >= :limite
            """)
    boolean tieneVigencia(@Param("usuarioId") Long usuarioId, @Param("limite") LocalDate limite);
}
