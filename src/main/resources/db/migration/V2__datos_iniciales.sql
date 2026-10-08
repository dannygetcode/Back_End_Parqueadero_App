-- V2__datos_iniciales.sql — Datos de referencia (no datos simulados: esos los carga el generador de la Fase 2).
-- El administrador NO se siembra aquí: lo crea la aplicación al arrancar desde ADMIN_USERNAME/ADMIN_PASSWORD.

INSERT INTO parqueadero (id, nombre) VALUES (1, 'Parqueadero principal');

INSERT INTO cupo (parqueadero_id, codigo, tipo_vehiculo) VALUES
    (1, 'C1', 'CARRO'), (1, 'C2', 'CARRO'), (1, 'C3', 'CARRO'),
    (1, 'C4', 'CARRO'), (1, 'C5', 'CARRO'), (1, 'C6', 'CARRO'),
    (1, 'M1', 'MOTO');

-- Tarifas mensuales en COP. La de 2025 cubre los 12 meses de datos simulados de la Fase 2; la de 2026 es la
-- vigente (~25 % de aumento anual). Valores confirmados por el dueño.
INSERT INTO tarifa (parqueadero_id, tipo_vehiculo, valor_mensual, vigente_desde) VALUES
    (1, 'CARRO', 64000, DATE '2025-01-01'),
    (1, 'CARRO', 80000, DATE '2026-01-01'),
    (1, 'MOTO',   8000, DATE '2025-01-01'),
    (1, 'MOTO',  10000, DATE '2026-01-01');

INSERT INTO puerta (parqueadero_id, nombre) VALUES (1, 'Principal');
INSERT INTO camara (parqueadero_id, nombre, url, activa, simulada) VALUES (1, 'Entrada (simulada)', NULL, true, true);

-- Alinear la secuencia de identidad tras insertar el id explícito.
SELECT setval(pg_get_serial_sequence('parqueadero', 'id'), (SELECT max(id) FROM parqueadero));
