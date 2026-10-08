package com.parqueadero.backend.service;

import com.parqueadero.backend.entity.TipoVehiculo;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalización y formato de placas colombianas (ADR 0002, RF-12). */
public final class Placas {

    private static final Pattern CARRO = Pattern.compile("^[A-Z]{3}\\d{3}$");
    private static final Pattern MOTO = Pattern.compile("^[A-Z]{3}\\d{2}[A-Z]?$");
    private static final Pattern SEPARADORES = Pattern.compile("[\\s\\-.]");

    private Placas() {
    }

    /** Mayúsculas, sin espacios, guiones ni puntos. Nunca devuelve null. */
    public static String normalizar(String placa) {
        if (placa == null) {
            return "";
        }
        return SEPARADORES.matcher(placa).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public static boolean formatoValido(String placaNormalizada, TipoVehiculo tipo) {
        return switch (tipo) {
            case CARRO -> CARRO.matcher(placaNormalizada).matches();
            case MOTO -> MOTO.matcher(placaNormalizada).matches();
        };
    }
}
