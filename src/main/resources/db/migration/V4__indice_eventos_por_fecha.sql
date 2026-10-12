-- V4: la analítica (Fase 4) consulta evento_acceso por rango de fechas sin filtrar por vehículo ni usuario.
CREATE INDEX ix_evento_ocurrido ON evento_acceso (ocurrido_en);
