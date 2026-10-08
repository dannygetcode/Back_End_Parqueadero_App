# ADR 0002 — Modelo de datos de la Fase 1

- Estado: Aceptado
- Fecha: 2026-10-07
- Detalle (ER, DDL, endpoints): `docs/arquitectura/fase-1-modelo.md`

## Contexto

Hoy hay un parqueadero de 7 cupos (6 carros, 1 moto), cada usuario con un cupo fijo. La placa, leída por una
cámara simulada, identifica vehículo y usuario. Se cobra una mensualidad por tipo de vehículo que sube cerca de
25 % al año. El modelo actual guarda la placa en `usuarios`, no tiene cupos ni tarifas, y `Payment` no tiene
estado ni FK a usuario. Más adelante se analizarán 12 meses de datos simulados y se harán pronósticos.
Queremos poder crecer a un parqueadero más grande sin construir multi-sede hoy.

## Decisión

Tablas (nombres en español, `snake_case`, singular):

| Tabla | Propósito | Puntos clave |
|---|---|---|
| `parqueadero` | La sede | Hoy una fila (id=1). Todas las tablas raíz llevan `parqueadero_id` FK. |
| `cupo` | Plaza física | `codigo` (`C1`..`C6`, `M1`), `tipo_vehiculo`, `activo`. **La capacidad es el número de cupos activos por tipo**: no hay constantes en el código ni un campo `capacidad` duplicado. |
| `usuario` | Cliente mensual | `cupo_id` FK (único entre usuarios vigentes), estado, PIN hash, bloqueo, consentimiento, baja lógica. |
| `vehiculo` | Vehículo del usuario | `placa` normalizada (mayúsculas, sin espacios ni guiones), `tipo_vehiculo` CARRO/MOTO, `carroceria`, `color`, `marca` opcional. Un solo vehículo activo por usuario; los anteriores quedan inactivos para no romper el historial. |
| `tarifa` | Precio mensual | Por `tipo_vehiculo` y `vigente_desde`. Sin `vigente_hasta`: la tarifa vigente en una fecha es la de mayor `vigente_desde <= fecha`. Así no hay solapes que validar. Subir 25 % = insertar una fila. |
| `pago` | Mensualidad pagada con comprobante | Estado PENDIENTE/APROBADO/RECHAZADO; periodo calculado por el backend; `monto_esperado` (copiado de la tarifa), `monto_ocr` y `monto_confirmado` separados; `tarifa_id`; hash del comprobante. |
| `evento_acceso` | Cada lectura de placa / apertura | ENTRADA/SALIDA, PERMITIDO/DENEGADO, motivo, origen, placa leída tal cual, FKs opcionales a vehículo y usuario (placa desconocida = sin FK). Es la fuente de verdad de ocupación y la serie temporal para analítica. |
| `puerta` | Estado de la puerta simulada | Una fila por parqueadero. |
| `camara` | Cámara simulada | Se conserva; se agrega `parqueadero_id`. |

Reglas transversales:

- **Enums como `varchar` + `CHECK`** (no tipos `ENUM` de PostgreSQL): agregar un valor es un `ALTER ... CHECK`
  simple, y JPA usa `@Enumerated(EnumType.STRING)`.
- **Dinero en pesos enteros** (`integer`, COP no usa centavos en la práctica). Rango suficiente hasta 2.147 millones.
- **Fechas**: `timestamptz` para instantes, `date` para periodos. Zona de negocio `America/Bogota`
  (ya configurada en Hibernate).
- **Datos simulados**: columna `simulado boolean not null default false` en `usuario`, `vehiculo`, `pago` y
  `evento_acceso`. Los índices de unicidad de negocio (cupo asignado, placa activa) excluyen filas simuladas,
  para que el histórico simulado de 12 meses conviva con los usuarios reales. Las consultas operativas filtran
  `simulado = false`; la analítica decide.
- **Baja lógica**: `usuario.dado_de_baja_en`. Nunca `DELETE` de usuarios, pagos ni eventos. La baja libera el cupo.
- **Configuración operativa en propiedades, no en tablas**: días de gracia, ventana anti-rebote, duración de
  apertura, intentos de PIN. Un parqueadero no justifica una tabla de parámetros todavía.
- **Pronóstico (fases siguientes)**: el servicio Python escribirá en tablas propias (p. ej. `pronostico_ocupacion`)
  creadas por migración Flyway de este repo (ADR 0001) y Spring las expone en solo lectura. No se crean hoy.

Cálculo del periodo de un pago (lo hace el backend al recibir el comprobante):

- Si el usuario tiene un pago APROBADO cuyo `periodo_fin >= hoy - dias_gracia`, el nuevo periodo empieza al día
  siguiente de ese `periodo_fin` (continuidad, sin huecos).
- Si no, empieza hoy.
- `periodo_fin = periodo_inicio + 1 mes - 1 día`. `monto_esperado` = tarifa vigente en `periodo_inicio` para el
  tipo del cupo del usuario.
- Solo puede haber un pago PENDIENTE por usuario a la vez (índice parcial único).

## Alternativas consideradas

- **Placa en `usuario`** (como hoy): no admite cambio de vehículo sin perder el historial de accesos. Descartada.
- **Tabla `asignacion_cupo` con fechas** (historial de asignaciones): útil si se analiza rotación de cupos; hoy
  no hay ese requisito. Se puede reconstruir desde los eventos. Pospuesta.
- **Capacidad como número en `parqueadero`**: duplica la información de `cupo` y se desincroniza. Descartada.
- **Tarifa con `vigente_hasta`**: obliga a validar solapes (o `btree_gist` + `EXCLUDE`). Descartada por simple.
- **Esquema o BD aparte para datos simulados**: separa mejor, pero duplica migraciones y complica que la analítica
  compare simulado vs real. Descartada a favor de la columna `simulado`.
- **Multi-sede completo** (usuarios por sede, roles por sede, tenant en el token): sin demanda hoy. Solo se deja el
  `parqueadero_id` como gancho.

## Consecuencias

- Crecer a un parqueadero más grande = insertar filas en `cupo` (y, si hay otra sede, en `parqueadero`). No hay
  cambios de código para la capacidad.
- La ocupación se calcula desde `evento_acceso` (último evento permitido por vehículo). A esta escala (decenas de
  eventos al día, unos 5.000 al año) es una consulta trivial con el índice `(vehiculo_id, ocurrido_en desc)`. Si
  algún día hay miles de cupos, se agrega una tabla de estado materializada; hoy no.
- Las consultas operativas deben acordarse de filtrar `simulado = false`. Se centraliza en los repositorios.
- `cupo_id` vive en `usuario`, así que un usuario tiene un solo cupo. Si se necesitan usuarios con varios cupos,
  se migra a una tabla de asignación.
- Contratos rotos: `UsuarioDTO` (sin `pin` ni `codigoValidacion` en listados, `placa` pasa a venir del vehículo),
  `PaymentDTO` (nuevos campos y estado). Consumidores en `mobile/.../ApiService.kt` y `frontend/js/usuarios.js`,
  `pagos.js`. Ver la tabla de endpoints.
