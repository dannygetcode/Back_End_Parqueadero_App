# ADR 0003 — Autenticación, roles, PIN y bloqueo

- Estado: Aceptado
- Fecha: 2026-10-07

## Contexto

Problemas del código actual (verificados en el fuente, commit 7f815d8):

- `JwtService.generarToken(subject)` firma solo un `sub`; el `sub` del usuario es el **teléfono** y el del admin
  es su **nombre de usuario**. No hay roles: `JwtAuthenticationFilter` crea la autenticación con
  `Collections.emptyList()`, así que cualquier token válido de usuario sirve para endpoints de admin.
- `SecurityConfig` deja públicos `/api/pagos/**`, `/api/usuarios/registro`, `/api/usuarios/solicitar-validacion`
  (que **crea** usuarios), `/uploads/**` y los GET/POST/PUT de `/api/camaras/**`.
- El PIN es de 4 dígitos y se guarda y se devuelve en claro (`UsuarioDTO.pin`, en `listarTodos` y `/yo`).
- `AdminController` compara la contraseña en claro con `admin.password`; responde el token como texto plano.
- Sin límite de intentos (resuelto: bloqueo por cuenta y límite por IP, ver Decisión).

Decisiones del dueño: alta solo por admin; código de validación entregado por el admin; PIN de 6 dígitos con
BCrypt; bloqueo de 15 min tras 5 fallos; JWT con `sub` = id y claim `rol`; un solo admin con contraseña BCrypt
desde variables de entorno; Next.js usará BFF con cookie httpOnly en una fase posterior.

## Decisión

**Roles** (`ROLE_` en Spring Security): `ADMIN`, `USUARIO` y `SISTEMA` (simulador de cámara, ver ADR 0004).

**Token JWT** (jjwt 0.11.5, HS256, secreto `JWT_SECRET` >= 32 bytes, ya existe):

- Claims: `sub` = id como texto (de `usuario` o de `administrador`); `rol` =
  `ADMIN` | `USUARIO`; `iat`, `exp`.
- Expiración configurable: `jwt.expiracion.admin` = 4 h, `jwt.expiracion.usuario` = 12 h. Sin refresh tokens en
  Fase 1: el usuario vuelve a meter el PIN.
- `JwtAuthenticationFilter` pone como principal el `sub` y como autoridad `ROLE_<rol>`. Los controladores obtienen
  el id del usuario autenticado desde el `SecurityContext`, nunca desde un parámetro (`userId` deja de venir en el
  request de pagos).
- La respuesta de login pasa a ser JSON `{ "token", "rol", "expiraEn" }` también para el admin (hoy es texto plano).
- Revocación: no hay lista negra. Mitigación: las operaciones sensibles del usuario (abrir puerta, subir pago)
  releen el usuario en BD y rechazan si está SUSPENDIDO, dado de baja o bloqueado. Un token robado vale como
  máximo 12 h.

**Ciclo de vida del usuario**:

1. El admin crea el usuario con su vehículo y cupo (`POST /api/usuarios`). El backend genera un código de
   validación de 8 caracteres (A-Z, 0-9, `SecureRandom`, ya existe), guarda **solo su hash BCrypt** y una
   expiración (`codigo-validacion.horas`, 72 h por defecto), y devuelve el código **una sola vez** en la respuesta.
   El admin se lo entrega al usuario en persona o por su canal. Si se pierde, `POST /api/usuarios/{id}/codigo`
   genera otro.
2. El usuario, en la app, envía teléfono + código + PIN nuevo + aceptación de tratamiento de datos
   (`POST /api/auth/activar`). El backend verifica el código, guarda `pin_hash` (BCrypt), `validado_en`,
   `consentimiento_datos_en` y `consentimiento_version`, y anula el código. Sin aceptación explícita no se activa
   (Ley 1581 de 2012: autorización previa, expresa e informada; la fecha y la versión del texto son la prueba).
3. Login: `POST /api/auth/login` con teléfono + PIN.

**PIN**: exactamente 6 dígitos (`^\d{6}$`, validado con Bean Validation). BCrypt con `BCryptPasswordEncoder`
(coste 10). Se rechazan PIN triviales (`000000`, `123456`, todos iguales) con una lista corta.

**Bloqueo**: columnas `intentos_fallidos` y `bloqueado_hasta` en `usuario`.

- Cada fallo de PIN (o de código de validación) suma 1. Al llegar a 5: `bloqueado_hasta = now + 15 min` y se
  reinicia el contador. Mientras esté bloqueado se responde 423 sin comprobar el PIN.
- Un login correcto pone el contador en 0.
- Mensaje de error genérico ("teléfono o PIN incorrectos") para no revelar qué teléfonos existen.
- Parámetros en propiedades: `seguridad.pin.max-intentos=5`, `seguridad.pin.bloqueo-minutos=15`.
- Admin: el mismo límite, persistido en su fila de `administrador` (sobrevive a reinicios).
- El contador es atómico: login, activación, cambio de PIN y login del admin leen la fila con
  `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`), así que los intentos simultáneos sobre la misma cuenta se
  serializan y ninguno pierde su incremento (con 20 intentos paralelos: 5 se evalúan y bloquean, 15 reciben 423).
- **Respuesta uniforme para cuentas inexistentes** (elección: simular el bloqueo). Un teléfono o un usuario de admin
  que no existen se comportan igual que una cuenta real: 401 genérico y, tras 5 fallos, 423 durante 15 min. El
  contador de los inexistentes vive en memoria (`IntentosCuentasInexistentes`, sin crear filas por identificadores
  inventados, acotado a 10.000 claves) y se comparte entre login y activación, como la fila de un usuario. Así la
  diferencia 401/423 no permite enumerar cuentas. Se descartó "siempre 401 hasta que el bloqueo aplique" porque el
  423 es útil para el usuario legítimo y, en cualquier caso, también delataría la cuenta en cuanto se aplicara.
  Una cuenta creada pero sin activar también suma fallos de login en su fila. Limitación aceptada: tras reiniciar el
  backend, los contadores en memoria empiezan de cero (los reales persisten); no es observable desde fuera.

**Límite por IP** (`LimitePorIpFilter`): en `POST /api/admin/login`, `/api/auth/login` y `/api/auth/activar`, como
mucho `seguridad.limite-ip.max-peticiones` (10) peticiones por IP y ruta en una ventana deslizante de
`seguridad.limite-ip.ventana-segundos` (60 s), en memoria. Al superarlo: 429 `ProblemDetail` con `Retry-After`, sin
llegar a comprobar la credencial. Detrás de un proxy inverso se configura `seguridad.limite-ip.cabecera-ip-cliente`
(p. ej. `X-Real-IP`, que el proxy debe sobrescribir; de una lista tipo `X-Forwarded-For` se toma el último
elemento). Es por instancia: con varias réplicas haría falta un almacén compartido (Redis/Bucket4j).

**Escenario de DoS contra el admin**: como el bloqueo es por cuenta, cualquiera que conozca el usuario del admin puede
dejarlo bloqueado 15 min con 5 intentos (y repetirlo). Mitigaciones actuales: el límite por IP frena la fuerza bruta
y obliga a un atacante a rotar IPs para sostener el bloqueo; el usuario del admin no es público; el backend se publica
por defecto solo en `127.0.0.1` (compose) y el panel es de uso interno. Riesgo aceptado para Fase 1. Si se observa,
opciones: bloquear por par (cuenta, IP) en lugar de por cuenta, segundo factor para el admin o restringir
`/api/admin/login` a la red interna en el proxy.

**Admin** (actualizado en la implementación, resolución del orquestador): tabla `administrador` (`usuario` único,
`password_hash` BCrypt, `intentos_fallidos`, `bloqueado_hasta`) en V1. Al arrancar, si la tabla está vacía, se crea
un admin con `ADMIN_USERNAME`/`ADMIN_PASSWORD` guardando solo el hash; si ya hay uno, las variables se ignoran; si está
vacía y faltan, la aplicación no arranca. No se usa `ADMIN_PASSWORD_HASH`. Se eliminan las propiedades
`spring.security.user.*` (y `SPRING_SECURITY_*`), que no se usan con la cadena de filtros actual.

**Autorización**: reglas por ruta en `SecurityConfig` (tabla de endpoints en `fase-1-modelo.md`) más comprobación
de propiedad en el servicio ("el pago `{id}` es del usuario autenticado o el que llama es ADMIN"). Se quita todo
`permitAll` salvo `/api/auth/login`, `/api/auth/activar`, `/api/admin/login`, `/api/ping` y
`/api/legal/aviso-privacidad` (texto del aviso cuya versión se guarda con el consentimiento). Se elimina el
`@CrossOrigin(origins = "*")` de `CamaraController`; CORS se configura solo en `SecurityConfig`, con orígenes desde
una variable de entorno (`CORS_ORIGENES`), sin IPs en el código.

**Next.js (fase posterior)**: el BFF guarda el JWT del admin en una cookie httpOnly y llama al backend con
`Authorization: Bearer`. El backend no cambia: sigue siendo stateless y sin cookies, así que CSRF puede seguir
desactivado en el backend (la protección CSRF va en el BFF, con `SameSite=Strict`).

## Alternativas consideradas

- **Spring Authorization Server / OAuth2 / Keycloak**: correcto para muchos clientes y SSO; desproporcionado para
  un admin y 7 usuarios. Descartado.
- **Sesiones con cookie en el backend**: la app Android funciona mejor con bearer token. Descartado.
- **Refresh tokens**: añaden tabla, rotación y revocación. Se reconsidera si 12 h resulta incómodo en la app.
- **Admin solo por variables de entorno con `ADMIN_PASSWORD_HASH`** (propuesta inicial de este ADR): sustituida por
  la tabla `administrador`, que persiste el bloqueo, da un `sub` numérico homogéneo y admite más admins sin cambios.
- **Bloqueo por IP con Bucket4j/Redis**: con una sola instancia basta una ventana deslizante en memoria (sin
  dependencias nuevas). Se reconsidera con varias réplicas.

## Consecuencias

- **Rompe los contratos de login y registro**: `mobile/.../ApiService.kt` (`registrarUsuario`, `verificarCodigo`,
  `validarCodigoYPin`, `login`, `obtenerMiUsuario`) y `frontend/js/login.js` (lee el token con
  `response.text()`), `usuarios.js` (`/registro`, `/validar`). Se actualizan en la misma fase.
- `.env.example` documenta `ADMIN_USERNAME`/`ADMIN_PASSWORD` (sin valores reales); cambiar la contraseña de un admin
  ya creado requiere actualizar su fila (no hay endpoint en Fase 1).
- Los usuarios existentes se pierden con el reset (ADR 0001); no hay migración de PIN.
- Un token de usuario ya no sirve para endpoints de admin (hoy sí sirve).
