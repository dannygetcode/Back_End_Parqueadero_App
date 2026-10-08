package com.parqueadero.backend.integracion;

import com.parqueadero.backend.config.AppConfig;
import com.parqueadero.backend.entity.TipoVehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-A05 / RF-22 / RF-23 / RF-27 de extremo a extremo: la tarifa aplicada es la vigente en periodo_inicio según el
 * tipo del cupo, y el periodo que empieza a fin de mes no se acorta (31-ene -> 28-feb).
 */
class TarifaPeriodoIT extends PruebaIntegracion {

    private void fijarDia(String dia) {
        reloj.fijar(LocalDate.parse(dia).atTime(LocalTime.NOON).atZone(AppConfig.ZONA).toInstant());
    }

    private void proximo(UsuarioPrueba u, String inicio, String fin, int monto) throws Exception {
        mvc.perform(get("/api/pagos/proximo-periodo").header("Authorization", bearerUsuario(u.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value(inicio))
                .andExpect(jsonPath("$.fin").value(fin))
                .andExpect(jsonPath("$.montoEsperado").value(monto));
    }

    @Test
    void tarifaSemillaSegunAnioYTipo() throws Exception {
        UsuarioPrueba moto = crearUsuario(TipoVehiculo.MOTO);
        UsuarioPrueba carro = crearUsuario(TipoVehiculo.CARRO);

        fijarDia("2025-06-01");
        proximo(moto, "2025-06-01", "2025-06-30", 8000);
        proximo(carro, "2025-06-01", "2025-06-30", 64000);

        // Último día de la tarifa 2025 y primer día de la 2026.
        fijarDia("2025-12-31");
        proximo(carro, "2025-12-31", "2026-01-30", 64000);
        fijarDia("2026-01-01");
        proximo(carro, "2026-01-01", "2026-01-31", 80000);

        fijarDia("2026-06-01");
        proximo(moto, "2026-06-01", "2026-06-30", 10000);
        proximo(carro, "2026-06-01", "2026-06-30", 80000);
    }

    @Test
    void periodoQueEmpiezaEl31DeEneroTerminaElUltimoDiaDeFebrero() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        fijarDia("2026-01-31");
        proximo(u, "2026-01-31", "2026-02-28", 80000);

        // Cortesía aprobada ese día y el siguiente periodo continúa sin hueco el 1-mar.
        cortesia(u);
        proximo(u, "2026-03-01", "2026-03-31", 80000);

        UsuarioPrueba bisiesto = crearUsuario(TipoVehiculo.MOTO);
        fijarDia("2028-01-31");
        proximo(bisiesto, "2028-01-31", "2028-02-29", 10000);
    }

    @Test
    void altaDeTarifaValidaFechaYDuplicadoYSeAplicaDesdeSuVigencia() throws Exception {
        String admin = bearerAdmin();
        String ayer = LocalDate.now(reloj).minusDays(1).toString();
        mvc.perform(post("/api/tarifas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipoVehiculo", "CARRO", "valorMensual", 100000, "vigenteDesde", ayer))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tarifas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipoVehiculo", "CARRO", "valorMensual", 0, "vigenteDesde", "2031-01-01"))))
                .andExpect(status().isBadRequest());

        // Fecha lejana para no interferir con las demás pruebas, que usan el reloj real.
        mvc.perform(post("/api/tarifas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipoVehiculo", "CARRO", "valorMensual", 100000, "vigenteDesde", "2031-01-01"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorMensual").value(100000));
        mvc.perform(post("/api/tarifas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipoVehiculo", "CARRO", "valorMensual", 120000, "vigenteDesde", "2031-01-01"))))
                .andExpect(status().isConflict());

        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        fijarDia("2030-12-20");
        proximo(u, "2030-12-20", "2031-01-19", 80000);
        fijarDia("2031-01-15");
        proximo(u, "2031-01-15", "2031-02-14", 100000);
    }
}
