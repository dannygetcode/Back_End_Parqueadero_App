package com.parqueadero.backend.analitica;

import com.parqueadero.backend.config.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Endpoints de solo lectura. /api/analitica/** es del ADMIN y /api/usuarios/yo/resumen del USUARIO (SecurityConfig). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnaliticaController {

    private final AnaliticaService service;

    @GetMapping("/analitica/resumen")
    public AnaliticaDTO.Resumen resumen(@RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return service.resumen(incluirSimulados);
    }

    @GetMapping("/analitica/ocupacion")
    public AnaliticaDTO.SerieOcupacion ocupacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "hora") String granularidad,
            @RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return service.ocupacion(desde, hasta, granularidad, incluirSimulados);
    }

    @GetMapping("/analitica/ocupacion/mapa-calor")
    public AnaliticaDTO.MapaCalor mapaCalor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return service.mapaCalor(desde, hasta, incluirSimulados);
    }

    @GetMapping("/analitica/ingresos")
    public AnaliticaDTO.IngresosAnio ingresos(@RequestParam(required = false) Integer anio,
                                              @RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return service.ingresos(anio, incluirSimulados);
    }

    @GetMapping("/analitica/morosidad")
    public AnaliticaDTO.Morosidad morosidad(@RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return service.morosidad(incluirSimulados);
    }

    @GetMapping("/analitica/permanencia")
    public AnaliticaDTO.Permanencia permanencia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return service.permanencia(desde, hasta, incluirSimulados);
    }

    @GetMapping("/usuarios/yo/resumen")
    public AnaliticaDTO.ResumenUsuario resumenUsuario(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.resumenUsuario(yo.id());
    }
}
