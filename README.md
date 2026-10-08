# Backend - Parqueadero

API REST (Spring Boot 3.5, Java 17) + servicio OCR (Flask/Tesseract) + PostgreSQL 15.
Diseño de la Fase 1: `docs/arquitectura/fase-1-modelo.md` y ADR en `docs/adr/`. Cambios para la app y el panel:
`docs/cambios-de-contrato-fase-1.md`.

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
Servicios: `db` (Postgres, healthcheck), `ocr` (solo red interna), `backend` (puerto `SERVER_PORT`, healthcheck en
`/api/ping`). Al arrancar, Flyway aplica las migraciones de `src/main/resources/db/migration` (V1 esquema, V2 datos
de referencia: 7 cupos, tarifas, puerta y camara simulada) y se crea el administrador.
La BD no se publica al host; para inspeccionarla anade temporalmente `ports: ["127.0.0.1:5432:5432"]` al servicio `db`.

## Verificar
- `docker compose ps` (los tres servicios Up; db y backend healthy)
- `docker compose logs backend | grep -E "Successfully applied|Started"`
- `curl http://localhost:8080/api/ping`
- Login del admin: `curl -X POST localhost:8080/api/admin/login -H "Content-Type: application/json" -d '{"username":"...","password":"..."}'`
- Simulador de camara: `curl -X POST localhost:8080/api/accesos/lecturas -H "X-Api-Key: $CAMARA_API_KEY" -H "Content-Type: application/json" -d '{"placa":"ABC123"}'`

## Reset de la base de datos (cambio de esquema incompatible)
El esquema lo gestiona solo Flyway (`ddl-auto=validate`); las migraciones aplicadas no se editan. Si hay que empezar
de cero (como en la Fase 1, ADR 0001):
1. Respaldo: `docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > ../backups/<fecha>.sql`
   y copia del volumen `uploads` (comprobantes).
2. `docker compose stop backend`
3. `docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'`
   (o borrar y recrear el volumen: `docker compose down`, `docker volume rm backend_pgdata`, `docker volume create backend_pgdata`)
4. `docker compose up -d`: Flyway vuelve a aplicar V1 y V2.

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
  (inferencia, anti-rebote, entrada repetida, salida siempre permitida) y vencimiento.
- Los tests usan sus propios valores de prueba; no leen `.env`.

## CI
`.github/workflows/ci.yml`: en cada push y pull request corre `./mvnw -B verify` con Java 17 (Testcontainers usa el
Docker del runner). Permisos del token: `contents: read`.
