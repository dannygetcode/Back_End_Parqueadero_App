package com.parqueadero.backend.analitica;

/** Proxy hacia el servicio de pronóstico. Devuelve el JSON del servicio tal cual; si falla, 503. */
public interface PronosticoService {

    String vacancia(boolean incluirSimulados);

    /** @param horizonteDias de 1 a 14. */
    String ocupacion(int horizonteDias, boolean incluirSimulados);

    String llegadas(boolean incluirSimulados);
}
