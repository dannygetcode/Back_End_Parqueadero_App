# Cambios de contrato de la Fase 1 (para la fase móvil y el panel)

El backend de la Fase 1 rompe a propósito el contrato que usan `mobile/.../data/network/ApiService.kt` y
`frontend/js/*.js` (decisión del dueño: se rompe una vez, sin `/v1`). Ninguno de los dos se ha tocado: **hoy no
funcionan contra este backend**. Este documento es la guía para adaptarlos. Referencias: `docs/arquitectura/fase-1-modelo.md`
§5 (todos los endpoints y DTOs) y `docs/requisitos/fase-1-backend.md` §7-8.

## Reglas generales

- **Token en todas las llamadas** salvo `POST /api/admin/login`, `POST /api/auth/login`, `POST /api/auth/activar`,
  `GET /api/ping` y `GET /api/legal/aviso-privacidad`: cabecera `Authorization: Bearer <token>`. Antes `camaras`,
  `pagos` y `/uploads` eran públicos.
- **Roles**: el token del usuario ya no sirve para rutas del admin (403) y viceversa para las de "yo".
- **Errores** en `application/problem+json`:
  `{"type","title","status","detail", "errores":[{"campo","mensaje"}]?, "codigo"?}`. Mostrar `detail`; para 400 de
  validación, `errores`. Códigos: 400 validación, 401 sin token / credenciales, 403 rol o cuenta no operable,
  404, 409 conflicto, 413 archivo grande, 415 tipo de archivo, 423 cuenta bloqueada (15 min tras 5 fallos).
  Excepción: un acceso denegado (`PUT /api/puerta`, `POST /api/accesos/lecturas`) responde 403 **con el evento**
  en el cuerpo, no con ProblemDetail.
- **Fechas**: `LocalDate` como `"2026-10-07"`; instantes ISO-8601 con offset de Bogotá
  (`"2026-10-07T18:30:00-05:00"`).
- **IDs en el token**: el `sub` del JWT es el id numérico (antes el teléfono). El cliente no debe enviar ids de
  usuario: salen del token.

## App móvil (`ApiService.kt`)

| Función actual | Antes | Ahora |
|---|---|---|
| `registrarUsuario` | `POST usuarios/registro` (público) | **Eliminar.** El alta la hace el admin y le entrega un código de 8 caracteres. |
| `verificarCodigo` + `validarCodigoYPin` | `POST usuarios/verificar-codigo` y `POST usuarios/validar?telefono&codigo&nuevoPin` | Un solo paso: `POST auth/activar` (JSON, ver abajo) → 204. |
| `login` | `POST usuarios/login?telefono&pin` → `{token}` | `POST auth/login` JSON `{telefono, pin}` → `TokenDTO {token, rol, expiraEn}`. PIN de **6 dígitos**. |
| `obtenerMiUsuario` | `GET usuarios/yo` | Misma ruta; `UsuarioDTO` nuevo (ver abajo). |
| `subirPago` | multipart `userId, placa, start, end, image` | `POST pagos` multipart con **solo** la parte `comprobante` (JPEG o PNG, máx. 5 MB) → 201 `PagoDTO`. |
| `obtenerEstadoPuerta` | `GET puerta` → `{abierta}` | Misma ruta; `{abierta, abiertaHasta, ultimoEvento?}` (`ultimoEvento` solo si es del propio usuario). |
| `actualizarEstadoPuerta` | `PUT puerta {abierta}` | Misma forma `{abierta:true}`; la placa sale del vehículo del usuario. 200 si se abre, **403 con `{..., evento}`** si se deniega (p. ej. `evento.motivo = USUARIO_VENCIDO`). `{abierta:false}` → 403 (solo el admin cierra). |
| `getCamaras` | `GET camaras` sin token | Con token. Para el USUARIO la respuesta es `[{id, nombre, activa}]`: **ya no trae `url`** (el `CamaraDTO.url: String` no nulo de Kotlin debe quitarse o hacerse nullable; `CamaraScreen` no puede abrir el stream con ella). |

Nuevos útiles para la app: `GET pagos/mios`, `GET pagos/proximo-periodo` (`{inicio, fin, montoEsperado}`: cuánto y
qué periodo va a pagar), `GET pagos/{id}/comprobante` (imagen, con token), `GET accesos/mios` (últimos 30 días),
`PUT usuarios/yo/pin` (`{pinActual, pinNuevo}` → 204), `GET tarifas?vigentes=true`, `GET legal/aviso-privacidad`.

### Activación (`POST /api/auth/activar`)

```json
{ "telefono": "3001234567", "codigo": "K7Q2M9XA", "pin": "482915",
  "aceptaTratamientoDatos": true, "versionConsentimiento": "2026-10" }
```

- La app debe mostrar el texto de `GET /api/legal/aviso-privacidad` (`{version, texto}`) y enviar esa `version`.
  Sin `aceptaTratamientoDatos = true` → 400.
- PIN: exactamente 6 dígitos; se rechazan triviales (`000000`, `123456`, `654321`, todos iguales...) → 400.
- Código incorrecto, expirado (72 h) o ya usado → 401 genérico. 5 fallos → 423 durante 15 min.
- "Olvidé mi PIN": el admin genera un código nuevo y el usuario repite la activación.

### `UsuarioDTO` (`GET /api/usuarios/yo`)

```json
{ "id": 7, "telefono": "3001234567", "nombre": "Ana", "apellido": "Pérez",
  "estado": "ACTIVO", "suspendidoMotivo": null, "validado": true, "bloqueadoHasta": null,
  "cupo": { "id": 3, "codigo": "C3", "tipoVehiculo": "CARRO" },
  "vehiculo": { "id": 9, "placa": "ABC123", "tipoVehiculo": "CARRO", "carroceria": "SEDAN", "color": "Rojo", "marca": null },
  "vigenteHasta": "2026-11-06", "creadoEn": "2026-10-07T20:15:00-05:00", "dadoDeBajaEn": null }
```

Desaparecen `pin`, `codigoValidacion`, `verificado` (→ `validado`), `activo` y `placa` (→ `vehiculo.placa`).
`estado`: `ACTIVO` | `VENCIDO` | `SUSPENDIDO`. Un usuario nuevo está `VENCIDO` hasta que se le apruebe el primer pago.

### `PagoDTO` (sustituye a `PaymentDTO`)

```json
{ "id": 12, "usuarioId": 7, "usuarioNombre": "Ana Pérez", "placa": "ABC123",
  "periodoInicio": "2026-10-07", "periodoFin": "2026-11-06", "montoEsperado": 80000,
  "montoOcr": 80000, "fechaPagoOcr": "2026-10-06", "ocrEstado": "EXITOSO",
  "montoConfirmado": null, "estado": "PENDIENTE", "motivoRechazo": null, "observacion": null,
  "registradoPor": "USUARIO", "posibleDuplicado": false,
  "comprobanteUrl": "/api/pagos/12/comprobante", "creadoEn": "...", "revisadoEn": null }
```

- Desaparecen `userId` (→ `usuarioId`), `paymentDate`/`amount` (→ `fechaPagoOcr`/`montoOcr` propuestos por el OCR y
  `montoConfirmado` del admin), `serviceStart`/`serviceEnd` (→ `periodoInicio`/`periodoFin`), `ocrData` e `imageUrl`.
- El periodo mostrado en un pago PENDIENTE es **provisional**: se recalcula al aprobar con la fecha de aprobación.
- `ocrEstado`: `EXITOSO` | `PARCIAL` | `FALLIDO` | `NO_APLICA`. Si el OCR falla el pago se crea igual.
- 409 si ya hay un pago pendiente o si el mismo comprobante ya se envió; 415 si no es JPEG/PNG; 413 si pasa de 5 MB;
  403 si la cuenta está suspendida, bloqueada o dada de baja.

## Panel web actual (`frontend/js`)

| Archivo | Cambio |
|---|---|
| `login.js` | `POST /api/admin/login` responde JSON: usar `response.json().token` (antes `response.text()`). 423 si está bloqueado. |
| `usuarios.js` | Alta: `POST /api/usuarios` `{telefono (3XXXXXXXXX), nombre, apellido, cupoId, vehiculo:{placa, tipoVehiculo, carroceria (solo CARRO), color, marca?}}` → 201 `{usuario, codigoValidacion, codigoExpiraEn}`: **mostrar el código una sola vez** para entregarlo. Se eliminan `/usuarios/registro` y `/usuarios/validar`. Editar: `PUT /api/usuarios/{id}` `{telefono?, nombre?, apellido?, cupoId?}` (ya no PIN ni estado); vehículo: `PUT /api/usuarios/{id}/vehiculo`. Estado: `PUT /api/usuarios/{id}/estado` con cuerpo `{accion: "SUSPENDER"|"REACTIVAR", motivo}` (antes `?estado=`). `DELETE` es baja lógica. Nuevo código: `POST /api/usuarios/{id}/codigo`. Pagos de un usuario: `GET /api/pagos?usuarioId=`. Cupos para el selector: `GET /api/cupos`. |
| `pagos.js` | `GET /api/pagos` es paginado (`?page=&size=&estado=&usuarioId=&desde=&hasta=` → `{content, page:{size, number, totalElements, totalPages}}`). Se eliminan `PUT /api/pagos/{id}` y `DELETE`: usar `PUT /api/pagos/{id}/aprobar` `{montoConfirmado, observacion?}` y `/rechazar` `{motivo}` (5 a 200 caracteres). La imagen ya no es pública: `fetch(comprobanteUrl, {headers:{Authorization}})` → `Blob` → `URL.createObjectURL` (y quitar `http://localhost:8080` fijo). Cortesía: `POST /api/pagos/manual` multipart `usuarioId, montoConfirmado=0, observacion`. |
| `puerta.js` | `PUT /api/puerta` al abrir debe enviar `placa` (y opcionalmente `tipo`, `observacion`). Si las reglas lo denegarían, la apertura se registra como forzada y exige `observacion` (si falta, 400). Para simular la cámara desde el panel: `POST /api/accesos/lecturas {placa, camaraId?}`. Nuevos: `GET /api/accesos` (historial paginado) y `GET /api/accesos/ocupacion`. |
| `camaras.js` | Enviar el token. `PUT /api/camaras/{id}` recibe `{activa}`; `POST` valida `nombre` (1-50) y `url` (http/https, opcional, sin `usuario:clave@`). El admin sí recibe `url`. |

## Simulador de cámara

`POST /api/accesos/lecturas` con cabecera `X-Api-Key: <CAMARA_API_KEY>` y cuerpo `{"placa":"ABC123","camaraId":1}`
(`ocurridoEn` opcional, como mucho 5 min en el pasado; `tipo` no se admite con API key). Respuesta `EventoAccesoDTO`:
200 si se permite (la puerta queda abierta 10 s), 403 con el mismo cuerpo si se deniega. Lecturas repetidas del mismo
vehículo en menos de 60 s devuelven el evento anterior con `duplicado: true`.
