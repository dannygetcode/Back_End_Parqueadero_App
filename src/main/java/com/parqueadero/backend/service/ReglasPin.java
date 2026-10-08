package com.parqueadero.backend.service;

import java.util.Set;

/** PIN de 6 dígitos (ADR 0003). El formato lo valida Bean Validation; aquí se rechazan los triviales. */
public final class ReglasPin {

    private static final Set<String> TRIVIALES = Set.of("123456", "654321", "012345", "543210", "123123");

    private ReglasPin() {
    }

    public static boolean esTrivial(String pin) {
        if (pin == null || pin.isEmpty()) {
            return true;
        }
        boolean todosIguales = pin.chars().allMatch(c -> c == pin.charAt(0));
        return todosIguales || TRIVIALES.contains(pin);
    }
}
