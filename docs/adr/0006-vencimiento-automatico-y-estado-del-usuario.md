# ADR 0006 — Vencimiento automático y estado del usuario

- Estado: Aceptado
- Fecha: 2026-10-07

## Contexto

El usuario tiene estado `ACTIVO`, `VENCIDO` o `SUSPENDIDO` (enum `EstadoUsuario`, ya existe). El dueño decidió que
`VENCIDO` sea automático, con un job diario y días de gracia configurables, que `SUSPENDIDO` sea manual y que la
baja sea lógica. El estado decide si la puerta se abre para ENTRAR (ADR 0004).

## Decisión

- **Transiciones**:
  - Alta → `VENCIDO` (todavía no hay pago aprobado; coincide con el valor por defecto actual).
  - Pago APROBADO cuyo periodo cubre hoy → `ACTIVO`, en el mismo momento de la aprobación, salvo que esté `SUSPENDIDO`.
  - Job diario → `VENCIDO` si el usuario está `ACTIVO` y no tiene ningún pago APROBADO con
    `periodo_fin + dias_gracia >= hoy`.
  - Admin → `SUSPENDIDO` (con motivo); admin → reactivar: se recalcula `ACTIVO`/`VENCIDO` según los pagos. El
    admin no puede poner `ACTIVO` a mano sin un pago (si hace falta una cortesía, se registra un pago APROBADO con
    `monto_confirmado = 0` y una observación, así queda trazado).
  - Baja → `dado_de_baja_en = now`, `cupo_id = null`, vehículo inactivo. El estado se deja como estaba.
- **Job**: `@Scheduled(cron = "${usuarios.vencimiento.cron:0 5 0 * * *}", zone = "America/Bogota")` en un
  `VencimientoJob` que llama a `UsuarioService.actualizarVencimientos(LocalDate hoy)` (una sola sentencia `UPDATE`,
  idempotente). También se ejecuta al arrancar (`ApplicationReadyEvent`) para ponerse al día si el servidor
  estuvo apagado a medianoche. `usuarios.vencimiento.dias-gracia=5` por defecto.
- Ignora usuarios `simulado = true`.

## Alternativas consideradas

- **Calcular el estado al vuelo en cada consulta** (sin columna): siempre exacto, pero complica los filtros y los
  listados, y el dueño pidió un estado explícito. Se usa el cálculo solo dentro del job.
- **Quartz, ShedLock o un worker aparte**: hacen falta con varias instancias o con jobs persistentes. Con una
  instancia, `@Scheduled` basta. Si algún día hay más de una réplica, se agrega ShedLock (una tabla) para que el
  job no corra dos veces.

## Consecuencias

- Hay que poner `@EnableScheduling` en la aplicación.
- Durante el día, un usuario puede seguir `ACTIVO` hasta la ejecución de las 00:05. Es aceptable y los días de
  gracia lo absorben.
- `PUT /api/usuarios/{id}/estado` deja de aceptar cualquier estado por query param: recibe
  `{ "accion": "SUSPENDER" | "REACTIVAR", "motivo": "..." }`. Consumidor: `frontend/js/usuarios.js` (línea ~137).
- Se guarda `suspendido_motivo` en `usuario` para que el admin y el usuario vean por qué.
