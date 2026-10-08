# Requisitos — Fase 1 (backend)

Repo: `backend` · Rama: `fase-1-backend` · Estado: alineado con el diseño de arquitectura, pendiente de revisión del dueño
Fuentes: `CLAUDE.md`, decisiones del dueño y del orquestador, `docs/arquitectura/fase-1-modelo.md`, ADR 0001 a 0006 (`docs/adr/`) y lectura del código actual (controllers, entidades, DTOs, `SecurityConfig`, `JwtService`, `PaymentServiceImpl`, `UsuarioServiceImpl`, `main.py` del OCR, `ApiService.kt` de `mobile` y `frontend/js/*.js`).

Relación con la arquitectura: los nombres de tablas, columnas, rutas, roles y DTOs son los de `fase-1-modelo.md`. Donde este documento se aparta (por decisión del orquestador o del dueño) se indica en la sección 12, para que se refleje allí.

Convenciones
- Prioridad MoSCoW: **M** (Must), **S** (Should), **C** (Could), **W** (Won't en Fase 1).
- Fase: **F1** se implementa ahora; **F2** generador de datos simulados; **F3** analítica/pronóstico (FastAPI) y nuevo panel Next.js; **FM** fase móvil.
- Salvo que se indique otra cosa, todo requisito afecta al repo `backend`.
- "Hoy" significa la fecha actual en zona `America/Bogota`.
- Roles: **P** público, **A** ADMIN, **U** USUARIO (solo lo suyo), **S** SISTEMA (simulador de cámara con API key).

---

## 1. Punto de partida (lo que existe hoy y cambia)

| Hoy | Problema | Fase 1 |
|---|---|---|
| `POST /api/usuarios/solicitar-validacion` y `/registro` son públicos y crean usuarios | Cualquiera puede darse de alta | Alta solo por ADMIN (`POST /api/usuarios`) |
| `UsuarioDTO` devuelve `pin` y `codigoValidacion` (en `listarTodos` y `/yo`) | Fuga de credenciales | Nunca salen PIN ni código (salvo el código, una vez, al admin que lo genera) |
| PIN de 4 caracteres en texto plano | Sin hash | PIN de 6 dígitos con BCrypt |
| Admin: usuario/contraseña en propiedades, comparación en texto plano; responde el token como texto plano | Sin hash | Tabla `administrador` con hash BCrypt, respuesta JSON |
| JWT `sub` = teléfono o username, sin rol; el filtro da `authorities` vacías | No hay autorización por rol | `sub` = id, claim `rol`; roles ADMIN, USUARIO y SISTEMA |
| `/api/pagos/**` y `/uploads/**` públicos; `userId` llega como parámetro | IDOR total | El usuario sale del token; comprobantes servidos con autorización |
| Nombre de archivo = `timestamp_` + nombre original | Path traversal / tipos arbitrarios | UUID + tipo por bytes + tamaño máximo |
| `PUT /api/puerta {abierta}` cambia un booleano | No registra quién entra/sale | Toda apertura pasa por `evento_acceso`; lecturas de la cámara simulada por `POST /api/accesos/lecturas` |
| `Payment` sin estado de revisión; el cliente manda `start`/`end`; si el OCR falla, el alta lanza excepción | El admin no revisa; periodos inconsistentes | `pago` con estados PENDIENTE/APROBADO/RECHAZADO; periodo calculado por el backend; OCR no bloqueante |
| `ddl-auto=update`, `show-sql=true` | Esquema implícito | Flyway + `ddl-auto=validate`, `show-sql=false` |
| CORS con IPs fijas en código y `@CrossOrigin("*")` en `CamaraController` | Configuración rígida e insegura | CORS por variable de entorno (`CORS_ORIGENES`) |
| Errores como `RuntimeException` → 500 | Respuestas inconsistentes | `@RestControllerAdvice` con `ProblemDetail` y 4xx |
| `/api/ping` en `permitAll` pero sin controlador | Healthcheck roto | Controlador trivial |

---

## 2. Alcance

### Dentro de Fase 1
- Esquema nuevo con Flyway (`V1__esquema_inicial.sql`, `V2__datos_iniciales.sql`) sobre BD reseteada, sin baseline del esquema viejo (ADR 0001).
- Modelo: `parqueadero`, `cupo`, `usuario`, `vehiculo`, `tarifa` por vigencia, `pago` con revisión, `evento_acceso`, `puerta`, `camara` y `administrador`; columna `simulado` (ADR 0002).
- Autenticación y autorización por rol (ADMIN/USUARIO/SISTEMA), bloqueo por intentos fallidos, activación de cuenta con código y consentimiento (ADR 0003).
- Gestión de usuarios, vehículos, cupos y tarifas por el admin.
- Pagos: subida por el usuario, OCR como propuesta, aprobación/rechazo por el admin, cálculo de periodo, pago manual de cortesía (ADR 0005, 0006).
- Estado del usuario automático (VENCIDO por fecha + gracia, job diario) y manual (SUSPENDIDO) (ADR 0006).
- Puerta y cámara simuladas: lecturas de placa, inferencia ENTRADA/SALIDA, reglas de acceso, anti-rebote, ocupación (ADR 0004).
- Endpoints propios del usuario: `/api/usuarios/yo`, `/api/pagos/mios`, `/api/accesos/mios`.
- Endurecimiento de seguridad, manejo de errores, validación, tests.
- Consentimiento de tratamiento de datos (Ley 1581 de 2012) en la activación.
- Eliminar `uploads/` del repo y añadirlo a `.gitignore`.

### Fuera de Fase 1
- Generador de 12 meses de datos simulados (F2): escribe directamente en la BD con `simulado = true`. Fase 1 solo deja el modelo preparado.
- Servicio de analítica y pronóstico en FastAPI y migración del OCR Flask (F3). Fase 1 solo define el contrato de `/api/analitica/*` y `/api/usuarios/yo/resumen`; Spring será el gateway.
- Adaptar la app Android (FM) y el panel `frontend/` actual (será reemplazado por Next.js en F3). En F1 la puerta la acciona el admin desde el panel o el simulador de cámara.
- Hardware real (cámara, puerta, lector de placas), ANPR/reconocimiento de placa en imagen.
- Pago en efectivo, pasarela de pago en línea, facturación electrónica, notificaciones (SMS, correo, push).
- Recuperación de PIN por autoservicio (la hace el admin regenerando el código).
- Varios administradores con permisos distintos, multisede funcional, reservas, cobro por horas o visitantes.
- Funcionalidad nueva de cámaras (solo cambian sus reglas de seguridad y se añade `simulada`).
- Supresión de datos del titular a petición (Ley 1581): se atiende manualmente; no hay endpoint.

---

## 3. Modelo de datos (V1)

Referencia: DDL de `docs/arquitectura/fase-1-modelo.md` §3. Resumen de lo que los requisitos usan:

| Tabla | Campos clave | Restricciones relevantes |
|---|---|---|
| `parqueadero` | `id`, `nombre`, `zona_horaria` | Semilla: una fila (id=1) |
| `administrador` | `id`, `username` (único), `password_hash`, `intentos_fallidos`, `bloqueado_hasta`, `creado_en`, `actualizado_en` | **No está en el DDL de arquitectura** (ver §12). La crea V1; la fila la inserta la aplicación al arrancar (RF-02) |
| `cupo` | `id`, `parqueadero_id`, `codigo` (`C1`…`C6`, `M1`), `tipo_vehiculo` (`CARRO`/`MOTO`), `activo` | Único `(parqueadero_id, codigo)`. Semilla: 6 CARRO + 1 MOTO |
| `usuario` | `id`, `parqueadero_id`, `cupo_id`, `telefono`, `nombre`, `apellido`, `estado` (`ACTIVO`/`VENCIDO`/`SUSPENDIDO`, por defecto VENCIDO), `suspendido_motivo`, `pin_hash`, `codigo_validacion_hash`, `codigo_validacion_expira_en`, `validado_en`, `intentos_fallidos`, `bloqueado_hasta`, `consentimiento_datos_en`, `consentimiento_version`, `simulado`, `dado_de_baja_en` | Teléfono y cupo únicos entre usuarios vigentes no simulados; SUSPENDIDO exige motivo; validado exige PIN y consentimiento; baja exige cupo nulo |
| `vehiculo` | `id`, `usuario_id`, `placa`, `tipo_vehiculo`, `carroceria`, `color`, `marca`, `activo`, `simulado` | Placa activa única y un vehículo activo por usuario (entre no simulados); carrocería obligatoria para CARRO y nula para MOTO |
| `tarifa` | `id`, `parqueadero_id`, `tipo_vehiculo`, `valor_mensual`, `vigente_desde` | Sin `vigente_hasta`: vigente en F = mayor `vigente_desde <= F`. Único `(parqueadero_id, tipo_vehiculo, vigente_desde)` |
| `pago` | `id`, `usuario_id`, `tarifa_id`, `periodo_inicio`, `periodo_fin`, `monto_esperado`, `monto_ocr`, `fecha_pago_ocr`, `ocr_estado` (`EXITOSO`/`PARCIAL`/`FALLIDO`/`NO_APLICA`), `ocr_datos` (jsonb), `monto_confirmado`, `estado` (`PENDIENTE`/`APROBADO`/`RECHAZADO`), `motivo_rechazo`, `observacion`, `registrado_por` (`USUARIO`/`ADMIN`), `comprobante_ruta`, `comprobante_tipo`, `comprobante_sha256`, `posible_duplicado`, `revisado_en`, `simulado` | Un PENDIENTE por usuario real; un APROBADO por `(usuario_id, periodo_inicio)`; APROBADO exige `monto_confirmado`; RECHAZADO exige motivo |
| `evento_acceso` | `id`, `parqueadero_id`, `placa_leida`, `vehiculo_id`, `usuario_id`, `camara_id`, `puerta_id`, `tipo` (`ENTRADA`/`SALIDA`), `tipo_inferido`, `resultado` (`PERMITIDO`/`DENEGADO`), `motivo`, `origen` (`CAMARA`/`APP_USUARIO`/`MANUAL_ADMIN`), `observacion`, `ocurrido_en`, `registrado_en`, `simulado` | Motivos: `PLACA_DESCONOCIDA`, `USUARIO_VENCIDO`, `USUARIO_SUSPENDIDO`, `USUARIO_DE_BAJA`, `SIN_CUPO`, `TIPO_CUPO_DISTINTO`, `FORZADO_ADMIN`, `SALIDA_CON_DEUDA` y **`YA_DENTRO`** (falta en el CHECK de arquitectura, ver §12) |
| `puerta` | `id`, `parqueadero_id`, `nombre`, `abierta_hasta` | Abierta = `abierta_hasta > now()`. Semilla: una puerta |
| `camara` | `id`, `parqueadero_id`, `nombre`, `url` (opcional), `activa`, `simulada` | Semilla: una cámara simulada |

Se eliminan: `payment_data` (el OCR pasa a `pago.ocr_datos`) y el modelo viejo de `usuarios`/`payment`.

Carrocería (solo CARRO): `SEDAN`, `HATCHBACK`, `SUV`, `PICKUP`, `VAN`, `COUPE`, `OTRO`. Las motos no llevan carrocería en Fase 1.

Semilla de tarifas (`V2__datos_iniciales.sql`, decisión del dueño):

| Tipo | Desde 2025-01-01 | Desde 2026-01-01 |
|---|---|---|
| CARRO | 64.000 COP | 80.000 COP |
| MOTO | 8.000 COP | 10.000 COP |

---

## 4. Requisitos funcionales

### 4.1 Autenticación y cuentas

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-01 | `POST /api/admin/login` con `AdminLoginRequest {username, password}` en JSON; compara con `administrador.password_hash` (BCrypt) y devuelve `TokenDTO {token, rol:"ADMIN", expiraEn}`. Credenciales inválidas → 401 con mensaje genérico. | M | F1 |
| RF-02 | Al arrancar, si la tabla `administrador` está vacía, se crea un admin con `ADMIN_USERNAME` y `ADMIN_PASSWORD` (variables de entorno), guardando solo el hash BCrypt. Si ya existe una fila, las variables se ignoran. Si la tabla está vacía y falta alguna de las dos variables, la aplicación no arranca. | M | F1 |
| RF-03 | `POST /api/auth/activar` con `ActivacionDTO {telefono, codigo, pin, aceptaTratamientoDatos, versionConsentimiento}`: si el código coincide con `codigo_validacion_hash`, no ha expirado y no se ha usado, guarda `pin_hash` (BCrypt), `validado_en`, `consentimiento_datos_en` y `consentimiento_version`, anula el código y reinicia intentos y bloqueo. Responde 204. Código incorrecto, expirado o usado, o teléfono desconocido → 401 genérico ("teléfono o código incorrectos"). Sirve también para fijar un PIN nuevo tras RF-15. | M | F1 |
| RF-04 | El PIN debe ser exactamente 6 dígitos (`^\d{6}$`) y no puede ser trivial (los 6 dígitos iguales, `123456`, `654321`). Si no cumple → 400. | M | F1 |
| RF-05 | El código de validación tiene 8 caracteres `[A-Z0-9]`, se genera con `SecureRandom`, expira a las 72 h (`codigo-validacion.horas`) y es de un solo uso. En BD solo se guarda su hash BCrypt. | M | F1 |
| RF-06 | `POST /api/auth/login` con `LoginUsuarioDTO {telefono, pin}` en JSON. Éxito → `TokenDTO {token, rol:"USUARIO", expiraEn}`. Falla con 401 genérico ("teléfono o PIN incorrectos") si el PIN no coincide, la cuenta no está validada o el usuario está dado de baja. Los usuarios VENCIDO y SUSPENDIDO sí pueden iniciar sesión (para ver su estado). | M | F1 |
| RF-07 | Bloqueo: cada fallo de PIN (login) o de código (activación, cuando el teléfono existe) suma 1 a `intentos_fallidos` del usuario; cada fallo de contraseña suma 1 en `administrador`. Al llegar a 5, `bloqueado_hasta = now + 15 min` y el contador se reinicia. Durante el bloqueo se responde 423 sin comprobar la credencial. Un acceso correcto pone el contador en 0. Parámetros: `seguridad.pin.max-intentos=5`, `seguridad.pin.bloqueo-minutos=15` (también para el admin, persistido en su tabla). El contador es atómico (la fila se lee con `SELECT ... FOR UPDATE`: intentos simultáneos no pierden incrementos). Respuesta uniforme: un teléfono o usuario de admin inexistente se comporta igual (401 y, tras 5 fallos, 423 durante 15 min) con un contador en memoria, y un login a una cuenta sin activar cuenta como fallo en su fila (ADR 0003). | M | F1 |
| RF-08 | El JWT (HS256, `JWT_SECRET`) lleva `sub` = id como texto (de `usuario` o de `administrador`), `rol` = `ADMIN` o `USUARIO`, `iat` y `exp`. Expiración: `jwt.expiracion.admin` = 4 h, `jwt.expiracion.usuario` = 12 h. Sin refresh tokens. El filtro pone la autoridad `ROLE_<rol>`. | M | F1 |
| RF-09 | Las operaciones sensibles del usuario (subir pago, abrir puerta, cambiar PIN) releen el usuario en BD y responden 403 si está SUSPENDIDO, dado de baja o bloqueado (mitigación de la falta de revocación, ADR 0003). | M | F1 |
| RF-10 | Se eliminan `POST /api/usuarios/registro`, `solicitar-validacion`, `verificar-codigo`, `validar` y `login` (este último pasa a `/api/auth/login`). | M | F1 |
| RF-56 | `PUT /api/usuarios/yo/pin` (U) con `CambioPinDTO {pinActual, pinNuevo}`: comprueba `pinActual` (cuenta como intento fallido si no coincide, RF-07), valida `pinNuevo` con RF-04 y responde 204. | S | F1 |

### 4.2 Usuarios, vehículos y cupos (admin)

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-11 | `POST /api/usuarios` (A) da de alta un usuario con su vehículo y cupo en una sola transacción: `UsuarioAltaDTO {telefono, nombre, apellido, cupoId, vehiculo: VehiculoDTO {placa, tipoVehiculo, carroceria?, color, marca?}}`. Responde 201 `UsuarioAltaRespuestaDTO {usuario, codigoValidacion, codigoExpiraEn}`. Es, junto con RF-15, la única respuesta que contiene el código en claro. | M | F1 |
| RF-12 | Validaciones del alta: teléfono móvil colombiano `^3\d{9}$` (más estricto que el CHECK de BD, que se mantiene) y único entre usuarios vigentes; nombre y apellido de 1 a 50 caracteres; placa normalizada (mayúsculas, sin espacios ni guiones) y única entre vehículos activos; CARRO `^[A-Z]{3}\d{3}$`, MOTO `^[A-Z]{3}\d{2}[A-Z]?$`; carrocería obligatoria y válida para CARRO, ausente para MOTO; color de 1 a 30 caracteres; marca de hasta 40 caracteres o nula; el cupo existe, está activo, es del mismo tipo que el vehículo y está libre. Formato inválido → 400; teléfono o placa duplicados o cupo ocupado → 409. | M | F1 |
| RF-13 | El consentimiento de tratamiento de datos lo da el titular al activar (RF-03), no el admin en el alta: `aceptaTratamientoDatos` debe ser `true` y `versionConsentimiento` no vacía; si no → 400 y la cuenta no se activa. | M | F1 |
| RF-14 | El usuario nuevo nace `VENCIDO` y sigue así hasta que se le apruebe su primer pago. El admin no puede ponerlo ACTIVO a mano; una cortesía se registra como pago manual de 0 COP (RF-34). | M | F1 |
| RF-15 | `POST /api/usuarios/{id}/codigo` (A) genera un código nuevo, invalida el anterior, reinicia `intentos_fallidos` y `bloqueado_hasta`, y devuelve `CodigoValidacionDTO {codigo, expiraEn}` una sola vez. Sirve para "olvidé mi PIN": el usuario fija un PIN nuevo con RF-03. El PIN anterior sigue valiendo hasta que se use el código nuevo. | M | F1 |
| RF-16 | `GET /api/usuarios?estado=&incluirBajas=` (A) devuelve `List<UsuarioDTO>`; `GET /api/usuarios/{id}` devuelve `UsuarioDetalleDTO {usuario, vehiculo, cupo, ultimoPago, vigenteHasta}`. `incluirBajas` por defecto `false`. Nunca devuelven PIN, hashes ni código. Excluyen usuarios simulados (RF-49). | M | F1 |
| RF-17 | `PUT /api/usuarios/{id}` (A) con `UsuarioActualizacionDTO {telefono?, nombre?, apellido?, cupoId?}`; `PUT /api/usuarios/{id}/vehiculo` (A) con `VehiculoDTO`: el vehículo anterior pasa a inactivo y se crea uno nuevo (se conserva el historial de accesos). Mismas validaciones que RF-12; el cupo nuevo debe ser del tipo del vehículo activo. | M | F1 |
| RF-18 | `DELETE /api/usuarios/{id}` (A) hace una baja lógica: `dado_de_baja_en = now`, `cupo_id = null`, vehículo inactivo; el estado se deja como estaba. Conserva pagos, comprobantes y eventos. Responde 204. | M | F1 |
| RF-19 | `PUT /api/usuarios/{id}/estado` (A) con `CambioEstadoDTO {accion: SUSPENDER\|REACTIVAR, motivo?}`. SUSPENDER exige `motivo` (1 a 200 caracteres) y lo guarda en `suspendido_motivo`; REACTIVAR quita la suspensión y recalcula ACTIVO/VENCIDO con RF-35 (puede quedar VENCIDO). Otra acción → 400. | M | F1 |
| RF-20 | `GET /api/cupos` (A) devuelve `List<CupoDTO {id, codigo, tipoVehiculo, activo, usuarioId?, ocupado}>`, donde `ocupado` indica que el cupo está asignado a un usuario vigente. La ocupación física (vehículos dentro) está en RF-60. | M | F1 |
| RF-21 | `POST /api/cupos` (A) con `{codigo, tipoVehiculo}` → 201; `PUT /api/cupos/{id}` (A) con `{activo}`; desactivar un cupo asignado → 409; código repetido → 409. Permite crecer sin migraciones. | S | F1 |

### 4.3 Tarifas

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-22 | `GET /api/tarifas?vigentes=true` (A, U) lista `TarifaDTO {id, tipoVehiculo, valorMensual, vigenteDesde}` (con `vigentes=true`, solo la vigente hoy por tipo; si no, el historial). `POST /api/tarifas` (A) con `{tipoVehiculo, valorMensual, vigenteDesde}`: `valorMensual > 0` y `vigenteDesde >= hoy` (si no → 400); ya existe una tarifa de ese tipo con esa fecha → 409. Las tarifas no se editan ni se borran. | M | F1 |
| RF-23 | La tarifa de un pago es la de mayor `vigente_desde <= periodo_inicio` para el tipo del cupo del usuario. No hay aumento automático: el ~25 % anual se registra como tarifa nueva. Si no hay tarifa aplicable → 409 `SIN_TARIFA_VIGENTE`. | M | F1 |

### 4.4 Pagos

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-57 | `GET /api/pagos/proximo-periodo` (U) devuelve `PeriodoDTO {inicio, fin, montoEsperado}` calculado con RF-27 para hoy, para que el usuario sepa cuánto pagar. | M | F1 |
| RF-24 | `POST /api/pagos` (U, multipart) recibe solo `comprobante` (obligatorio). El usuario sale del token. Aplica RF-09, valida el archivo (RNF-05), calcula `comprobante_sha256`, el periodo propuesto (RF-27 con la fecha de hoy), `tarifa_id` y `monto_esperado`, llama al OCR **fuera de la transacción** y, ya en una transacción corta, guarda el archivo y crea el pago en PENDIENTE con `registrado_por = USUARIO`, `monto_ocr`, `fecha_pago_ocr`, `ocr_datos` y `ocr_estado`. Responde 201 `PagoDTO`. Si dos subidas simultáneas pasan la comprobación de pendiente, el índice `uq_pago_pendiente_usuario` deja entrar una y la otra recibe 409 (ADR 0005). | M | F1 |
| RF-25 | Si el OCR no responde en 10 s, devuelve error o no extrae nada, el pago **se crea igual** con `ocr_estado = FALLIDO` y `monto_ocr`/`fecha_pago_ocr` nulos; `PARCIAL` si extrajo solo uno de los dos; `EXITOSO` si extrajo ambos. El fallo del OCR nunca produce 5xx al usuario. | M | F1 |
| RF-26 | Un usuario no puede tener más de un pago PENDIENTE: un segundo envío → 409. Un comprobante con el mismo SHA-256 que otro pago no rechazado **del mismo usuario** → 409. Si coincide con el de otro usuario, se acepta y se marca `posible_duplicado = true`. | M | F1 |
| RF-27 | **Cálculo del periodo** para una fecha de referencia F: si el usuario tiene un pago APROBADO cuyo `periodo_fin >= F − dias_gracia`, `periodo_inicio` = el mayor de esos `periodo_fin` + 1 día (continuidad sin hueco); si no, `periodo_inicio = F`. `periodo_fin = periodo_inicio + 1 mes − 1 día`. Al subir, F = fecha de subida (periodo propuesto, la columna es obligatoria); **al aprobar se recalcula con F = fecha de aprobación** y se actualizan `tarifa_id` y `monto_esperado` si cambian. El cliente nunca envía el periodo. | M | F1 |
| RF-28 | `PUT /api/pagos/{id}/aprobar` (A) con `AprobacionPagoDTO {montoConfirmado, observacion?}`: `montoConfirmado` obligatorio y `>= 0` (si falta → 400). Recalcula el periodo (RF-27), fija `estado = APROBADO`, `revisado_en` y recalcula el estado del usuario en la misma transacción (RF-35). Solo se aprueban pagos PENDIENTE → 409 si no lo están. | M | F1 |
| RF-29 | `PUT /api/pagos/{id}/rechazar` (A) con `RechazoPagoDTO {motivo}` (de 5 a 200 caracteres) → RECHAZADO con `revisado_en`. No afecta al estado del usuario. Solo desde PENDIENTE → si no, 409. | M | F1 |
| RF-30 | `GET /api/pagos?estado=&usuarioId=&desde=&hasta=&page=&size=` (A) → `Page<PagoDTO>`; `GET /api/pagos/{id}` (A, o U si es el dueño). | M | F1 |
| RF-31 | `GET /api/pagos/mios` (U) lista los pagos del usuario autenticado (cualquier estado, con motivo de rechazo), ordenados por `creado_en` descendente. | M | F1 |
| RF-32 | `GET /api/pagos/{id}/comprobante` (A, o U dueño) transmite el archivo con su `Content-Type` y `Cache-Control: private, no-store`. Pago ajeno o sin comprobante → 404. | M | F1 |
| RF-33 | No se borran ni se editan pagos libremente: se eliminan `PUT /api/pagos/{id}` y `DELETE /api/pagos/{id}`. Un pago erróneo se rechaza. | M | F1 |
| RF-34 | `POST /api/pagos/manual` (A, multipart) con `{usuarioId, montoConfirmado, observacion, comprobante?}` crea un pago ya APROBADO con `registrado_por = ADMIN`, `ocr_estado = NO_APLICA`, `revisado_en = now` y periodo según RF-27 (F = hoy). En Fase 1 solo se usa para **cortesías**: `montoConfirmado` debe ser 0 y `observacion` obligatoria; un monto mayor (pago en efectivo) → 400 hasta la fase que lo incluya. | M | F1 |

### 4.5 Estado del usuario

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-35 | Regla: si el usuario está SUSPENDIDO, sigue SUSPENDIDO. Si no, es ACTIVO cuando tiene algún pago APROBADO con `periodo_fin + dias_gracia >= hoy`, y VENCIDO en otro caso (incluido no tener pagos aprobados). `usuarios.vencimiento.dias-gracia` por defecto 5. | M | F1 |
| RF-36 | `VencimientoJob` con `@Scheduled(cron = usuarios.vencimiento.cron, por defecto 0 5 0 * * *, zona America/Bogota)` pasa a VENCIDO a los ACTIVO que ya no cumplen RF-35, con una sola sentencia idempotente; también se ejecuta en `ApplicationReadyEvent`. Ignora usuarios simulados y dados de baja. | M | F1 |
| RF-37 | La regla de ENTRADA (RF-39) usa el estado persistido. Se acepta que un usuario siga ACTIVO hasta la ejecución de las 00:05 del día en que vence (lo absorben los días de gracia). | M | F1 |

### 4.6 Puerta, cámara simulada y accesos

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-58 | Autenticación del simulador: `ApiKeyAuthenticationFilter` (antes del filtro JWT) compara la cabecera `X-Api-Key` en tiempo constante con `CAMARA_API_KEY` y, si coincide, autentica con `ROLE_SISTEMA`. `ROLE_SISTEMA` solo puede llamar a `POST /api/accesos/lecturas` (otra ruta → 403). Sin `CAMARA_API_KEY` configurada no se autentica a nadie. | M | F1 |
| RF-38 | `POST /api/accesos/lecturas` (S, A) con `LecturaPlacaDTO {placa, camaraId?, ocurridoEn?, tipo? (solo A)}`, origen `CAMARA`. `ocurridoEn` opcional (por defecto ahora); solo en el pasado y con un máximo de 5 min de desfase → si no, 400. `tipo` enviado por SISTEMA → 400. En F1 no se acepta `simulado` (el generador F2 escribe directo en BD). Respuesta `EventoAccesoDTO {id, placaLeida, tipo, tipoInferido, resultado, motivo, origen, usuarioId?, usuarioNombre?, vehiculo?, ocurridoEn, puertaAbierta, duplicado}`: 200 si PERMITIDO, 403 con el mismo cuerpo si DENEGADO. Todo evento se guarda, también los denegados. | M | F1 |
| RF-39 | `AccesoService.registrar`, en una transacción: (1) normaliza la placa y busca el vehículo activo no simulado; (2) **anti-rebote**: si el vehículo tiene un evento en los últimos `accesos.antirrebote-segundos` (60), devuelve ese evento con `duplicado = true` sin crear otro; (3) **tipo**: si viene explícito se usa (`tipo_inferido = false`); si no, SALIDA si el último PERMITIDO fue ENTRADA, ENTRADA en otro caso; (4) **reglas**. Placa desconocida → ENTRADA DENEGADO `PLACA_DESCONOCIDA`, sin FKs. ENTRADA, en orden: dado de baja → `USUARIO_DE_BAJA`; SUSPENDIDO → `USUARIO_SUSPENDIDO`; VENCIDO → `USUARIO_VENCIDO`; sin cupo → `SIN_CUPO`; tipo de cupo distinto al del vehículo → `TIPO_CUPO_DISTINTO`; ENTRADA explícita con el vehículo ya dentro → `YA_DENTRO`; si no, PERMITIDO. SALIDA: siempre PERMITIDA; si el usuario está VENCIDO, motivo `SALIDA_CON_DEUDA`; si no constaba dentro, se anota en `observacion`. (5) Si es PERMITIDO, `puerta.abierta_hasta = now + accesos.apertura-segundos` (10 s). | M | F1 |
| RF-40 | "Dentro" significa que el último evento PERMITIDO (no simulado) del vehículo es una ENTRADA. No se guarda un estado "dentro/fuera" aparte. | M | F1 |
| RF-41 | Si se envía `camaraId`, la cámara debe existir y estar activa → si no, 409. | C | F1 |
| RF-42 | `GET /api/puerta` (A, U) devuelve `PuertaDTO {abierta, abiertaHasta, ultimoEvento?}`, con `abierta = abierta_hasta > now`. Para un USUARIO, `ultimoEvento` solo se incluye si es suyo (privacidad). | M | F1 |
| RF-59 | `PUT /api/puerta` (A, U) con `PuertaComandoDTO {abierta, placa?, tipo?, observacion?}`. **ADMIN** `{abierta:true, placa, tipo?, observacion?}`: `placa` obligatoria (si falta → 400), origen `MANUAL_ADMIN`; es una apertura manual que siempre queda PERMITIDA: si las reglas de RF-39 la denegarían (o la placa es desconocida), queda con motivo `FORZADO_ADMIN` y `observacion` es obligatoria. **ADMIN** `{abierta:false}`: cierra (`abierta_hasta = now`) sin evento. **USUARIO** `{abierta:true}`: la placa sale de su vehículo activo (se ignora la del cuerpo), origen `APP_USUARIO`, mismas reglas que RF-39, aplica RF-09; 403 con el evento si se deniega. Respuesta: `PuertaDTO` con el `EventoAccesoDTO` anidado. | M (admin) / S (usuario, consumido en FM) | F1 |
| RF-43 | `GET /api/accesos?desde=&hasta=&placa=&resultado=&page=&size=` (A) → `Page<EventoAccesoDTO>` ordenado por `ocurrido_en` descendente. | M | F1 |
| RF-44 | `GET /api/accesos/mios` (U) → `List<EventoAccesoDTO>` de los últimos 30 días, solo eventos con `usuario_id` del usuario autenticado. | S | F1 |
| RF-60 | `GET /api/accesos/ocupacion` (A) → `OcupacionDTO {porTipo: [{tipoVehiculo, cupos, ocupados, libres}], vehiculosDentro: [{placa, usuario, desde}]}`; `cupos` = cupos activos del tipo; `ocupados` según RF-40. | M | F1 |

### 4.7 Cámaras

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-45 | `GET /api/camaras` (A, U), `POST /api/camaras` (A) y `PUT /api/camaras/{id}` (A, con `{activa}`). `CamaraDTO {id, nombre, url, activa, simulada}`. Se quita `@CrossOrigin("*")`. Validación: `nombre` de 1 a 50; `url` opcional, hasta 200 caracteres y esquema `http`/`https`. | M | F1 |

### 4.8 Perfil, privacidad y datos simulados

| ID | Descripción | Prior. | Fase |
|---|---|---|---|
| RF-46 | `GET /api/usuarios/yo` (U) devuelve el `UsuarioDTO` propio: `{id, telefono, nombre, apellido, estado, suspendidoMotivo, validado, bloqueadoHasta, cupo, vehiculo, vigenteHasta, creadoEn}`, con `vigenteHasta` = `periodo_fin` del último pago aprobado (nulo si no hay). Sin PIN ni código. | M | F1 |
| RF-47 | `GET /api/legal/aviso-privacidad` (P) devuelve `{version, texto}` del aviso vigente (responsable, finalidades, derechos del titular según la Ley 1581, canal de contacto y retención de comprobantes; sin datos personales reales en el repo). La `version` es la que la app envía en `versionConsentimiento` (RF-03). | S | F1 |
| RF-48 | `usuario`, `vehiculo`, `pago` y `evento_acceso` tienen `simulado boolean not null default false`. Ningún endpoint de F1 permite ponerlo a `true` (el generador F2 escribe directo en BD). | M | F1 |
| RF-49 | Las consultas operativas (listados de usuarios, pagos y accesos, ocupación, inferencia de tipo, anti-rebote, job de vencimiento, unicidad de cupo/placa/teléfono) ignoran las filas `simulado = true`. El filtro se centraliza en los repositorios. Solo la analítica F3 decide incluirlas (RF-55). | M | F1 |

### 4.9 Contratos reservados para fases siguientes (solo definición)

Spring es el gateway hacia el servicio Python: autentica y autoriza, y reenvía.

| ID | Endpoint | Rol | Fase |
|---|---|---|---|
| RF-50 | `GET /api/usuarios/yo/resumen` → `{estado, diasRestantes, totalPagadoAnio, visitasMes, permanenciaPromedioMin}` | USUARIO | F3 (W en F1) |
| RF-51 | `GET /api/analitica/ocupacion?desde&hasta&granularidad=hora\|dia` → serie de ocupación por tipo | ADMIN | F3 |
| RF-52 | `GET /api/analitica/ingresos?anio` → ingresos aprobados (`monto_confirmado`) por mes y tipo | ADMIN | F3 |
| RF-53 | `GET /api/analitica/morosidad` → usuarios VENCIDO, días de mora, monto adeudado | ADMIN | F3 |
| RF-54 | `GET /api/analitica/pronostico?horizonteDias=7` → ocupación esperada por hora con intervalo | ADMIN | F3 |
| RF-55 | Todos los endpoints de analítica aceptan `incluirSimulados`. | ADMIN | F3 |

---

## 5. Requisitos no funcionales

### Seguridad

| ID | Descripción | Verificación | Prior. |
|---|---|---|---|
| RNF-01 | Matriz de autorización (sección 7): públicos solo `POST /api/admin/login`, `POST /api/auth/login`, `POST /api/auth/activar`, `GET /api/ping` y, si se adopta RF-47, `GET /api/legal/aviso-privacidad`; SISTEMA solo `POST /api/accesos/lecturas`; el resto según el rol de cada ruta; `anyRequest().authenticated()`. Sin token → 401; rol incorrecto → 403. | Test MockMvc por cada fila de la matriz (sin token, USUARIO, ADMIN, API key) | M |
| RNF-02 | IDOR: ningún endpoint de USUARIO recibe un id de usuario; todo se filtra por el `sub` del token. Pedir un pago o comprobante ajeno → 404. | Test: el usuario A pide el pago y el comprobante de B → 404 | M |
| RNF-03 | Ninguna respuesta contiene `pin`, `pin_hash`, `codigo_validacion_hash` ni `password_hash`; el código en claro solo aparece en las respuestas de RF-11 y RF-15. | Test que serializa todos los DTO de salida y revisión del código | M |
| RNF-04 | PIN, contraseña del admin y código de validación con BCrypt (coste 10). | Inspección de la BD tras un alta y una activación: no hay texto plano | M |
| RNF-05 | Comprobantes: JPEG o PNG detectado por los primeros bytes (no por extensión ni `Content-Type`), máximo 5 MB (`spring.servlet.multipart.max-file-size`), guardados como `{UPLOAD_DIR}/comprobantes/{aaaa}/{mm}/{uuid}.{ext}` con ruta relativa en `comprobante_ruta`. Se elimina el handler `/uploads/**` (`WebConfig`) y su `permitAll`. Tipo no permitido → 415; demasiado grande → 413; en ambos casos no queda archivo en disco. | Tests con un `.exe` renombrado a `.png`, un archivo de 6 MB y un nombre `../../x.png` | M |
| RNF-06 | CORS solo en `SecurityConfig`, con orígenes de `CORS_ORIGENES` (por defecto `http://localhost:5500,http://localhost:3000`); sin IPs en el código ni comodín. | Preflight desde un origen no listado → sin `Access-Control-Allow-Origin` | M |
| RNF-07 | Credenciales y secretos solo por variables de entorno (`.env` fuera de git, `.env.example` sin valores reales): `JWT_SECRET` (32 bytes o más; si falta, no arranca), `ADMIN_USERNAME`, `ADMIN_PASSWORD` (solo se lee para crear el admin, RF-02), `CAMARA_API_KEY` (32+ bytes aleatorios), credenciales de BD y `UPLOAD_DIR`. Se eliminan `admin.password` como credencial de login y `spring.security.user.*`. | Revisión de `application.properties` y `.env.example`; arranque sin `JWT_SECRET` falla | M |
| RNF-08 | Los logs no registran PIN, código, contraseña, API key, token ni el cuerpo de las peticiones de autenticación; `spring.jpa.show-sql=false`; el OCR Python deja de imprimir el texto completo del comprobante. | Revisión de logs al ejecutar el flujo completo | M |
| RNF-09 | CSRF sigue desactivado porque la API es stateless con Bearer y sin cookies (decisión documentada en ADR 0003; la protección CSRF irá en el BFF de Next.js). | Revisión de `SecurityConfig` | M |
| RNF-10 | `SERVER_ADDRESS` por defecto `127.0.0.1` en `.env.example`; `0.0.0.0` solo cuando se pida explícitamente. | Revisión de `.env.example` | S |
| RNF-33 | Límite por IP en `POST /api/admin/login`, `/api/auth/login` y `/api/auth/activar`: como mucho `seguridad.limite-ip.max-peticiones` (10) por IP y ruta en `seguridad.limite-ip.ventana-segundos` (60 s), ventana deslizante en memoria; al superarlo 429 `ProblemDetail` con `Retry-After`. Detrás de un proxy, la IP sale de `seguridad.limite-ip.cabecera-ip-cliente` (ADR 0003). | `LimitePorIpIT`: la 4.ª petición con límite 3 → 429; otra IP no se ve afectada; pasada la ventana vuelve a 401 | M |

### Manejo de errores y validación

| ID | Descripción | Verificación | Prior. |
|---|---|---|---|
| RNF-11 | `@Valid` en todos los cuerpos de entrada y `@RestControllerAdvice` que responde `ProblemDetail` (RFC 9457) con `errores:[{campo, mensaje}]` y mensajes en español. Mapeo: validación 400, sin autenticar 401, prohibido 403, no encontrado 404, conflicto 409, tamaño 413, tipo 415, bloqueado 423, demasiadas peticiones 429 (RNF-33). Excepción: el acceso denegado (RF-38, RF-59) responde 403 con el `EventoAccesoDTO` en el cuerpo. | Tests por cada código | M |
| RNF-12 | Ningún error devuelve stack traces ni mensajes de SQL; los no controlados → 500 con `detail` genérico y log del error con un id de correlación. | Test que fuerza una excepción | M |

### Rendimiento

| ID | Descripción | Verificación | Prior. |
|---|---|---|---|
| RNF-13 | Con la BD cargada con 12 meses simulados (≤ 7 usuarios reales, ≤ 25.000 eventos de acceso, ≤ 200 pagos), los endpoints sin OCR responden en p95 < 300 ms en local. | Prueba sencilla (100 peticiones por endpoint) con los tiempos registrados | S |
| RNF-14 | `POST /api/pagos` responde en p95 < 12 s incluido el OCR (timeout de 10 s en `RestTemplate`, RF-25). | Prueba con el OCR apagado: responde en < 12 s con `ocrEstado=FALLIDO` | M |
| RNF-15 | `GET /api/pagos` y `GET /api/accesos` están paginados (`page`, `size` por defecto 20 y máximo 100). | Test con `size=500` → se aplica 100 | M |

### Disponibilidad y operación

| ID | Descripción | Verificación | Prior. |
|---|---|---|---|
| RNF-16 | El backend funciona con el OCR caído (RF-25). | Test con el OCR apagado | M |
| RNF-17 | `GET /api/ping` (público) responde 200 `{status}`; lo usa el healthcheck de Docker. | `curl` | S |
| RNF-18 | `docker compose up -d db` + `./mvnw spring-boot:run` sobre una BD vacía crea el esquema con Flyway (V1 y V2) sin pasos manuales; el reset del volumen está documentado en el README. | Prueba sobre un volumen nuevo | M |

### Mantenibilidad

| ID | Descripción | Verificación | Prior. |
|---|---|---|---|
| RNF-19 | Esquema solo por Flyway (`src/main/resources/db/migration`), `spring.jpa.hibernate.ddl-auto=validate` (aprobado por el dueño), `spring.flyway.clean-disabled=true`. Las migraciones aplicadas no se editan; las tablas futuras del servicio Python también se crean aquí. | El arranque falla si entidad y esquema no coinciden | M |
| RNF-20 | Se mantiene el patrón controller → service (interfaz + `Impl`) → repository → entity; las entidades JPA no se exponen (solo DTOs). El reloj se inyecta como bean `Clock`. | Revisión | M |
| RNF-21 | Tests unitarios de: cálculo del periodo (RF-27: con y sin continuidad, último día de gracia, fin de mes 31-ene → 28/29-feb, año bisiesto), tarifa vigente (RF-23), estado (RF-35, incluido SUSPENDIDO con pago al día), acceso (RF-39: inferencia, anti-rebote, cada motivo de denegación, salida siempre permitida) y bloqueo (RF-07: 5 fallos, desbloqueo a los 15 min con `Clock` fijo). | `./mvnw test` | M |
| RNF-22 | Tests de integración de seguridad (RNF-01 a RNF-03) y del flujo alta → activar → login → pagar → aprobar → lectura de ENTRADA. | `./mvnw test` en CI | M |
| RNF-23 | Las migraciones se validan contra PostgreSQL 15 real (Testcontainers) con Flyway + `validate`. | CI en verde | S |
| RNF-24 | Cobertura de líneas ≥ 70 % en el paquete `service` (JaCoCo). | Reporte de JaCoCo | C |
| RNF-25 | Se actualizan `CLAUDE.md` (API, deuda resuelta) y `.env.example` con las variables de RNF-07 y `CORS_ORIGENES`; las propiedades con valor por defecto (`jwt.expiracion.*`, `seguridad.pin.*`, `codigo-validacion.horas`, `usuarios.vencimiento.*`, `accesos.antirrebote-segundos`, `accesos.apertura-segundos`) quedan en `application.properties`. | Revisión | M |
| RNF-26 | `uploads/` se elimina del repo y se añade a `.gitignore`. | `git ls-files uploads` vacío | M |

### Escalabilidad (sin sobrediseño)

| ID | Descripción | Verificación | Prior. |
|---|---|---|---|
| RNF-27 | Capacidad, cupos y tarifas son datos, no constantes: la capacidad es el número de cupos activos por tipo; añadir un cupo o una tarifa no requiere cambiar código Java. Todas las tablas raíz llevan `parqueadero_id`. | Crear el cupo `C7`: aparece en `GET /api/cupos` y suma 1 a `cupos` CARRO en `GET /api/accesos/ocupacion` | M |
| RNF-28 | Los instantes se guardan como `timestamptz` y se presentan en `America/Bogota`; los periodos como `date`. | Test de un evento a las 23:30 hora local | M |

### Privacidad (Ley 1581 de 2012)

| ID | Descripción | Verificación | Prior. |
|---|---|---|---|
| RNF-29 | Cada usuario solo accede a sus datos (RNF-02, RF-42). El admin ve datos operativos (usuarios, pagos, accesos) y, en F3, agregados; la analítica no devuelve datos personales salvo morosidad, necesaria para la operación. | Tests y revisión del contrato F3 | M |
| RNF-30 | Minimización: solo se guardan teléfono, nombre, apellido, datos del vehículo, comprobantes y eventos de acceso; no se guardan documento de identidad, correo ni imágenes de la cámara. | Revisión del esquema V1 | M |
| RNF-31 | Queda registro de la autorización de cada titular (`consentimiento_datos_en` y `consentimiento_version`) al activar; la BD impide una cuenta validada sin consentimiento. | Consulta a la BD tras la activación | M |
| RNF-32 | Retención: los comprobantes se conservan mientras exista el historial de pagos; la baja lógica no los borra. Se documenta en el aviso de privacidad, y el volumen `uploads` se respalda junto con el `pg_dump`. | Revisión del aviso y del procedimiento de backup | M |

---

## 6. Historias de usuario

### Administrador

**HU-A01 — Dar de alta a un usuario** (RF-11, RF-12, RF-14)
- Dado un cupo `C3` libre de tipo CARRO, cuando el admin envía un alta válida con placa `abc-123`, tipo CARRO y carrocería SEDAN, entonces recibe 201 con `codigoValidacion` de 8 caracteres y `codigoExpiraEn` a 72 h, la placa guardada es `ABC123`, el usuario queda VENCIDO y `C3` aparece con `ocupado=true`.
- Dado que `C3` ya está asignado, cuando el admin da otra alta con ese `cupoId`, entonces recibe 409 y no se crea nada.
- Dado el cupo `M1` (MOTO), cuando el admin envía un vehículo CARRO para ese cupo, entonces recibe 409 y no se crea nada.
- Dado un alta de MOTO con `carroceria=SEDAN`, cuando se envía, entonces recibe 400.
- Dado un alta ya creada, cuando el admin lista usuarios, entonces ningún elemento contiene `codigoValidacion` ni `pin`.

**HU-A02 — Revisar un pago** (RF-27 a RF-29)
- Dado un pago PENDIENTE de un usuario CARRO cuyo último periodo aprobado termina el 2026-10-14, cuando el admin lo aprueba el 2026-10-10 con `montoConfirmado=80000`, entonces queda APROBADO con periodo del 2026-10-15 al 2026-11-14 y `montoEsperado=80000`.
- Dado un usuario cuyo último periodo aprobado terminó el 2026-08-31 (gracia de 5 días), cuando el admin aprueba su pago el 2026-10-07, entonces el periodo va del 2026-10-07 al 2026-11-06 y el usuario queda ACTIVO.
- Dado un pago PENDIENTE con `ocrEstado=FALLIDO`, cuando el admin aprueba sin `montoConfirmado`, entonces recibe 400.
- Dado un pago APROBADO, cuando el admin intenta rechazarlo o aprobarlo otra vez, entonces recibe 409.
- Dado un pago PENDIENTE, cuando el admin lo rechaza con motivo "Comprobante ilegible", entonces queda RECHAZADO y el estado del usuario no cambia.

**HU-A03 — Suspender, reactivar y cortesía** (RF-14, RF-19, RF-34, RF-35)
- Dado un usuario ACTIVO, cuando el admin lo suspende con motivo, entonces su siguiente lectura de ENTRADA es DENEGADA con `USUARIO_SUSPENDIDO`.
- Dado un usuario SUSPENDIDO cuyo periodo venció hace 30 días, cuando el admin lo reactiva, entonces el estado resultante es VENCIDO.
- Dado cualquier usuario, cuando el admin envía `{accion:"SUSPENDER"}` sin motivo o una acción distinta de SUSPENDER/REACTIVAR, entonces recibe 400.
- Dado un usuario VENCIDO sin pagos, cuando el admin registra un pago manual con `montoConfirmado=0` y observación "Cortesía", entonces el pago nace APROBADO con `registradoPor=ADMIN` y el usuario queda ACTIVO; con `montoConfirmado=80000` recibe 400.

**HU-A04 — Simular la cámara y la puerta** (RF-38 a RF-42, RF-58 a RF-60)
- Dado un usuario ACTIVO con placa `ABC123` fuera del parqueadero, cuando el simulador envía una lectura de `ABC123` con la API key, entonces recibe 200 con `tipo=ENTRADA`, `tipoInferido=true`, `resultado=PERMITIDO`, `puertaAbierta=true`, y `GET /api/accesos/ocupacion` muestra un CARRO más ocupado.
- Dado que `ABC123` entró hace 30 s, cuando llega otra lectura, entonces se devuelve el evento anterior con `duplicado=true` y no se crea otro.
- Dado que `ABC123` está dentro desde hace 2 h, cuando el admin envía una lectura con `tipo=ENTRADA`, entonces recibe 403 con `motivo=YA_DENTRO` y el evento queda registrado.
- Dado un usuario VENCIDO con el vehículo dentro, cuando llega su lectura, entonces es SALIDA PERMITIDA con `motivo=SALIDA_CON_DEUDA`.
- Dada la placa `ZZZ999` no registrada, cuando llega una lectura, entonces recibe 403 con ENTRADA DENEGADA, `PLACA_DESCONOCIDA`, sin usuario, y se guarda.
- Dada una petición con una API key incorrecta o con API key válida a `GET /api/usuarios`, entonces recibe 401 o 403 respectivamente.
- Dado un usuario VENCIDO, cuando el admin abre con `PUT /api/puerta {abierta:true, placa, observacion}`, entonces el evento queda PERMITIDO con `origen=MANUAL_ADMIN` y `motivo=FORZADO_ADMIN`; sin `observacion` recibe 400.

**HU-A05 — Cambiar la tarifa** (RF-22, RF-23)
- Dada la tarifa CARRO de 80.000 vigente desde 2026-01-01, cuando el admin crea una de 100.000 vigente desde 2027-01-01, entonces un pago con `periodo_inicio=2027-01-15` tiene `montoEsperado=100000` y uno con `periodo_inicio=2026-12-20` tiene 80.000.
- Dada la semilla, cuando se calcula el monto de una MOTO con `periodo_inicio=2025-06-01`, entonces es 8.000; con `2026-06-01`, 10.000.
- Cuando el admin crea una tarifa con `vigenteDesde` anterior a hoy, entonces recibe 400; con una fecha que ya tiene tarifa de ese tipo, 409.

**HU-A06 — Reiniciar el PIN de un usuario** (RF-15, RF-03)
- Dado un usuario validado y bloqueado, cuando el admin regenera el código, entonces recibe un código nuevo una sola vez y el bloqueo se levanta; el código anterior ya no activa la cuenta.
- Dado el código nuevo, cuando el usuario se activa con un PIN nuevo, entonces el login con el PIN anterior falla con 401 y el nuevo funciona.

**HU-A07 — Consultar historial y ocupación** (RF-43, RF-60)
- Dados 30 eventos en octubre, cuando el admin consulta `GET /api/accesos?desde=2026-10-01&hasta=2026-10-31&size=20`, entonces recibe 20 elementos y `totalElements=30`.

### Usuario del parqueadero

**HU-U01 — Activar mi cuenta** (RF-03 a RF-05, RF-07, RF-13)
- Dado un código válido entregado por el admin, cuando envío teléfono, código, PIN `482915`, `aceptaTratamientoDatos=true` y la versión del aviso, entonces recibo 204, quedan guardadas la fecha y la versión del consentimiento y puedo iniciar sesión.
- Dado un código válido, cuando envío el PIN `123456` o `12345`, o `aceptaTratamientoDatos=false`, entonces recibo 400 y la cuenta sigue sin validar.
- Dado un código emitido hace más de 72 h o ya usado, cuando intento activar, entonces recibo 401 y la cuenta no cambia.

**HU-U02 — Iniciar sesión** (RF-06 a RF-08)
- Dada mi cuenta validada, cuando inicio sesión con el PIN correcto, entonces recibo un token cuyo `sub` es mi id, `rol=USUARIO` y que expira a las 12 h.
- Dados 5 intentos fallidos seguidos, cuando hago el sexto con el PIN correcto antes de 15 min, entonces recibo 423; pasados 15 min, el PIN correcto funciona y el contador vuelve a 0.
- Dado que estoy VENCIDO, cuando inicio sesión, entonces puedo hacerlo y ver mi estado.

**HU-U03 — Subir mi comprobante** (RF-24 a RF-26, RF-57, RNF-05)
- Dado que estoy autenticado con cupo CARRO y sin pagos aprobados, cuando consulto `GET /api/pagos/proximo-periodo` el 2026-10-07, entonces veo inicio 2026-10-07, fin 2026-11-06 y `montoEsperado=80000`.
- Cuando subo un JPEG de 1 MB, entonces recibo 201 con el pago PENDIENTE, el periodo propuesto y los valores OCR.
- Dado que el OCR está apagado, cuando subo un comprobante válido, entonces recibo 201 con `ocrEstado=FALLIDO` en menos de 12 s.
- Dado que ya tengo un pago PENDIENTE, cuando subo otro, entonces recibo 409.
- Cuando subo un PDF o un archivo de 6 MB, entonces recibo 415 o 413 y no queda archivo en disco.
- Dado que estoy SUSPENDIDO, cuando subo un comprobante, entonces recibo 403.

**HU-U04 — Ver solo mis datos** (RF-31, RF-32, RF-42, RF-44, RF-46, RNF-02)
- Dado que soy el usuario A, cuando consulto `GET /api/pagos/mios`, entonces solo veo mis pagos.
- Cuando pido `GET /api/pagos/{id}` o `/comprobante` de un pago del usuario B, entonces recibo 404.
- Cuando llamo a `GET /api/usuarios` o `GET /api/accesos` con mi token, entonces recibo 403.
- Cuando consulto `GET /api/puerta` y el último evento es de otro usuario, entonces la respuesta no incluye `ultimoEvento`.
- Cuando consulto `GET /api/usuarios/yo` con un pago aprobado hasta el 2026-10-10, entonces veo `vigenteHasta=2026-10-10` y mi estado ACTIVO.

### Sistema

**HU-S01 — Vencimiento automático** (RF-35, RF-36)
- Dado un usuario ACTIVO con `periodo_fin=2026-10-01` y gracia de 5 días, cuando el job se ejecuta el 2026-10-06, entonces sigue ACTIVO; cuando se ejecuta el 2026-10-07, pasa a VENCIDO.
- Dado un usuario SUSPENDIDO con pago al día, cuando corre el job, entonces sigue SUSPENDIDO.
- Dado que el servidor estuvo apagado a las 00:05, cuando arranca, entonces el estado queda recalculado sin intervención.

**HU-S02 — Aprobación que reactiva** (RF-28, RF-35)
- Dado un usuario VENCIDO, cuando se aprueba un pago, entonces queda ACTIVO en la misma transacción (salvo que esté SUSPENDIDO).

**HU-S03 — Datos preparados para la simulación** (RF-48, RF-49)
- Dada la BD recién migrada, cuando se inspecciona el esquema, entonces `usuario`, `vehiculo`, `pago` y `evento_acceso` tienen `simulado` no nulo con valor por defecto `false`.
- Dado un usuario con `simulado=true` insertado directamente con el mismo cupo y la misma placa que un usuario real, cuando se inserta, entonces no viola la unicidad; y cuando el admin lista usuarios o consulta la ocupación, no aparece.

**HU-S04 — Esquema reproducible** (RNF-18, RNF-19, RF-02)
- Dado un volumen de PostgreSQL vacío, cuando arranca el backend, entonces Flyway aplica V1 y V2 y hay 1 parqueadero, 7 cupos (6 CARRO y 1 MOTO), 4 tarifas (CARRO 64.000 y MOTO 8.000 desde 2025-01-01; CARRO 80.000 y MOTO 10.000 desde 2026-01-01), 1 puerta, 1 cámara simulada y 1 administrador creado desde las variables de entorno con la contraseña solo como hash.

---

## 7. Contrato de la API — Fase 1

Prefijo `/api`, sin versión (decisión de arquitectura). Cuerpos JSON salvo donde se indica multipart. Detalle de DTOs en `fase-1-modelo.md` §5.

| Método y ruta | Rol | Prior. | RF |
|---|---|---|---|
| `POST /admin/login` | P | M | 01 |
| `POST /auth/activar` | P | M | 03, 13 |
| `POST /auth/login` | P | M | 06 |
| `GET /ping` | P | S | RNF-17 |
| `GET /legal/aviso-privacidad` | P | S | 47 |
| `GET /usuarios`, `GET /usuarios/{id}`, `POST /usuarios`, `PUT /usuarios/{id}`, `DELETE /usuarios/{id}` | A | M | 11, 16–18 |
| `PUT /usuarios/{id}/vehiculo`, `PUT /usuarios/{id}/estado`, `POST /usuarios/{id}/codigo` | A | M | 15, 17, 19 |
| `GET /usuarios/yo` | U | M | 46 |
| `PUT /usuarios/yo/pin` | U | S | 56 |
| `GET /cupos` | A | M | 20 |
| `POST /cupos`, `PUT /cupos/{id}` | A | S | 21 |
| `GET /tarifas` | A, U | M | 22 |
| `POST /tarifas` | A | M | 22 |
| `GET /pagos/proximo-periodo` | U | M | 57 |
| `POST /pagos` (multipart) | U | M | 24–27 |
| `POST /pagos/manual` (multipart) | A | M | 34 |
| `GET /pagos` | A | M | 30 |
| `GET /pagos/mios` | U | M | 31 |
| `GET /pagos/{id}`, `GET /pagos/{id}/comprobante` | A, U dueño | M | 30, 32 |
| `PUT /pagos/{id}/aprobar`, `PUT /pagos/{id}/rechazar` | A | M | 28, 29 |
| `POST /accesos/lecturas` | S, A | M | 38, 39, 58 |
| `GET /accesos`, `GET /accesos/ocupacion` | A | M | 43, 60 |
| `GET /accesos/mios` | U | S | 44 |
| `GET /puerta` | A, U | M | 42 |
| `PUT /puerta` | A (M), U (S) | M/S | 59 |
| `GET /camaras` | A, U | M | 45 |
| `POST /camaras`, `PUT /camaras/{id}` | A | M | 45 |
| `GET /usuarios/yo/resumen` | U | W (F3) | 50 |
| `GET /analitica/*` | A | W (F3) | 51–55 |

---

## 8. Cambios de contrato (para la fase móvil y el nuevo panel)

Romper el contrato está aceptado (se rompe una vez, sin `/v1`). Consumidores: `mobile/.../data/network/ApiService.kt` (FM) y `frontend/js/*.js` (se reemplaza en F3). Tabla de impacto verificada en `fase-1-modelo.md` §5.

| Antes | Después | Consumidor | Notas |
|---|---|---|---|
| `POST /api/admin/login` responde el token como **texto plano** | Mismo cuerpo `{username, password}`; responde `TokenDTO {token, rol, expiraEn}`; 423 si está bloqueado | `frontend/js/login.js` | Leer `response.json().token` |
| `POST /api/usuarios/login?telefono&pin` → `{token}` | `POST /api/auth/login` con JSON `{telefono, pin}` → `TokenDTO`; PIN de 6 dígitos; 423 si está bloqueado | `ApiService.login` | `sub` del JWT pasa de teléfono a id |
| `POST /api/usuarios/registro` (público) | **Eliminado** → `POST /api/usuarios` (ADMIN) con vehículo y cupo | `ApiService.registrarUsuario`, `usuarios.js` | La app ya no da de alta; el panel muestra el código una vez |
| `POST /api/usuarios/solicitar-validacion` | **Eliminado** | — | |
| `POST /api/usuarios/verificar-codigo` y `POST /api/usuarios/validar?telefono&codigo&nuevoPin` | `POST /api/auth/activar` con `{telefono, codigo, pin, aceptaTratamientoDatos, versionConsentimiento}` | `ApiService.verificarCodigo`, `validarCodigoYPin`, `usuarios.js` | Código, PIN y consentimiento en un solo paso |
| `GET /api/usuarios/yo` | Misma ruta; `UsuarioDTO` nuevo | `ApiService.obtenerMiUsuario` | Sin `pin`, `codigoValidacion`, `verificado` ni `activo`; `validado` sustituye a `verificado` |
| `GET /api/usuarios` | Misma ruta, solo ADMIN, filtros `estado` e `incluirBajas` | `usuarios.js` | |
| `PUT /api/usuarios/{id}` (aceptaba `pin` y `estado`) | `{telefono?, nombre?, apellido?, cupoId?}` + `PUT /api/usuarios/{id}/vehiculo` | `usuarios.js` | Ya no se cambia el PIN ni el estado por aquí |
| `PUT /api/usuarios/{id}/estado?estado=` | Cuerpo `{accion: SUSPENDER\|REACTIVAR, motivo?}` | `usuarios.js` | |
| `DELETE /api/usuarios/{id}` (borrado físico) | Misma ruta, baja lógica | `usuarios.js` | |
| `POST /api/pagos` multipart `userId, placa, start, end, image?` | Misma ruta, solo USUARIO, multipart `comprobante` (obligatorio) | `ApiService.subirPago`, `usuarios.js`, `pagos.js` | Sin `userId`, `placa` ni periodo; `PaymentDTO` pasa a `PagoDTO` |
| `GET /api/pagos`, `GET /api/pagos/{id}` (públicos) | `GET /api/pagos` (ADMIN, paginado), `GET /api/pagos/mios` (USUARIO), `GET /api/pagos/{id}` (ADMIN o dueño) | `pagos.js`, `usuarios.js` | Filtro `usuarioId` en vez de filtrar en el cliente |
| `PUT /api/pagos/{id}` (edición libre) | `PUT /api/pagos/{id}/aprobar` y `/rechazar` | `pagos.js` | |
| `DELETE /api/pagos/{id}` | **Eliminado** | `pagos.js` | |
| `GET /uploads/{archivo}` (público) | `GET /api/pagos/{id}/comprobante` con token | `pagos.js` | `fetch` + `Blob`; quitar `http://localhost:8080` fijo |
| `GET /api/puerta` → `{abierta}` | `{abierta, abiertaHasta, ultimoEvento?}` (ADMIN, USUARIO) | `ApiService.obtenerEstadoPuerta`, `puerta.js` | Compatible: `abierta` se mantiene |
| `PUT /api/puerta {abierta}` | Misma forma; ADMIN debe enviar `placa` al abrir; genera evento; 403 con el evento si se deniega | `ApiService.actualizarEstadoPuerta`, `puerta.js` | La app se adapta en FM |
| — | `POST /api/accesos/lecturas` con `X-Api-Key` (simulador) o JWT ADMIN | Simulador, panel | Nuevo |
| `GET /api/camaras` público | Requiere token (ADMIN o USUARIO); `CamaraDTO` añade `simulada` | `ApiService.getCamaras`, `camaras.js`, `puerta.js` | |
| `POST/PUT /api/camaras` públicos | Solo ADMIN; `PUT` recibe `{activa}` | `camaras.js` | |
| — | Nuevos: `/api/auth/*`, `/api/cupos`, `/api/tarifas`, `/api/pagos/proximo-periodo`, `/api/pagos/manual`, `/api/accesos`, `/api/accesos/mios`, `/api/accesos/ocupacion`, `/api/usuarios/yo/pin`, `/api/ping`, `/api/legal/aviso-privacidad` | Next.js y FM | |

**DTOs** (definición completa en arquitectura §5)
- `UsuarioDTO {id, telefono, nombre, apellido, estado, suspendidoMotivo, validado, bloqueadoHasta, cupo{id, codigo, tipoVehiculo}, vehiculo, vigenteHasta, creadoEn}`; `UsuarioDetalleDTO`; `UsuarioAltaDTO` / `UsuarioAltaRespuestaDTO {usuario, codigoValidacion, codigoExpiraEn}`; `UsuarioActualizacionDTO`; `CambioEstadoDTO`; `CodigoValidacionDTO`; `CambioPinDTO`.
- Autenticación: `AdminLoginRequest {username, password}`, `ActivacionDTO`, `LoginUsuarioDTO`, `TokenDTO {token, rol, expiraEn}` común a los dos logins.
- `PaymentDTO` → `PagoDTO {id, usuarioId, usuarioNombre, placa, periodoInicio, periodoFin, montoEsperado, montoOcr, fechaPagoOcr, ocrEstado, montoConfirmado, estado, motivoRechazo, observacion, registradoPor, posibleDuplicado, comprobanteUrl, creadoEn, revisadoEn}`; `PeriodoDTO`, `AprobacionPagoDTO`, `RechazoPagoDTO`. Se eliminan `ocrData`, `imageUrl`, `serviceStart/End`, `amount` y `paymentDate`.
- `PuertaDTO {abierta, abiertaHasta, ultimoEvento?}`, `PuertaComandoDTO`, `LecturaPlacaDTO`, `EventoAccesoDTO`, `OcupacionDTO`, `CupoDTO`, `TarifaDTO`, `CamaraDTO`.
- Errores: `ProblemDetail` (RNF-11); antes eran texto plano o 500.
- Fechas: `LocalDate` en ISO `yyyy-MM-dd`; instantes en ISO-8601 con offset (`2026-10-07T18:30:00-05:00`).

---

## 9. Dependencias de datos

- **Estado del usuario** depende de pagos APROBADOS con periodo; sin pago aprobado el usuario está VENCIDO y no entra (RF-14). Para probar la entrada hay que aprobar un pago (o una cortesía de 0) antes, o sembrar datos en los tests.
- **Reglas de acceso y ocupación** dependen de `evento_acceso`, que registra también los DENEGADOS, y de que cada vehículo activo tenga placa única y normalizada. Si se pierde una SALIDA, la inferencia se desfasa una vez; el admin lo corrige con una lectura con `tipo` explícito.
- **Monto esperado** depende de que exista una tarifa con `vigente_desde <= periodo_inicio` para el tipo del cupo; si no, 409 `SIN_TARIFA_VIGENTE`. La semilla cubre desde 2025-01-01 para que el histórico simulado de F2 tenga tarifa.
- **Analítica F3** (ocupación, permanencia, pronóstico) depende de `evento_acceso` con `ocurrido_en`, `tipo` y `resultado`, y de `pago` APROBADO con `monto_confirmado` y periodo. Entrada y salida se emparejan por vehículo (RF-40). Las tablas que escriba Python se crean con migraciones de este repo.
- **Generador F2** inserta directamente en la BD usuarios, vehículos, pagos y eventos con fechas pasadas y `simulado = true`; la unicidad y las consultas operativas excluyen esas filas (RF-49).

---

## 10. Supuestos

- S-01. Un usuario tiene **un** vehículo activo y **un** cupo; los vehículos anteriores quedan inactivos para conservar el historial. Varios cupos por usuario requerirían una tabla de asignación.
- S-02. Un pago cubre exactamente un mes; no hay pagos parciales ni de varios meses.
- S-03. Un solo administrador; la tabla `administrador` admite más sin cambiar el login.
- S-04. En F1 la puerta la accionan el admin desde el panel (`PUT /api/puerta`, `POST /api/accesos/lecturas`) y el simulador de cámara con API key. La rama USUARIO de `PUT /api/puerta` se implementa según ADR 0004 pero se usa a partir de FM.
- S-05. La apertura manual del admin (`PUT /api/puerta`) es siempre PERMITIDA y, cuando las reglas la denegarían, queda trazada con `FORZADO_ADMIN` y observación (interpretación de ADR 0004).
- S-06. Los formatos de placa son los colombianos estándar (carro `AAA123`, moto `AAA12A` o `AAA12`); no se admiten placas diplomáticas ni extranjeras.
- S-07. El OCR Flask actual (que devuelve `fecha` y `valor`) se sigue usando sin cambios de contrato en F1, salvo quitar el `print` del texto completo (RNF-08).
- S-08. JPEG/PNG basta: el OCR (PIL + Tesseract) no procesa PDF.
- S-09. No se valida la capacidad al entrar: con cupo fijo por usuario, la capacidad se garantiza al asignar el cupo (ADR 0004).

---

## 11. Decisiones y preguntas

Resueltas:
- **P-01. Tarifa de la moto:** 8.000 COP desde 2025-01-01 y 10.000 COP desde 2026-01-01 (CARRO: 64.000 y 80.000). Reflejado en §3, RF-23 y HU-A05/HU-S04.
- **P-02. Periodo tras una mora:** continúa sin hueco si el último pago aprobado está dentro de la gracia; si no, empieza el día de aprobación (RF-27).
- **P-03. Estado inicial:** VENCIDO hasta el primer pago aprobado; el admin no fuerza ACTIVO; la cortesía es un pago manual de 0 (RF-14, RF-34).
- **P-04. Apertura desde la app:** queda para FM; en F1 la puerta la acciona el admin o el simulador (S-04).
- **P-05. Doble entrada:** se deniega y se registra como evento DENEGADO con `YA_DENTRO` (RF-39).
- **P-06. Generador F2:** escribe directamente en la BD con `simulado = true`; F1 no expone `simulado` ni `ocurridoEn` libre (RF-38, RF-48).
- **P-07. Retención de comprobantes:** se conservan mientras exista el historial; la baja lógica no los borra (RNF-32).
- **P-08. Analítica F3:** Spring hace de gateway hacia FastAPI (§4.9).
- **P-09. Pago en efectivo:** fuera de F1; `POST /api/pagos/manual` solo admite cortesías de 0 (RF-34).
- **P-10. Tarifas semilla:** 64.000 desde 2025-01-01 y 80.000 desde 2026-01-01 para CARRO (MOTO en P-01).

Abiertas: ninguna por ahora.

---

## 12. Diferencias con el diseño de arquitectura (para reflejar en `fase-1-modelo.md` y los ADR)

| Tema | Arquitectura / ADR | Este documento | Origen |
|---|---|---|---|
| Credenciales del admin | `ADMIN_USERNAME` + `ADMIN_PASSWORD_HASH`, sin tabla, `sub = "admin"`, bloqueo en memoria (ADR 0003) | Tabla `administrador` creada al arrancar desde `ADMIN_USERNAME`/`ADMIN_PASSWORD` (solo hash), `sub` = id, bloqueo 5/15 persistido (RF-01, RF-02, RF-07, RF-08). Falta la tabla en el DDL V1 | Orquestador |
| Tarifas semilla | CARRO y MOTO 80.000 desde 2025-01-01 | CARRO 64.000 (2025-01-01) y 80.000 (2026-01-01); MOTO 8.000 y 10.000 en las mismas fechas | Dueño |
| Cálculo del periodo | Se calcula al recibir el comprobante con "hoy" (ADR 0002) | Propuesto al subir y **recalculado al aprobar** con la fecha de aprobación (RF-27) | P-02 |
| Motivo `YA_DENTRO` | No está en `ck_evento_motivo` | Necesario para P-05 (RF-39) | P-05 |
| `simulado` en `LecturaPlacaDTO` | Aceptado con ADMIN/SISTEMA | No se acepta en F1 (RF-38) | P-06 |
| `POST /api/pagos/manual` | Efectivo o cortesía | Solo cortesía (`montoConfirmado = 0`) en F1 (RF-34) | P-09 |
| `ultimoEvento` en `GET /api/puerta` para USUARIO | Sin restricción | Solo si es suyo (RF-42) | Privacidad |
| `GET /api/legal/aviso-privacidad` | No existe; no está en la lista de `permitAll` | Público, Should (RF-47), para que la app muestre el texto cuya versión envía en la activación | Ley 1581 |
