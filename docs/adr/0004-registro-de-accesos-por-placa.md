# ADR 0004 — Registro de accesos por placa con puerta y cámara simuladas

- Estado: Aceptado
- Fecha: 2026-10-07

## Contexto

No hay hardware: la cámara y la puerta son simuladas. La "cámara" captura una placa en cada entrada o salida, y
la placa identifica al vehículo y al usuario. El interruptor de la puerta (app del usuario y panel del admin) también
debe dejar registrado quién entra o sale. Hoy `PUT /api/puerta` solo cambia un booleano en la fila id=1, sin
registro ni regla de negocio, y la app móvil lo usa (`actualizarEstadoPuerta`). Los eventos de acceso son además la
serie temporal que usará la analítica.

Carga esperada: 7 vehículos, unas 2 a 6 lecturas por vehículo al día, es decir menos de 50 eventos al día.

## Decisión

**Un solo flujo para todo**: `AccesoService.registrar(lectura)` recibe placa, origen y opcionalmente tipo y
instante; lo usan la cámara simulada, la app y el panel. Lo hace todo en una transacción:

1. Normaliza la placa (mayúsculas, sin espacios ni guiones) y busca un `vehiculo` activo, no simulado.
2. **Anti-rebote**: si el mismo vehículo tiene un evento en los últimos `accesos.antirrebote-segundos` (60 s por
   defecto), no crea otro y devuelve el existente con `duplicado = true`. Excepción: la apertura forzada del admin
   justo después de una lectura denegada sí se registra (el admin decide abrir igual). Así no se duplica cuando la cámara lee
   dos veces o cuando el usuario pulsa "abrir" en la app y la cámara también lo lee.
3. **Inferencia ENTRADA/SALIDA**: se mira el último evento **PERMITIDO** del vehículo. Si fue ENTRADA, este es
   SALIDA; si fue SALIDA o no hay ninguno, este es ENTRADA. Si la lectura trae `tipo` explícito (solo lo admite el
   admin, o en el futuro una cámara con carril fijo), manda el explícito y se marca `tipo_inferido = false`.
4. **Reglas**:
   - Placa desconocida: ENTRADA, DENEGADO, motivo `PLACA_DESCONOCIDA`, sin FKs; se guarda `placa_leida`.
   - ENTRADA: PERMITIDO solo si el usuario no está dado de baja, su estado es `ACTIVO` y tiene cupo asignado
     del mismo tipo que el vehículo. Si no, DENEGADO con motivo (`USUARIO_VENCIDO`, `USUARIO_SUSPENDIDO`,
     `USUARIO_DE_BAJA`, `SIN_CUPO`, `TIPO_CUPO_DISTINTO`). Una ENTRADA explícita (solo el admin envía `tipo`) de un
     vehículo que ya está dentro se deniega con `YA_DENTRO` y queda registrada (decisión P-05 del dueño).
   - SALIDA: **siempre PERMITIDA** (no se retiene un vehículo dentro). Si el usuario está vencido se registra
     igual, con la observación correspondiente.
   - Origen `MANUAL_ADMIN` (`PUT /api/puerta` del admin): siempre queda PERMITIDO. Si las reglas la denegarían (o la
     placa es desconocida) se registra con motivo `FORZADO_ADMIN`, la `observacion` es obligatoria (si falta, 400) y
     se anota la regla que se saltó.
5. Si el resultado es PERMITIDO, abre la puerta: `puerta.abierta_hasta = now + accesos.apertura-segundos`
   (10 s). "Abierta" se calcula como `abierta_hasta > now()`: no hace falta un job que la cierre.
6. Devuelve `EventoAccesoDTO` (tipo, resultado, motivo, placa, usuario, puerta abierta, duplicado).

No se valida la capacidad en la ENTRADA: con cupo fijo por usuario, la capacidad ya está garantizada cuando se
asigna el cupo. Si en el futuro hay visitantes por horas, se agrega la comprobación "ocupados < cupos activos"
del tipo, con bloqueo de fila sobre `parqueadero`.

**Endpoint del simulador de cámara**: `POST /api/accesos/lecturas`

```json
{ "placa": "ABC123", "camaraId": 1, "ocurridoEn": "2026-10-07T08:15:00-05:00" }
```

`ocurridoEn` es opcional; si no viene se usa `now()`. Solo se acepta en el pasado y como mucho 5 minutos de
desfase, salvo que la petición sea del generador de datos simulados (ver más abajo).

**Autenticación del simulador**: rol `SISTEMA` con API key.

- Cabecera `X-Api-Key`. Un `ApiKeyAuthenticationFilter` (antes del filtro JWT) la compara en tiempo constante
  (`MessageDigest.isEqual`) con la variable de entorno `CAMARA_API_KEY` (32+ bytes aleatorios) y, si coincide,
  autentica con `ROLE_SISTEMA`.
- `ROLE_SISTEMA` **solo** puede llamar a `POST /api/accesos/lecturas`. Con ADMIN también se puede, para simular
  desde el panel.
- Sin `CAMARA_API_KEY` configurada, el filtro no autentica a nadie (falla cerrado).

**Cómo queda `/api/puerta`**:

- `GET /api/puerta` (ADMIN, USUARIO): `{ "abierta": bool, "abiertaHasta": instant, "ultimoEvento": {...} }`.
  Sigue teniendo `abierta`, compatible con el `PuertaDTO` de la app y del panel. Para un USUARIO, `ultimoEvento`
  solo se incluye si es suyo (privacidad).
- `PUT /api/puerta` se mantiene como **interruptor**, pero ya no escribe el booleano directamente:
  - `{ "abierta": true }` desde un **USUARIO**: la placa sale de su vehículo activo (no del body) y se llama a
    `AccesoService.registrar` con origen `APP_USUARIO`. Se aplican las mismas reglas: un usuario vencido no
    puede abrir para entrar.
  - `{ "abierta": true, "placa": "ABC123", "tipo": "ENTRADA"?, "observacion": "..."? }` desde **ADMIN**: `placa`
    es obligatoria y el origen es `MANUAL_ADMIN`.
  - `{ "abierta": false }` (ADMIN): cierra (`abierta_hasta = now`). No genera evento.
  - Respuesta: `PuertaDTO` con el `EventoAccesoDTO` anidado. 403 si se deniega el acceso, con el evento en el
    cuerpo (el intento denegado también queda registrado).
- Generador de datos de la Fase 2 (decisión P-06): escribe directamente en la BD con `simulado = true`. En Fase 1
  `LecturaPlacaDTO` no tiene `simulado` y `ocurridoEn` solo se acepta en el pasado con 5 min de desfase como mucho.
  Si SISTEMA envía `tipo`, 400.

**Consultas**: `GET /api/accesos` (ADMIN, con filtros de fecha, placa y resultado, paginado), `GET /api/accesos/mios`
(USUARIO) y `GET /api/accesos/ocupacion` (ADMIN): por tipo de vehículo, cupos activos y ocupados. Ocupados = vehículos
cuyo último evento PERMITIDO es ENTRADA.

## Alternativas consideradas

- **Guardar "dentro/fuera" en `vehiculo`** en vez de inferirlo de los eventos: más rápido de leer, pero son dos
  fuentes de verdad que se pueden desincronizar. A esta escala la consulta sobre eventos es instantánea. Descartada.
- **Cámaras con sentido fijo (una de entrada, otra de salida)**: es lo real con hardware. Se deja la columna `tipo`
  explícita en la lectura para soportarlo sin cambiar el modelo, pero hoy hay una sola cámara simulada.
- **Simulador autenticado con JWT de una cuenta de servicio**: obliga a hacer login y renovar el token desde un
  script. La API key es más simple y se puede rotar cambiando una variable de entorno. Descartada.
- **mTLS o firma HMAC por petición**: más seguro contra la repetición de peticiones, pero innecesario mientras el
  simulador corre en la misma red de Docker. Se reconsidera con hardware real expuesto a Internet.
- **Cola (RabbitMQ/Kafka) para las lecturas**: menos de 50 eventos al día. Descartada.
- **Job que cierra la puerta**: se sustituye por `abierta_hasta`, sin estado temporal que mantener.

## Consecuencias

- `PUT /api/puerta` cambia de semántica pero conserva la forma del body (`abierta`). La app móvil sigue
  funcionando sin cambios de DTO; el panel tiene que mandar `placa` al abrir (`frontend/js/puerta.js`).
- Toda apertura queda auditada en `evento_acceso`, incluidas las denegadas y las forzadas por el admin.
- El anti-rebote de 60 s impide registrar una salida y una nueva entrada del mismo vehículo en menos de un minuto.
  Es aceptable para un parqueadero mensual.
- Si se pierde un evento (por ejemplo, una SALIDA no leída), la inferencia se desfasa una vez. El admin lo corrige
  con un registro manual con `tipo` explícito.
