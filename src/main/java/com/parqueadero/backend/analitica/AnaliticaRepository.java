package com.parqueadero.backend.analitica;

import com.parqueadero.backend.entity.TipoVehiculo;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de solo lectura de la analítica. SQL nativo siempre parametrizado. El filtro de simulados se aplica con
 * {@code (:sim OR NOT x.simulado)}: con sim=false solo cuentan las filas reales.
 */
@Repository
class AnaliticaRepository {

    private static final String BOGOTA = "'America/Bogota'";

    private final NamedParameterJdbcTemplate jdbc;

    AnaliticaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }

    private static MapSqlParameterSource p(boolean sim) {
        return new MapSqlParameterSource("sim", sim);
    }

    // ---- filas
    record CupoFila(TipoVehiculo tipo, int totales, int asignados, int vigentes) {
    }

    record Vigencia(long usuarioId, String cupo, TipoVehiculo tipo, String estado, LocalDate fin) {
    }

    record Tarifa(TipoVehiculo tipo, int valor, LocalDate desde) {
    }

    record PagoMes(int mes, TipoVehiculo tipo, long suma, int cantidad) {
    }

    record RenovacionFila(String mes, int vencian, int renovaron) {
    }

    // ---- resumen
    List<CupoFila> cupos(boolean sim) {
        return jdbc.query("""
                SELECT c.tipo_vehiculo, count(DISTINCT c.id) AS totales,
                       count(DISTINCT c.id) FILTER (WHERE u.id IS NOT NULL) AS asignados,
                       count(DISTINCT c.id) FILTER (WHERE u.estado = 'ACTIVO') AS vigentes
                FROM cupo c
                LEFT JOIN usuario u ON u.cupo_id = c.id AND u.dado_de_baja_en IS NULL AND (:sim OR NOT u.simulado)
                WHERE c.activo GROUP BY c.tipo_vehiculo ORDER BY c.tipo_vehiculo
                """, p(sim), (rs, i) -> new CupoFila(TipoVehiculo.valueOf(rs.getString(1)), rs.getInt(2),
                rs.getInt(3), rs.getInt(4)));
    }

    int cuposCarroActivos() {
        Integer n = jdbc.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM cupo WHERE activo AND tipo_vehiculo = 'CARRO'", Integer.class);
        return n == null ? 0 : n;
    }

    /** Vehículos cuyo último evento permitido es una ENTRADA de las últimas 24 h: [tipo, cantidad]. */
    List<Object[]> dentroAhora(boolean sim, Instant ahora) {
        return jdbc.query("""
                WITH ultimo AS (
                    SELECT DISTINCT ON (e.vehiculo_id) e.vehiculo_id, e.tipo
                    FROM evento_acceso e
                    WHERE e.resultado = 'PERMITIDO' AND e.vehiculo_id IS NOT NULL
                      AND e.ocurrido_en > :limite AND e.ocurrido_en <= :ahora AND (:sim OR NOT e.simulado)
                    ORDER BY e.vehiculo_id, e.ocurrido_en DESC, e.id DESC)
                SELECT v.tipo_vehiculo, count(*) FROM ultimo u JOIN vehiculo v ON v.id = u.vehiculo_id
                WHERE u.tipo = 'ENTRADA' GROUP BY v.tipo_vehiculo
                """, p(sim).addValue("ahora", ts(ahora)).addValue("limite", ts(ahora.minus(Intervalos.MAXIMO))),
                (rs, i) -> new Object[]{rs.getString(1), rs.getInt(2)});
    }

    List<Object[]> usuariosPorEstado(boolean sim) {
        return jdbc.query("""
                SELECT estado, count(*) FROM usuario
                WHERE dado_de_baja_en IS NULL AND (:sim OR NOT simulado) GROUP BY estado
                """, p(sim), (rs, i) -> new Object[]{rs.getString(1), rs.getInt(2)});
    }

    long ingresosEntre(Instant ini, Instant fin, boolean sim) {
        Long v = jdbc.queryForObject("""
                SELECT coalesce(sum(monto_confirmado), 0) FROM pago
                WHERE estado = 'APROBADO' AND monto_confirmado > 0 AND creado_en >= :ini AND creado_en < :fin
                  AND (:sim OR NOT simulado)
                """, p(sim).addValue("ini", ts(ini)).addValue("fin", ts(fin)), Long.class);
        return v == null ? 0 : v;
    }

    int pagosPendientes(boolean sim) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM pago WHERE estado = 'PENDIENTE' AND (:sim OR NOT simulado)",
                p(sim), Integer.class);
        return n == null ? 0 : n;
    }

    /** Usuarios vigentes con cupo y el fin de su último periodo aprobado (nulo si nunca pagó). */
    List<Vigencia> vigencias(boolean sim) {
        return jdbc.query("""
                SELECT u.id, c.codigo, c.tipo_vehiculo, u.estado, max(p.periodo_fin)
                FROM usuario u JOIN cupo c ON c.id = u.cupo_id
                LEFT JOIN pago p ON p.usuario_id = u.id AND p.estado = 'APROBADO'
                WHERE u.dado_de_baja_en IS NULL AND (:sim OR NOT u.simulado)
                GROUP BY u.id, c.codigo, c.tipo_vehiculo, u.estado
                """, p(sim), (rs, i) -> new Vigencia(rs.getLong(1), rs.getString(2),
                TipoVehiculo.valueOf(rs.getString(3)), rs.getString(4),
                rs.getObject(5, LocalDate.class)));
    }

    // ---- eventos
    List<Intervalos.Evento> eventos(Instant desde, Instant hasta, boolean sim) {
        return jdbc.query("""
                SELECT e.vehiculo_id, e.usuario_id, v.tipo_vehiculo, e.tipo, e.ocurrido_en
                FROM evento_acceso e JOIN vehiculo v ON v.id = e.vehiculo_id
                WHERE e.resultado = 'PERMITIDO' AND e.ocurrido_en >= :desde AND e.ocurrido_en < :hasta
                  AND (:sim OR NOT e.simulado)
                ORDER BY e.vehiculo_id, e.ocurrido_en, e.id
                """, p(sim).addValue("desde", ts(desde)).addValue("hasta", ts(hasta)), this::evento);
    }

    List<Intervalos.Evento> eventosDeUsuario(long usuarioId, Instant desde, Instant hasta) {
        return jdbc.query("""
                SELECT e.vehiculo_id, e.usuario_id, v.tipo_vehiculo, e.tipo, e.ocurrido_en
                FROM evento_acceso e JOIN vehiculo v ON v.id = e.vehiculo_id
                WHERE e.resultado = 'PERMITIDO' AND e.usuario_id = :u AND e.ocurrido_en >= :desde
                  AND e.ocurrido_en < :hasta
                ORDER BY e.vehiculo_id, e.ocurrido_en, e.id
                """, new MapSqlParameterSource("u", usuarioId).addValue("desde", ts(desde))
                .addValue("hasta", ts(hasta)), this::evento);
    }

    private Intervalos.Evento evento(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Intervalos.Evento(rs.getLong(1), rs.getLong(2), TipoVehiculo.valueOf(rs.getString(3)),
                "ENTRADA".equals(rs.getString(4)), rs.getTimestamp(5).toInstant());
    }

    // ---- pagos
    List<Tarifa> tarifas() {
        return jdbc.getJdbcTemplate().query("SELECT tipo_vehiculo, valor_mensual, vigente_desde FROM tarifa "
                        + "ORDER BY tipo_vehiculo, vigente_desde",
                (rs, i) -> new Tarifa(TipoVehiculo.valueOf(rs.getString(1)), rs.getInt(2),
                        rs.getObject(3, LocalDate.class)));
    }

    List<PagoMes> pagosPorMes(Instant ini, Instant fin, boolean sim) {
        return jdbc.query("""
                SELECT extract(month FROM p.creado_en AT TIME ZONE %s)::int, t.tipo_vehiculo,
                       sum(p.monto_confirmado), count(*)
                FROM pago p JOIN tarifa t ON t.id = p.tarifa_id
                WHERE p.estado = 'APROBADO' AND p.monto_confirmado > 0 AND p.creado_en >= :ini AND p.creado_en < :fin
                  AND (:sim OR NOT p.simulado)
                GROUP BY 1, 2
                """.formatted(BOGOTA), p(sim).addValue("ini", ts(ini)).addValue("fin", ts(fin)),
                (rs, i) -> new PagoMes(rs.getInt(1), TipoVehiculo.valueOf(rs.getString(2)), rs.getLong(3),
                        rs.getInt(4)));
    }

    /** Días entre el inicio del periodo y la fecha del pago (positivo = pagó tarde) de pagos aprobados. */
    List<Integer> atrasos(Instant desde, boolean sim) {
        return jdbc.query("""
                SELECT ((p.creado_en AT TIME ZONE %s)::date - p.periodo_inicio)
                FROM pago p
                WHERE p.estado = 'APROBADO' AND p.monto_confirmado > 0 AND p.creado_en >= :desde
                  AND (:sim OR NOT p.simulado)
                """.formatted(BOGOTA), p(sim).addValue("desde", ts(desde)), (rs, i) -> rs.getInt(1));
    }

    /** Por mes de fin de periodo: cuántos vencían y cuántos renovaron (siguiente periodo pagado dentro de la gracia). */
    List<RenovacionFila> renovaciones(LocalDate desde, LocalDate hoy, int gracia, boolean sim) {
        return jdbc.query("""
                SELECT to_char(p.periodo_fin, 'YYYY-MM') AS mes, count(*),
                       count(*) FILTER (WHERE EXISTS (
                           SELECT 1 FROM pago q
                           WHERE q.usuario_id = p.usuario_id AND q.estado = 'APROBADO'
                             AND q.periodo_inicio = p.periodo_fin + 1
                             AND (q.creado_en AT TIME ZONE %s)::date <= p.periodo_fin + :gracia))
                FROM pago p
                WHERE p.estado = 'APROBADO' AND p.periodo_fin >= :desde AND p.periodo_fin + :gracia < :hoy
                  AND (:sim OR NOT p.simulado)
                GROUP BY 1 ORDER BY 1
                """.formatted(BOGOTA), p(sim).addValue("desde", desde).addValue("hoy", hoy)
                .addValue("gracia", gracia), (rs, i) -> new RenovacionFila(rs.getString(1), rs.getInt(2), rs.getInt(3)));
    }

    // ---- usuario
    Optional<String> estadoDeUsuario(long id) {
        return jdbc.query("SELECT estado FROM usuario WHERE id = :id AND dado_de_baja_en IS NULL",
                new MapSqlParameterSource("id", id), (rs, i) -> rs.getString(1)).stream().findFirst();
    }

    Optional<LocalDate> vigenteHasta(long usuarioId) {
        return jdbc.query("SELECT max(periodo_fin) FROM pago WHERE usuario_id = :id AND estado = 'APROBADO'",
                new MapSqlParameterSource("id", usuarioId), (rs, i) -> rs.getObject(1, LocalDate.class))
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    long totalPagado(long usuarioId, Instant ini, Instant fin) {
        Long v = jdbc.queryForObject("""
                SELECT coalesce(sum(monto_confirmado), 0) FROM pago
                WHERE usuario_id = :id AND estado = 'APROBADO' AND creado_en >= :ini AND creado_en < :fin
                """, new MapSqlParameterSource("id", usuarioId).addValue("ini", ts(ini)).addValue("fin", ts(fin)),
                Long.class);
        return v == null ? 0 : v;
    }
}
