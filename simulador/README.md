# Simulador de datos del parqueadero (Fase 2)

Genera 12 meses de datos **ficticios** (usuarios, vehiculos, pagos y accesos) directo en PostgreSQL, todos con
`simulado=true`. Sirven para desarrollar la analitica y el pronostico. Los listados operativos del backend excluyen
`simulado=true`; nunca se mezclan con datos reales (usuarios, placas y telefonos son inventados; telefonos
`3000000001+`, placas `ABC123` / `ABC12D`).

## Escenario

7 cupos (6 carros + 1 moto), ~10 usuarios en el ano: ~3 bajas con reemplazo, 1 usuario vencido y 2 con perfil de
pago tardio (1-10 dias de atraso). Pagos mensuales continuos con la tarifa vigente, algunos rechazados y reenviados.
Perfiles de uso: **oficina** (entra 7-8:30, sale 17-19 h, L-V), **nocturno** (sale temprano, vuelve tarde) y
**mixto** (visitas cortas ocasionales). Incluye vacaciones, festivos, salidas con deuda y accesos DENEGADOS.
Con la misma `--semilla` el resultado es identico.

## Como correrlo

La BD del compose no se publica. Publicala temporalmente en el loopback con el override incluido:

```bash
docker compose -f docker-compose.yml -f simulador/compose.publicar-bd.yml up -d db
pip install -r simulador/requirements.txt
python simulador/generar.py --limpiar          # --meses 12 --semilla 2025 --hasta AAAA-MM-DD
python simulador/verificar.py                  # resumen sin datos personales; exit 1 si falla un chequeo
docker compose up -d db                        # al terminar: vuelve a no publicar la BD
```

Credenciales: variables `POSTGRES_DB`, `DB_USER`, `DB_PASSWORD` (y opcional `DB_HOST`/`DB_PORT`, por defecto
`localhost:5432`); se leen del entorno o del `.env` del repo y nunca se imprimen.

`--limpiar` borra **solo** filas `simulado=true` y las regenera en una transaccion; sin el, falla si ya hay
simulados. No toca filas reales. Tests (sin Docker): `cd simulador && python -m pytest`.
