package com.parqueadero.backend.entity;

/** Motivo de un evento de acceso denegado o especial (ADR 0004). */
public enum MotivoAcceso {
    PLACA_DESCONOCIDA, USUARIO_VENCIDO, USUARIO_SUSPENDIDO, USUARIO_DE_BAJA, SIN_CUPO,
    TIPO_CUPO_DISTINTO, FORZADO_ADMIN, SALIDA_CON_DEUDA, YA_DENTRO
}
