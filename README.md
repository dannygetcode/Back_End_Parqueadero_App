# Backend - Parqueadero

API REST (Spring Boot 3.5, Java 17) + servicio OCR (Flask/Tesseract) + servicio de pronostico (FastAPI) + PostgreSQL 15.
Sistema de un parqueadero de 7 cupos (6 carros y 1 moto): usuarios con cupo fijo y placa, pagos con comprobante y revision
del admin, control de la puerta con registro de entradas y salidas por placa, y analitica con pronostico de vacancia.
La puerta y las camaras son simuladas (no hay hardware).

## Que incluye
| Parte | Donde |
|---|---|
| API REST con seguridad por rol (ADMIN, USUARIO, SISTEMA), JWT, PIN con hash y bloqueo | `src/main/java`, ADR 0003 |
| Esquema versionado con Flyway (V1 a V4) | `src/main/resources/db/migration` |
| Pagos con revision (PENDIENTE, APROBADO, RECHAZADO) y OCR del comprobante | `docs/adr/0005` |
| Puerta y camara simuladas: entradas y salidas por placa | `docs/adr/0004` |
| Analitica (KPIs, ocupacion, ingresos, mora, permanencia) y resumen del usuario | `docs/contrato-analitica.md` |
| Pronostico de vacancia, ocupacion y llegadas (servicio FastAPI) | `pronostico/` |
| Datos simulados de 12 meses y historial demo para un usuario real | `simulador/` |
| Aviso de privacidad y condiciones de uso versionado (Ley 1581) | `src/main/resources/legal` |

## Documentacion
- Contrato de la API para la app movil (resumen compacto): `docs/contrato-movil.md`
- Contrato de la analitica y el pronostico (panel): `docs/contrato-analitica.md`
- Diseño de la Fase 1: `docs/arquitectura/fase-1-modelo.md`, ADR en `docs/adr/` y requisitos en `docs/requisitos/`
- Cambios de contrato para la app y el panel: `docs/cambios-de-contrato-fase-1.md`

## Configuracion
Todo se configura por variables de entorno (ver `.env.example`). Los secretos no van en git.

1. `cp .env.example .env` y reemplaza cada `change-me` (los comentarios de `.env.example` dicen como generar cada
   secreto, p. ej. `openssl rand -base64 48`).
2. `.env` esta en `.gitignore`; nunca lo subas.
3. El backend **no arranca** si una credencial vale `change-me`, si `JWT_SECRET` o `CAMARA_API_KEY` (cuando se define)
   tienen menos de 32 bytes, o si `ADMIN_PASSWORD` (cuando se define) tiene menos de 12 caracteres. El error nombra la
   variable, nunca el valor.

| Variable | Obligatoria | Uso |
|---|---|---|
| `POSTGRES_DB`, `DB_USER`, `DB_PASSWORD` | si | Base de datos (compose crea la BD con ellas) |
| `JWT_SECRET` | si | Firma de los JWT, 32 bytes o mas (`openssl rand -base64 48`). Sin ella no arranca |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | la primera vez | Crean el administrador si la tabla `administrador` esta vacia (solo se guarda el hash BCrypt). Despues se ignoran. `ADMIN_PASSWORD` de 12 caracteres o mas (`openssl rand -base64 18`) |
| `CAMARA_API_KEY` | para el simulador | Cabecera `X-Api-Key` del simulador de camara (rol SISTEMA). 32+ bytes aleatorios (`openssl rand -hex 32`); vacia = simulador deshabilitado |
| `CORS_ORIGENES` | no | Origenes permitidos separados por comas. Por defecto `http://localhost:5500,http://localhost:3000` |
| `SERVER_PORT` | no | Puerto publicado en el host (8080) |
| `SERVER_ADDRESS` | no | Fuera de Docker: `127.0.0.1` por defecto; `0.0.0.0` solo si hay que exponerlo en la LAN |
| `GRACIA_DIAS` | no | Dias de gracia (5). Backend y pronostico leen el mismo valor |
| `PRONOSTICO_URL` | si (compose la fija) | URL del servicio de pronostico; sin ella el backend no arranca |
| `DB_URL`, `OCR_SERVICE_URL`, `UPLOAD_DIR` | fuera de Docker | Compose ya los fija para los contenedores |
| `TESSERACT_CMD` | OCR fuera de Docker | Ruta de `tesseract.exe` en Windows |

Parametros con valor por defecto en `application.properties` (no son secretos): expiracion del JWT (admin 4 h,
usuario 12 h), bloqueo (5 intentos, 15 min), vigencia del codigo de validacion (72 h), dias de gracia (5) y cron del
vencimiento, anti-rebote (60 s) y apertura de la puerta (10 s), tamano maximo del comprobante (5 MB).

## Levantar (Docker Compose)
La BD usa un volumen **externo** para que `docker compose down -v` no la borre. La primera vez:
```
docker volume create backend_pgdata
docker compose up -d --build
```
Servicios: `db` (Postgres, healthcheck), `ocr` (solo red interna), `pronostico` (solo red interna, healthcheck en
`/salud`), `backend` (puerto `SERVER_PORT`, healthcheck en
`/api/ping`). Al arrancar, Flyway aplica las migraciones de `src/main/resources/db/migration` (V1 esquema, V2 datos
de referencia: 7 cupos, tarifas, puerta y camara simulada; V3 version de credenciales para revocar tokens; V4 indice de
eventos de acceso) y se crea el administrador.
La BD no se publica al host; para inspeccionarla anade temporalmente `ports: ["127.0.0.1:5432:5432"]` al servicio `db`.

El backend se publica solo en `127.0.0.1` (variable `BIND_ADDRESS` para cambiarlo). **TLS:** el backend habla HTTP;
en produccion hay que ponerlo detras de un proxy inverso (nginx, Caddy, un balanceador) que termine HTTPS, no exponer
el puerto 8080 directamente, y configurar `LIMITE_IP_CABECERA` con la cabecera de IP real del proxy.

### Servicio de pronostico
`pronostico/` (FastAPI, ver `pronostico/README.md`) lee la BD y calcula vacancia, ocupacion y llegadas. Compose lo
construye y lo conecta por la red interna (`http://pronostico:8000`); el backend depende de que este healthy y lo expone
a los ADMIN en `/api/analitica/pronostico/*` (contrato: `docs/contrato-analitica.md`).
- **No tiene autenticacion y no se publica al host** (sin `ports`): quien llegue a su puerto lee los pronosticos. No
  anadir `ports` ni ponerlo detras de un tunel; el unico cliente es el backend.
- Comparte las credenciales de BD del `.env` (solo hace `SELECT`, en transaccion de solo lectura).
- **Mejora pendiente:** crear un rol de Postgres de solo lectura (`GRANT SELECT`) para este servicio en lugar de la
  cuenta de la aplicacion; requiere gestionar su contrasena (nueva variable en `.env`), por eso no esta hecho aun.
- Si el servicio cae o tarda mas de 8 s, el backend responde 503 en esos endpoints; el resto de la API no se afecta.
- Revertir: quitar el servicio `pronostico`, su `depends_on` y `PRONOSTICO_URL` de `docker-compose.yml`, y el
  controlador/propiedades `pronostico.service.*` del backend (es obligatoria: sin quitarla no arranca).

## Verificar
- `docker compose ps` (db, ocr, pronostico y backend Up; db, pronostico y backend healthy; pronostico sin puertos publicados)
- `docker compose logs backend | grep -E "Successfully applied|Started"`
- `curl http://localhost:8080/api/ping`
- Login del admin: `curl -X POST localhost:8080/api/admin/login -H "Content-Type: application/json" -d '{"username":"...","password":"..."}'`
- Pronostico (con el token del admin): `curl -H "Authorization: Bearer $TOKEN" "localhost:8080/api/analitica/pronostico/vacancia?incluirSimulados=true"`; sin token da 401
- Simulador de camara: `curl -X POST localhost:8080/api/accesos/lecturas -H "X-Api-Key: $CAMARA_API_KEY" -H "Content-Type: application/json" -d '{"placa":"ABC123"}'`

## Reset de la base de datos (cambio de esquema incompatible)
El esquema lo gestiona solo Flyway (`ddl-auto=validate`); las migraciones aplicadas no se editan. Si hay que empezar
de cero (como en la Fase 1, ADR 0001):
1. Respaldo: `docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > ../backups/<fecha>.sql`
   y copia del volumen `uploads` (comprobantes).
2. `docker compose stop backend`
3. `docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'`
   (o borrar y recrear el volumen: `docker compose down`, `docker volume rm backend_pgdata`, `docker volume create backend_pgdata`)
4. `docker compose up -d`: Flyway vuelve a aplicar todas las migraciones (V1 a V4).

## Datos simulados
`simulador/` (Python, ver `simulador/README.md`) genera 12 meses de usuarios, vehiculos, pagos y entradas y salidas con
`simulado=true`. Los listados de administracion y la analitica los excluyen salvo `incluirSimulados=true`; el simulador
solo borra y regenera lo simulado, nunca datos reales. La BD no esta publicada: el simulador trae un override de compose
para publicarla en `127.0.0.1` solo mientras se ejecuta.
```
docker compose -f docker-compose.yml -f simulador/compose.publicar-bd.yml up -d db
pip install -r simulador/requirements.txt
python simulador/generar.py --limpiar
python simulador/verificar.py
docker compose up -d db      # vuelve a cerrar el puerto 5432
```
**Historial demo para un usuario real:** `python simulador/generar.py --limpiar --usuario-demo-placa PLACA` agrega al usuario
real de esa placa 12 meses de pagos y visitas simulados, justo antes de su primer periodo real, para ver la actividad
en la app. Esos registros tambien son simulados: `/api/pagos/mios` y `/api/accesos/mios` los muestran al dueño, pero no
cuentan como reales en la analitica ni afectan la puerta. Para quitarlos, ejecutar `generar.py --limpiar` sin la opcion.
`GET /api/accesos/mios?dias=90` (1 a 365, por defecto 30) permite pedir mas historia de visitas.

## Aviso de privacidad
El texto vigente esta en `src/main/resources/legal/aviso-privacidad-{version}.txt` y la version en
`legal.aviso-privacidad.version` (hoy `2026-10-14`). Para cambiarlo: crear el archivo de la version nueva (el anterior se
conserva, porque las aceptaciones guardan su version) y subir la propiedad. **Antes de publicar el servicio hay que
completar en el texto los datos del responsable del tratamiento** (nombre, identificacion, direccion y contacto).

## Revertir
`docker compose down` (conserva BD y comprobantes). `docker compose down -v` borra el volumen `uploads` (comprobantes)
pero no la BD (volumen externo).

## Desarrollo sin Docker
`docker compose up -d db` (publicando el puerto, ver arriba); exporta las variables de `.env` mas `DB_URL`,
`OCR_SERVICE_URL` y `UPLOAD_DIR` (ruta absoluta fuera del repo) y ejecuta `./mvnw spring-boot:run`.

## Tests
- `./mvnw test`: unitarios (`*Test`: calculo de periodo, placas, PIN, OCR). No necesitan Docker.
- `./mvnw verify`: ademas los de integracion (`*IT`) con MockMvc contra PostgreSQL 15 real levantado por
  **Testcontainers** (necesita Docker en marcha). Cubren migraciones + `validate`, matriz de autorizacion por rol,
  IDOR, PIN y bloqueo, alta -> activacion -> login, pagos (subida, tipo invalido, OCR caido, aprobacion), accesos
  (inferencia, anti-rebote, entrada repetida, salida siempre permitida), vencimiento, analitica y proxy del pronostico,
  limite de intentos por IP e historial propio (`/mios`).
- `pytest` en `pronostico/` y en `simulador/`: no necesitan Docker ni base de datos.
- Los tests usan sus propios valores de prueba; no leen `.env`.

## CI
`.github/workflows/ci.yml`: en cada push y pull request corre `./mvnw -B verify` con Java 17 (Testcontainers usa el
Docker del runner) y, en un job aparte, `pytest` de `pronostico/` y `simulador/` con Python 3.12. Permisos del token: `contents: read`.
