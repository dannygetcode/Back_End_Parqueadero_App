# Backend — Parqueadero

API REST de un sistema de parqueadero: usuarios con placa, pagos con comprobante (OCR), cámaras IP y control de la puerta. Lo consumen la app móvil (`mobile`) y el panel web (`frontend`).

## Stack
- Spring Boot 3.5.0, Java 17, Maven (usa siempre `./mvnw`, no un Maven global)
- PostgreSQL 15 (docker-compose), Spring Data JPA, Spring Security + JWT (jjwt 0.11.5), Lombok
- Microservicio OCR aparte en Python/Flask: `src/main/java/com/ocr-service/` (pytesseract)

## Comandos
- Base de datos: `docker compose up -d db`
- Correr: `./mvnw spring-boot:run` (puerto 8080)
- Tests: `./mvnw verify` (unitarios + integración con Testcontainers; necesita Docker)
- OCR: `pip install -r src/main/java/com/ocr-service/requirements.txt` y `python main.py` desde esa carpeta (necesita Tesseract instalado)

## Arquitectura
Paquete base `com.parqueadero.backend`, en capas: `controller` → `service` (interfaz + `Impl`) → `repository` → `entity`. Los `dto` separan lo que sale/entra por la API. `config/` tiene seguridad (`SecurityConfig`, `JwtAuthenticationFilter`), CORS y `OcrProperties`.

Dominio (nombres en español, mantenlos): `Usuario` (teléfono, placa, estado `EstadoUsuario`, PIN, código de validación), `Payment` (userId, fechas de servicio, `ocrData`, placa, imagen, monto), `Camara` (nombre, url, activa), `Puerta` (una sola fila, id=1, `abierta`).

## API (prefijo `/api`, Fase 1)
Detalle y DTOs: `docs/arquitectura/fase-1-modelo.md` §5; cambios para los consumidores: `docs/cambios-de-contrato-fase-1.md`.
Roles: P público, A ADMIN (JWT), U USUARIO (JWT, solo lo suyo), S SISTEMA (cabecera `X-Api-Key`).
- P: `POST admin/login`, `POST auth/activar`, `POST auth/login`, `GET ping`, `GET legal/aviso-privacidad`
- `usuarios` — A: listar, detalle, alta (devuelve el código de validación una vez), editar, `{id}/vehiculo`, `{id}/estado` (SUSPENDER/REACTIVAR), `{id}/codigo`, baja lógica; U: `yo`, `yo/pin`
- `cupos` (A), `tarifas` (GET A/U, POST A)
- `pagos` — U: `POST` (multipart `comprobante`), `mios`, `proximo-periodo`; A: listado paginado, `manual` (solo cortesía 0), `{id}/aprobar`, `{id}/rechazar`; A o dueño: `{id}`, `{id}/comprobante`
- `accesos` — S/A: `POST lecturas` (simulador de cámara); A: listado, `ocupacion`; U: `mios`
- `puerta` — A/U: GET estado, PUT interruptor (las aperturas generan `evento_acceso`)
- `camaras` — GET A/U; POST/PUT A
- Esquema solo por Flyway (`src/main/resources/db/migration`); entidades validadas con `ddl-auto=validate`.

## Reglas para trabajar aquí
- Sigue el patrón existente: interfaz de servicio + `Impl`; no expongas entidades JPA directamente, usa DTOs.
- Antes de cambiar un endpoint o un DTO, busca quién lo consume: `mobile/app/src/main/java/com/parqueadero/appparqueadero/data/network/ApiService.kt` y `frontend/js/*.js`. Un cambio aquí rompe esos dos.
- No escribas credenciales, tokens ni IPs reales en código, commits ni en este archivo.
- No cambies `spring.jpa.hibernate.ddl-auto` ni toques `uploads/` sin avisar.

## Deuda conocida (no la "arregles" sin que se pida, pero tenla presente)
- Resuelto en Fase 1: credenciales fuera del código (todo por variables de entorno; admin en tabla `administrador` con BCrypt), autorización por rol en todas las rutas, `server.address` por defecto `127.0.0.1`, `show-sql=false`, tests de integración con Testcontainers, `uploads/` fuera de git.
- CSRF desactivado a propósito (API stateless con Bearer y sin cookies); la protección irá en el BFF de Next.js.
- `mobile/` y `frontend/` todavía usan el contrato viejo y están rotos contra este backend hasta su fase (ver `docs/cambios-de-contrato-fase-1.md`).
- Sin revocación de JWT (mitigado: las operaciones sensibles releen el usuario) ni refresh tokens. Bloqueo por cuenta, no por IP.
- El job de vencimiento y el anti-rebote asumen una sola instancia (con réplicas haría falta ShedLock).
- Los comprobantes están en disco local (volumen `uploads`): hay que respaldarlo junto con el `pg_dump`.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**This project has a knowledge graph. Start with the code-review-graph
MCP tools to narrow scope, then read the source.** The graph is cheaper than scanning files and
gives you structural context (callers, dependents, test coverage) that file search cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes_tool` or `query_graph_tool` instead of Grep
- **Understanding impact**: `get_impact_radius_tool` instead of manually tracing imports
- **Code review**: `detect_changes_tool` + `get_review_context_tool` instead of reading entire files
- **Finding relationships**: `query_graph_tool` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview_tool` + `list_communities_tool`

### Verify in the source

- Narrow scope with the graph, then read the source. Do not change code from graph output alone.
- For any non-trivial change, read the implementation and the relevant tests before concluding.
- Verify the exact source when touching behavior, database logic, migrations, retries, fallbacks,
  recovery, or compatibility code.
- When the graph and the source disagree, the source wins. The graph may be stale or may not
  model that relationship.
- An empty graph result can mean "not indexed" or "not statically visible", not "does not exist".

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes_tool` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context_tool` | Need source snippets for review — token-efficient |
| `get_impact_radius_tool` | Understanding blast radius of a change |
| `get_affected_flows_tool` | Finding which execution paths are impacted |
| `query_graph_tool` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes_tool` | Finding functions/classes by name or keyword |
| `get_architecture_overview_tool` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes_tool` for code review.
3. Use `get_affected_flows_tool` to understand impact.
4. Use `query_graph_tool` pattern="tests_for" to check coverage.
<!-- /code-review-graph MCP tools -->
