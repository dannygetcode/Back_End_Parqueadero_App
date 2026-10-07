# Backend - Parqueadero

API REST (Spring Boot 3.5, Java 17) + servicio OCR (Flask/Tesseract) + PostgreSQL 15.

## Configuracion
Todo se configura por variables de entorno (ver `.env.example`). Los secretos no van en git.

1. `cp .env.example .env` y reemplaza cada `change-me` (JWT_SECRET: minimo 32 caracteres aleatorios).
2. `.env` esta en `.gitignore`; nunca lo subas.

Variables: `POSTGRES_DB`, `DB_USER`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `JWT_SECRET`,
`SPRING_SECURITY_PASSWORD`, `SERVER_PORT`; opcionales `DB_URL`, `OCR_SERVICE_URL`, `UPLOAD_DIR`,
`SERVER_ADDRESS`, `TESSERACT_CMD` (solo OCR fuera de Docker, p. ej. Windows).

## Levantar (Docker Compose)
```
docker compose up -d --build
```
Servicios: `db` (Postgres, volumen `pgdata`, healthcheck), `ocr` (solo red interna), `backend` (puerto `SERVER_PORT`, espera a `db` healthy).
La BD no se publica al host; para inspeccionarla anade temporalmente `ports: ["127.0.0.1:5432:5432"]` al servicio `db`.

## Verificar
- `docker compose ps` (db healthy, backend y ocr Up)
- `docker compose logs backend | grep Started`
- `curl -i http://localhost:8080/api/camaras`

## Revertir
`docker compose down` (conserva datos) o `docker compose down -v` (borra BD y uploads del contenedor).

## Desarrollo sin Docker
`docker compose up -d db` requiere publicar el puerto (ver arriba); exporta las variables de `.env` (incluidas `DB_URL` y `OCR_SERVICE_URL`) y ejecuta `./mvnw spring-boot:run`.

## CI
`.github/workflows/ci.yml`: en cada push y pull request corre `./mvnw -B verify` con Java 17 y un Postgres de servicio con valores dummy (necesario para `contextLoads`).
