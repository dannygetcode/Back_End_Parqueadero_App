-- N6 (ADR 0003): revocación de tokens. Un JWT emitido antes de credenciales_cambiadas_en deja de ser válido.
-- Usuario: se actualiza al cambiar el PIN, activar con un código, regenerar el código, dar de baja y suspender.
-- Administrador: todavía no hay flujo de cambio de contraseña; se puede fijar a mano para cerrar sus sesiones.
ALTER TABLE usuario ADD COLUMN credenciales_cambiadas_en timestamptz;
ALTER TABLE administrador ADD COLUMN credenciales_cambiadas_en timestamptz;

COMMENT ON COLUMN usuario.credenciales_cambiadas_en IS
    'Los JWT emitidos antes se rechazan (cambio de PIN, activacion, codigo nuevo, baja, suspension)';
COMMENT ON COLUMN administrador.credenciales_cambiadas_en IS
    'Los JWT emitidos antes se rechazan';
