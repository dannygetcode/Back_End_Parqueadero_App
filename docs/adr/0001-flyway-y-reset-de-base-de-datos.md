# ADR 0001 — Flyway y reset de la base de datos

- Estado: Aceptado (decisión del dueño, Fase 1)
- Fecha: 2026-10-07

## Contexto

El esquema actual lo genera Hibernate con `spring.jpa.hibernate.ddl-auto=update`. Eso no deja historial, no crea
CHECKs ni índices parciales, no borra columnas obsoletas y hace que el esquema real dependa del orden en que se
arrancó cada versión. La Fase 1 cambia casi todo el modelo (vehículo, cupo, tarifa, evento de acceso, pago con
estados), y los datos actuales son de prueba. Más adelante habrá un segundo escritor (servicio Python de
pronóstico) que necesita un esquema estable y conocido.

## Decisión

1. Agregar Flyway (`org.flywaydb:flyway-core` y `org.flywaydb:flyway-database-postgresql`, versiones gestionadas por
   el parent de Spring Boot 3.5; desde Flyway 10 el soporte de PostgreSQL va en ese módulo aparte).
2. **Reset sin baseline**: no se migra el esquema viejo. `V1__esquema_inicial.sql` crea el modelo nuevo completo
   (DDL en `docs/arquitectura/fase-1-modelo.md`). `V2__datos_iniciales.sql` inserta solo datos de referencia:
   el parqueadero id=1, los 7 cupos, las tarifas iniciales, la puerta y la cámara simulada.
3. `spring.jpa.hibernate.ddl-auto=validate`: Hibernate solo comprueba que las entidades coinciden con el esquema;
   el único dueño del esquema es Flyway. `spring.flyway.clean-disabled=true` (valor por defecto) para que nunca
   se pueda borrar la BD desde la aplicación.
4. **Un solo dueño del esquema**: las tablas que escriba el servicio Python en fases siguientes también se crean con
   migraciones Flyway en este repo. Python no ejecuta DDL.
5. Reset del entorno de desarrollo (una sola vez, manual y documentado en el README): parar compose, borrar y
   recrear el volumen externo `backend_pgdata` y arrancar; Flyway aplica V1 y V2. En lugar de borrar el volumen
   también vale `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` desde `psql`.
6. `uploads/` sale del repositorio: `UPLOAD_DIR` apunta a una ruta absoluta fuera del repo (en Docker, el volumen
   `uploads` que ya existe), y `uploads/` va en `.gitignore`. Ver ADR 0005.
7. Las migraciones aplicadas no se editan nunca; cada cambio es un `V{n}__descripcion.sql` nuevo.
8. Los datos simulados (Fase 2) **no** van en migraciones: los carga el generador Python con `simulado = true`.

## Alternativas consideradas

- **Seguir con `ddl-auto=update`**: cero trabajo hoy, pero el esquema no es reproducible y no expresa
  restricciones. Descartada.
- **Flyway con `baselineOnMigrate` sobre el esquema viejo**: conserva datos que no valen nada (son de prueba) a
  cambio de escribir migraciones de transformación complejas. Descartada.
- **Liquibase**: equivalente en capacidad, pero con XML/YAML y más ceremonia. SQL plano en Flyway se lee mejor y
  sirve de documentación del esquema. Descartada.

## Consecuencias

- Se pierden los datos actuales de la BD de desarrollo (aceptado). El volumen `backend_pgdata` sigue siendo
  externo, así que el reset es un paso consciente, no accidental.
- Cambiar una entidad JPA sin migración hace fallar el arranque (es lo que queremos: falla temprano).
- Los tests de integración deberían correr contra PostgreSQL real (Testcontainers, Docker ya está en el runner
  de GitHub Actions) para validar migraciones; H2 no entiende los índices parciales ni algunos CHECK.
  Mínimo: un test que levanta el contexto con Flyway + `validate`.
- Los nombres de tablas y columnas pasan a `snake_case` en español; las entidades usan `@Table`/`@Column`
  explícitos cuando el nombre no coincida con la estrategia por defecto de Spring.
