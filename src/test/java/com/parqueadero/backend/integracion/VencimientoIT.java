package com.parqueadero.backend.integracion;

import com.parqueadero.backend.config.AppConfig;
import com.parqueadero.backend.entity.TipoVehiculo;
import com.parqueadero.backend.service.VencimientoJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** ADR 0006: el job pasa a VENCIDO cuando ya no hay pago aprobado con periodo_fin + 5 días de gracia >= hoy. */
class VencimientoIT extends PruebaIntegracion {

    @Autowired
    VencimientoJob job;

    @Test
    void venceDespuesDeLaGraciaYNoAntes() throws Exception {
        UsuarioPrueba u = crearUsuario(TipoVehiculo.CARRO);
        cortesia(u);
        LocalDate fin = LocalDate.parse(leer(mvc.perform(get("/api/usuarios/" + u.id())
                .header("Authorization", bearerAdmin())).andReturn()).get("vigenteHasta").asText());

        // Último día de gracia: sigue ACTIVO.
        reloj.fijar(fin.plusDays(5).atTime(12, 0).atZone(AppConfig.ZONA).toInstant());
        job.ejecutar();
        estado(u, "ACTIVO");

        // Un día después: VENCIDO.
        reloj.fijar(fin.plusDays(6).atTime(0, 5).atZone(AppConfig.ZONA).toInstant());
        job.ejecutar();
        estado(u, "VENCIDO");
    }

    private void estado(UsuarioPrueba u, String esperado) throws Exception {
        mvc.perform(get("/api/usuarios/" + u.id()).header("Authorization", bearerAdmin()))
                .andExpect(jsonPath("$.usuario.estado").value(esperado));
    }
}
