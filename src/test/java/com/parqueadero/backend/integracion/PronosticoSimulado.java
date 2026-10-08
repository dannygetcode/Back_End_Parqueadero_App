package com.parqueadero.backend.integracion;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Servidor HTTP mínimo (JDK) que hace de servicio de pronóstico en las pruebas. Escucha solo en loopback. */
class PronosticoSimulado {

    enum Modo { OK, ERROR_500, CORTAR_CONEXION }

    static final String CUERPO = "{\"versionModelo\":\"prueba\",\"porTipo\":{\"CARRO\":{\"probabilidades\":[]}}}";

    private final HttpServer servidor;
    private volatile Modo modo = Modo.OK;
    private final AtomicReference<String> ultimaConsulta = new AtomicReference<>();

    PronosticoSimulado() {
        try {
            servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", ex -> {
            ultimaConsulta.set(ex.getRequestURI().getPath() + "?" + ex.getRequestURI().getRawQuery());
            switch (modo) {
                case CORTAR_CONEXION -> ex.close();
                case ERROR_500 -> {
                    byte[] b = "detalle interno secreto".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(500, b.length);
                    ex.getResponseBody().write(b);
                    ex.close();
                }
                default -> {
                    byte[] b = CUERPO.getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().add("Content-Type", "application/json");
                    ex.sendResponseHeaders(200, b.length);
                    ex.getResponseBody().write(b);
                    ex.close();
                }
            }
        });
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    void modo(Modo m) {
        this.modo = m;
    }

    String ultimaConsulta() {
        return ultimaConsulta.get();
    }
}
