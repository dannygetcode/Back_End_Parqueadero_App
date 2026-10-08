package com.parqueadero.backend.repository;

import com.parqueadero.backend.entity.EstadoUsuario;
import com.parqueadero.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Las búsquedas operativas excluyen usuarios simulados y dados de baja (ADR 0002). */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByTelefonoAndDadoDeBajaEnIsNullAndSimuladoFalse(String telefono);

    boolean existsByTelefonoAndDadoDeBajaEnIsNullAndSimuladoFalse(String telefono);

    Optional<Usuario> findByCupoIdAndDadoDeBajaEnIsNullAndSimuladoFalse(Long cupoId);

    @Query("""
            select u from Usuario u
            where u.simulado = false
              and (:incluirBajas = true or u.dadoDeBajaEn is null)
            order by u.id desc
            """)
    List<Usuario> listar(@Param("incluirBajas") boolean incluirBajas);

    @Query("""
            select u from Usuario u
            where u.simulado = false and u.estado = :estado
              and (:incluirBajas = true or u.dadoDeBajaEn is null)
            order by u.id desc
            """)
    List<Usuario> listarPorEstado(@Param("estado") EstadoUsuario estado,
                                  @Param("incluirBajas") boolean incluirBajas);

    /** Job de vencimiento (ADR 0006). Una sola sentencia, idempotente. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE usuario u SET estado = 'VENCIDO', actualizado_en = now()
            WHERE u.estado = 'ACTIVO' AND u.dado_de_baja_en IS NULL AND NOT u.simulado
              AND NOT EXISTS (SELECT 1 FROM pago p
                              WHERE p.usuario_id = u.id AND p.estado = 'APROBADO'
                                AND p.periodo_fin + :diasGracia >= :hoy)
            """, nativeQuery = true)
    int marcarVencidos(@Param("hoy") LocalDate hoy, @Param("diasGracia") int diasGracia);
}
