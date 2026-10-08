package com.parqueadero.backend.analitica;

import java.time.LocalDate;

/** Analítica de solo lectura. Los rangos de fechas son inclusivos, en hora de Bogotá, de máximo 400 días. */
public interface AnaliticaService {

    int MAX_DIAS = 400;

    AnaliticaDTO.Resumen resumen(boolean incluirSimulados);

    AnaliticaDTO.SerieOcupacion ocupacion(LocalDate desde, LocalDate hasta, String granularidad, boolean incluirSimulados);

    AnaliticaDTO.MapaCalor mapaCalor(LocalDate desde, LocalDate hasta, boolean incluirSimulados);

    AnaliticaDTO.IngresosAnio ingresos(Integer anio, boolean incluirSimulados);

    AnaliticaDTO.Morosidad morosidad(boolean incluirSimulados);

    AnaliticaDTO.Permanencia permanencia(LocalDate desde, LocalDate hasta, boolean incluirSimulados);

    AnaliticaDTO.ResumenUsuario resumenUsuario(long usuarioId);
}
