package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.*;
import com.parqueadero.backend.entity.EstadoUsuario;
import com.parqueadero.backend.entity.Usuario;

import java.time.LocalDate;
import java.util.List;

public interface UsuarioService {

    List<UsuarioDTO> listar(EstadoUsuario estado, boolean incluirBajas);

    UsuarioDetalleDTO obtenerDetalle(Long id);

    UsuarioAltaRespuestaDTO crear(UsuarioAltaDTO dto);

    UsuarioDTO actualizar(Long id, UsuarioActualizacionDTO dto);

    VehiculoDTO cambiarVehiculo(Long id, VehiculoDTO dto);

    UsuarioDTO cambiarEstado(Long id, CambioEstadoDTO dto);

    CodigoValidacionDTO regenerarCodigo(Long id);

    void darDeBaja(Long id);

    UsuarioDTO yo(Long id);

    void cambiarPin(Long id, CambioPinDTO dto);

    /** Job de vencimiento (ADR 0006): pasa a VENCIDO a los ACTIVO sin pago vigente. Devuelve cuántos cambió. */
    int actualizarVencimientos(LocalDate hoy);

    /** ACTIVO/VENCIDO según los pagos aprobados (RF-35). No toca a los SUSPENDIDO. */
    void recalcularEstado(Usuario usuario);

    /**
     * RF-09: rechaza con 403 las operaciones sensibles si el usuario está dado de baja, bloqueado o (si se pide)
     * SUSPENDIDO. Devuelve el usuario releído de la BD.
     */
    Usuario exigirOperable(Long id, boolean rechazarSuspendido);
}
