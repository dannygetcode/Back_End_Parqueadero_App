# ADR 0005 — Almacenamiento y entrega de comprobantes de pago

- Estado: Aceptado
- Fecha: 2026-10-07

## Contexto

Situación actual (`PaymentServiceImpl.create`, `WebConfig`, `SecurityConfig`):

- El archivo se guarda como `System.currentTimeMillis() + "_" + nombreOriginal` en `app.upload.dir`. El nombre
  original lo controla el cliente, lo que permite *path traversal* y nombres raros.
- `WebConfig` sirve `file:uploads/` (ruta relativa al directorio de arranque, no `UPLOAD_DIR`) en `/uploads/**`, y
  `SecurityConfig` lo deja **público**: cualquiera que adivine el nombre ve el comprobante, que es un dato personal
  y financiero.
- Si el OCR falla, se lanza una excepción y el usuario no puede registrar el pago.
- No hay límite de tamaño ni validación del tipo de archivo.

Volumen: 7 usuarios, un comprobante al mes cada uno, de unos 100 KB a 2 MB. Menos de 200 MB al año.

## Decisión

1. **Almacenamiento en disco local** dentro del volumen Docker `uploads` (ya declarado en `docker-compose.yml`),
   con ruta absoluta en `UPLOAD_DIR`, fuera del repositorio. Estructura: `{UPLOAD_DIR}/comprobantes/{aaaa}/{mm}/{uuid}.{ext}`.
   En BD se guarda la **ruta relativa** (`comprobante_ruta`), nunca una URL.
2. **Interfaz `AlmacenComprobantes`** (`guardar`, `abrir`) con una sola implementación,
   `AlmacenComprobantesLocal`, que sigue el patrón interfaz + Impl del proyecto. Si se despliega en un PaaS con
   disco efímero, se agrega una implementación S3/R2 sin tocar `PagoService`.
3. **Validación al subir**: como máximo 5 MB (`spring.servlet.multipart.max-file-size=5MB`); el tipo se detecta por
   los primeros bytes del archivo (JPEG/PNG; el OCR actual usa `PIL.Image` y no lee PDF), no por la extensión
   ni el `Content-Type` del cliente; nombre generado con UUID; se calcula `comprobante_sha256`.
4. **Duplicados**: si ya existe un pago no rechazado del mismo usuario con el mismo SHA-256, se responde 409.
   Entre usuarios distintos no se bloquea, pero se marca `posible_duplicado` para que lo revise el admin.
5. **OCR no bloqueante para el negocio**: se llama de forma síncrona (timeout de 10 s en `RestTemplate`). Si falla
   o no extrae nada, el pago se crea igual en `PENDIENTE` con `monto_ocr` y `fecha_pago_ocr` en null, y
   `ocr_estado = FALLIDO`. El admin aprueba mirando la imagen. El texto extraído se guarda en `ocr_datos jsonb`.
6. **Entrega autenticada**: se elimina el *resource handler* público `/uploads/**` y su `permitAll`. Nuevo
   `GET /api/pagos/{id}/comprobante` que transmite el archivo con su `Content-Type` y
   `Cache-Control: private, no-store`. Lo puede ver el ADMIN o el USUARIO dueño del pago.
   - El panel ya no puede usar `<img src=...>` directo (no envía la cabecera `Authorization`): hace `fetch` con el
     token, crea un `Blob` y usa `URL.createObjectURL` (`frontend/js/pagos.js`, línea ~270, que además tiene
     `http://localhost:8080` fijo).
7. **Retención**: los comprobantes no se borran con la baja lógica del usuario (soporte contable). Se documenta
   en la política de tratamiento de datos. Los backups del volumen `uploads` van junto con el `pg_dump`.

## Alternativas consideradas

- **Guardar la imagen en PostgreSQL (`bytea`)**: backups atómicos con la BD y sin volumen aparte. Con menos de
  200 MB al año sería viable, pero hincha los dumps y las consultas. Se descarta por poco: el disco local con la
  interfaz es igual de simple y escala mejor.
- **Object storage (S3, Cloudflare R2, Supabase Storage) desde ya**: necesario solo si el hosting tiene disco
  efímero. Se decide con el ADR de despliegue; la interfaz deja la puerta abierta.
- **URLs firmadas temporales**: útil con CDN y archivos grandes. Innecesario aquí.
- **OCR asíncrono con cola**: el OCR tarda 1 a 3 s y hay 7 pagos al mes. Descartado.

## Consecuencias

- Rompe `PaymentDTO.imageUrl`: pasa a ser `comprobanteUrl = "/api/pagos/{id}/comprobante"` (necesita token).
  Consumidores: `frontend/js/pagos.js`. La app móvil solo sube; no muestra el comprobante hoy.
- El volumen `uploads` pasa a ser estado crítico: hay que incluirlo en los backups.
- `WebConfig` queda vacío o se elimina.
