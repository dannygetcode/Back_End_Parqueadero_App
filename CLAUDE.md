# Backend — Parqueadero

API REST de un sistema de parqueadero: usuarios con placa, pagos con comprobante (OCR), cámaras IP y control de la puerta. Lo consumen la app móvil (`mobile`) y el panel web (`frontend`).

## Stack
- Spring Boot 3.5.0, Java 17, Maven (usa siempre `./mvnw`, no un Maven global)
- PostgreSQL 15 (docker-compose), Spring Data JPA, Spring Security + JWT (jjwt 0.11.5), Lombok
- Microservicio OCR aparte en Python/Flask: `src/main/java/com/ocr-service/` (pytesseract)

## Comandos
- Base de datos: `docker compose up -d db`
- Correr: `./mvnw spring-boot:run` (puerto 8080)
- Tests: `./mvnw test` (hoy solo existe `contextLoads`)
- OCR: `pip install -r src/main/java/com/ocr-service/requirements.txt` y `python main.py` desde esa carpeta (necesita Tesseract instalado)

## Arquitectura
Paquete base `com.parqueadero.backend`, en capas: `controller` → `service` (interfaz + `Impl`) → `repository` → `entity`. Los `dto` separan lo que sale/entra por la API. `config/` tiene seguridad (`SecurityConfig`, `JwtAuthenticationFilter`), CORS y `OcrProperties`.

Dominio (nombres en español, mantenlos): `Usuario` (teléfono, placa, estado `EstadoUsuario`, PIN, código de validación), `Payment` (userId, fechas de servicio, `ocrData`, placa, imagen, monto), `Camara` (nombre, url, activa), `Puerta` (una sola fila, id=1, `abierta`).

## API (prefijo `/api`)
- `admin/login` — login del administrador, devuelve JWT
- `usuarios` — `registro`, `solicitar-validacion`, `verificar-codigo`, `validar`, `login`, `yo`, CRUD y `{id}/estado`
- `pagos` — CRUD; el alta recibe la imagen del comprobante y llama al servicio OCR (`ocr.service.url`)
- `puerta` — GET/PUT del estado
- `camaras` — GET/POST/PUT

## Reglas para trabajar aquí
- Sigue el patrón existente: interfaz de servicio + `Impl`; no expongas entidades JPA directamente, usa DTOs.
- Antes de cambiar un endpoint o un DTO, busca quién lo consume: `mobile/app/src/main/java/com/parqueadero/appparqueadero/data/network/ApiService.kt` y `frontend/js/*.js`. Un cambio aquí rompe esos dos.
- No escribas credenciales, tokens ni IPs reales en código, commits ni en este archivo.
- No cambies `spring.jpa.hibernate.ddl-auto` ni toques `uploads/` sin avisar.

## Deuda conocida (no la "arregles" sin que se pida, pero tenla presente)
- Credenciales del admin y de la base de datos en texto plano en `application.properties`, versionado en git.
- `SecurityConfig` deja GET/POST/PUT de `/api/camaras/**` sin autenticación; CSRF desactivado.
- `server.address` expuesto a toda la red, `show-sql` activo.
- Sin tests reales. El servicio OCR tiene la ruta de Tesseract de Windows fija en el código.

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
