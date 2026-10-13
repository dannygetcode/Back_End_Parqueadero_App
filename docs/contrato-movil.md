# Contrato de la API para la app móvil

Resumen de lo que consume la app Android (rol USUARIO), verificado contra el código de `main`. Si algo no cuadra, manda el código: `controller/`, `dto/`, `config/SecurityConfig.java`. Detalle completo en `docs/arquitectura/fase-1-modelo.md` §5 y `docs/contrato-analitica.md`.

## Generalidades
- Base: `{BASE_URL}/api`. JSON UTF-8. Fechas `LocalDate` como `"2026-10-13"`; instantes `OffsetDateTime` ISO-8601 con zona (`"2026-10-13T08:15:00-05:00"`). Montos en COP, enteros.
- Autenticación: `Authorization: Bearer <token>` en todo salvo las rutas públicas. El token del usuario dura 12 h. No hay refresh: al vencer, 401 y se vuelve a iniciar sesión.
- El token se invalida al cambiar el PIN, activar con un código nuevo, suspensión o baja: cualquier 401 debe cerrar sesión.
- El id del usuario sale del token. La app NUNCA envía `usuarioId`.

## Errores (ProblemDetail, RFC 9457, en español)
```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "No hay tarifa vigente para el tipo de vehículo",
  "codigo": "SIN_TARIFA_VIGENTE" }
```
```json
{ "title": "Bad Request", "status": 400, "detail": "Datos inválidos",
  "errores": [ { "campo": "pin", "mensaje": "debe tener 6 dígitos" } ] }
```
`codigo` (pocas veces presente) y `errores` son opcionales; no dependas del texto de `detail` para la lógica, solo del `status`. Mostrar `detail` al usuario; `errores[]` junto a cada campo. Un 500 trae solo `referencia` (id para soporte).

| Código | Cuándo |
|---|---|
| 400 | Validación (`errores[]`), JSON ilegible, PIN trivial, consentimiento con versión distinta a la vigente |
| 401 | Sin token, token vencido o revocado, credenciales o código inválidos (mensaje genérico) |
| 403 | Sin permiso; usuario dado de baja; acción no permitida por su estado. En la puerta, ver más abajo |
| 404 | Recurso inexistente o ajeno (un pago de otro usuario responde 404, no 403) |
| 409 | Conflicto de negocio (pago pendiente duplicado, comprobante repetido, sin vehículo activo) |
| 413 / 415 | Comprobante mayor a 5 MB / que no es JPEG ni PNG (se mira el contenido, no la extensión) |
| 423 | Cuenta bloqueada 15 min tras 5 PIN fallidos |
| 429 | Demasiados intentos desde la misma IP (login y activación) |
| 503 | Servicio auxiliar caído |

## Enumeraciones
- `EstadoUsuario`: `ACTIVO`, `VENCIDO`, `SUSPENDIDO`
- `EstadoPago`: `PENDIENTE`, `APROBADO`, `RECHAZADO`
- `OcrEstado`: `EXITOSO`, `PARCIAL`, `FALLIDO`, `NO_APLICA`
- `TipoVehiculo`: `CARRO`, `MOTO`. `Carroceria` (solo carros): `SEDAN`, `HATCHBACK`, `SUV`, `PICKUP`, `VAN`, `COUPE`, `OTRO`
- `TipoEvento`: `ENTRADA`, `SALIDA`. `ResultadoAcceso`: `PERMITIDO`, `DENEGADO`. `OrigenAcceso`: `CAMARA`, `APP_USUARIO`, `MANUAL_ADMIN`
- `MotivoAcceso` (por qué se denegó o se marcó): `PLACA_DESCONOCIDA`, `USUARIO_VENCIDO`, `USUARIO_SUSPENDIDO`, `USUARIO_DE_BAJA`, `SIN_CUPO`, `TIPO_CUPO_DISTINTO`, `FORZADO_ADMIN`, `SALIDA_CON_DEUDA`, `YA_DENTRO`

## Rutas públicas
| Método y ruta | Entrada | Salida |
|---|---|---|
| `GET /ping` | — | `{"status":"ok"}` |
| `GET /legal/aviso-privacidad` | — | `{ version, texto }` |
| `POST /auth/activar` | `{ telefono, codigo, pin, aceptaTratamientoDatos, versionConsentimiento }` | 204 sin cuerpo |
| `POST /auth/login` | `{ telefono, pin }` | `{ token, rol, expiraEn }` |

- **No hay autorregistro.** El admin crea al usuario desde el panel y le entrega un código de 8 caracteres, de un solo uso, que vence en 72 h. Con ese código el usuario activa su cuenta y crea su PIN.
- `pin`: exactamente 6 dígitos; se rechazan los triviales (`000000`, `123456`, etc.).
- `aceptaTratamientoDatos` debe ser `true` y `versionConsentimiento` igual a la `version` que devuelve el aviso de privacidad.
- Si el usuario olvida el PIN, el admin le genera un código nuevo y se vuelve a activar. El PIN anterior vale hasta entonces.
- `rol` en el login es `USUARIO`.

## Rutas del USUARIO (Bearer)
| Método y ruta | Entrada | Salida |
|---|---|---|
| `GET /usuarios/yo` | — | `UsuarioDTO` |
| `PUT /usuarios/yo/pin` | `{ pinActual, pinNuevo }` | 204; el token actual queda revocado: volver a iniciar sesión |
| `GET /usuarios/yo/resumen` | — | `ResumenUsuario` (analítica del usuario) |
| `GET /tarifas` | — | `[ TarifaDTO ]` |
| `GET /pagos/proximo-periodo` | — | `{ inicio, fin, montoEsperado }` |
| `POST /pagos` | multipart, parte `comprobante` (JPEG o PNG, máx. 5 MB) | `PagoDTO` (queda `PENDIENTE`) |
| `GET /pagos/mios` | — | `[ PagoDTO ]` (lista completa, sin paginar) |
| `GET /pagos/{id}` | — | `PagoDTO` (solo los propios) |
| `GET /pagos/{id}/comprobante` | — | imagen (`image/jpeg` o `image/png`), con Bearer |
| `GET /accesos/mios` | — | `[ EventoAccesoDTO ]` (lista completa, sin paginar) |
| `GET /puerta` | — | `PuertaDTO` |
| `PUT /puerta` | `{ "abierta": true }` | `PuertaDTO` (200 si se permite, 403 si se deniega) |
| `GET /camaras` | — | `[ { id, nombre, activa } ]` (sin URL para el usuario) |

## DTO
```text
UsuarioDTO      { id, telefono, nombre, apellido, estado, suspendidoMotivo, validado, bloqueadoHasta,
                  cupo: { id, codigo, tipoVehiculo } | null,
                  vehiculo: VehiculoDTO | null, vigenteHasta, creadoEn, dadoDeBajaEn }
VehiculoDTO     { id, placa, tipoVehiculo, carroceria, color, marca }
TarifaDTO       { id, tipoVehiculo, valorMensual, vigenteDesde }
PagoDTO         { id, usuarioId, usuarioNombre, placa, periodoInicio, periodoFin, montoEsperado,
                  montoOcr, fechaPagoOcr, ocrEstado, montoConfirmado, estado, motivoRechazo,
                  observacion, registradoPor, posibleDuplicado, comprobanteUrl, creadoEn, revisadoEn }
EventoAccesoDTO { id, placaLeida, tipo, tipoInferido, resultado, motivo, origen, observacion,
                  usuarioId, usuarioNombre, vehiculo, ocurridoEn, puertaAbierta, duplicado }
PuertaDTO       { abierta, abiertaHasta, ultimoEvento: EventoAccesoDTO | null, evento: EventoAccesoDTO | null }
ResumenUsuario  { estado, diasRestantes, totalPagadoAnio, promedioMensualPagado, visitasMes,
                  permanenciaPromedioMin, horaHabitualLlegada }
```
- Placas normalizadas en mayúsculas sin espacios: carro `ABC123`, moto `ABC12D`.
- `ResumenUsuario`: `diasRestantes`, `permanenciaPromedioMin` y `horaHabitualLlegada` (`"07:40"`) pueden ser `null` si no hay datos. `totalPagadoAnio` y `promedioMensualPagado` en COP. Mostrar "Sin datos todavía" en lugar de ceros inventados.
- `PagoDTO`: `montoOcr` y `fechaPagoOcr` son lo que leyó el OCR (pueden ser `null`, `ocrEstado=FALLIDO`); el valor que vale es `montoConfirmado`, que pone el admin al aprobar. Si `RECHAZADO`, mostrar `motivoRechazo`. El periodo definitivo se recalcula al aprobar.

## Reglas de negocio que afectan a la interfaz
- **Estado:** el usuario nuevo empieza `VENCIDO` hasta su primer pago aprobado. Pasa a `VENCIDO` solo cuando termina su periodo más 5 días de gracia. `SUSPENDIDO` lo pone el admin.
- **Pagos:** un solo pago `PENDIENTE` a la vez (409). Tarifa mensual vigente: carro 80.000, moto 10.000 (en 2025: 64.000 y 8.000).
- **Puerta (simulada, no hay hardware):**
  - El usuario solo puede **abrir** (`{"abierta": true}`). Cerrar (`false`) es solo del admin y al usuario le responde 403.
  - La puerta se cierra sola: queda abierta 10 s (`abiertaHasta`). La app debe animar el cierre al llegar a esa hora.
  - El backend usa la placa del vehículo del usuario y deduce si es `ENTRADA` o `SALIDA` según su último evento.
  - Si se deniega, la respuesta es **403 con un `PuertaDTO`** (no un ProblemDetail) cuyo `evento.motivo` explica por qué: `USUARIO_VENCIDO`, `USUARIO_SUSPENDIDO`, `YA_DENTRO`, etc. La salida siempre se permite (con deuda queda `SALIDA_CON_DEUDA`).
  - 409 si no tiene vehículo activo. Repetir la orden en menos de 60 s devuelve el mismo evento con `duplicado: true`.
- **Cámaras (simuladas):** el usuario solo recibe nombre y si está activa; no hay video. La app debe mostrar una vista de demostración, no un reproductor.
- **Privacidad:** el usuario solo ve sus datos. Nunca mostrar ocupación detallada de otros usuarios.

## Entorno de desarrollo
- El backend corre en Docker y escucha en `127.0.0.1:8080`. Emulador: `http://10.0.2.2:8080`. Teléfono real: `adb reverse tcp:8080 tcp:8080` y `http://127.0.0.1:8080`, o cambiar `BIND_ADDRESS` en el `.env` del backend.
- HTTP sin TLS solo en desarrollo. No escribir IPs reales en el código.
- Los usuarios simulados (datos de demostración) no pueden iniciar sesión. Para probar, el admin crea un usuario desde el panel y le pasa el código de activación.
