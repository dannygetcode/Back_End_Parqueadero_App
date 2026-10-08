package com.parqueadero.backend.service;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Almacenamiento de comprobantes (ADR 0005). Hoy en disco local (volumen uploads); la interfaz permite cambiar a
 * object storage sin tocar PagoService.
 */
public interface AlmacenComprobantes {

    /** Guarda el archivo con nombre UUID y devuelve la ruta relativa (comprobantes/aaaa/mm/uuid.ext). */
    String guardar(byte[] contenido, String extension, LocalDate fecha);

    /** Ruta absoluta del archivo, o null si no existe o la ruta sale del directorio base. */
    Path abrir(String rutaRelativa);

    void eliminar(String rutaRelativa);
}
