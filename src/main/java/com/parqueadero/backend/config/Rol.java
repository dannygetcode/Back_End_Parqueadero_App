package com.parqueadero.backend.config;

/** Roles de la API (ADR 0003). En Spring Security se exponen como ROLE_&lt;rol&gt;. */
public enum Rol {
    ADMIN, USUARIO, SISTEMA;

    public String autoridad() {
        return "ROLE_" + name();
    }
}
