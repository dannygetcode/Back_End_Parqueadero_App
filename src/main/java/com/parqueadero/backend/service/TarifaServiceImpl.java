package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.TarifaDTO;
import com.parqueadero.backend.entity.Parqueadero;
import com.parqueadero.backend.entity.Tarifa;
import com.parqueadero.backend.entity.TipoVehiculo;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.ParqueaderoRepository;
import com.parqueadero.backend.repository.TarifaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Las tarifas no se editan ni se borran: una nueva vigencia sustituye a la anterior. */
@Service
@RequiredArgsConstructor
public class TarifaServiceImpl implements TarifaService {

    private final TarifaRepository tarifaRepo;
    private final ParqueaderoRepository parqueaderoRepo;
    private final Mapeos mapeos;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<TarifaDTO> listar(boolean vigentes) {
        if (!vigentes) {
            return tarifaRepo.findByParqueaderoIdOrderByTipoVehiculoAscVigenteDesdeDesc(Parqueadero.PRINCIPAL)
                    .stream().map(mapeos::tarifa).toList();
        }
        LocalDate hoy = LocalDate.now(clock);
        return Arrays.stream(TipoVehiculo.values())
                .map(tipo -> buscarVigente(tipo, hoy))
                .flatMap(Optional::stream)
                .map(mapeos::tarifa)
                .toList();
    }

    @Override
    @Transactional
    public TarifaDTO crear(TarifaDTO dto) {
        if (dto.vigenteDesde().isBefore(LocalDate.now(clock))) {
            throw NegocioException.invalido("vigenteDesde no puede ser anterior a hoy");
        }
        if (tarifaRepo.existsByParqueaderoIdAndTipoVehiculoAndVigenteDesde(Parqueadero.PRINCIPAL, dto.tipoVehiculo(),
                dto.vigenteDesde())) {
            throw NegocioException.conflicto("Ya existe una tarifa de ese tipo con esa fecha de vigencia");
        }
        Tarifa t = new Tarifa();
        t.setParqueadero(parqueaderoRepo.getReferenceById(Parqueadero.PRINCIPAL));
        t.setTipoVehiculo(dto.tipoVehiculo());
        t.setValorMensual(dto.valorMensual());
        t.setVigenteDesde(dto.vigenteDesde());
        return mapeos.tarifa(tarifaRepo.saveAndFlush(t));
    }

    @Override
    @Transactional(readOnly = true)
    public Tarifa vigenteEn(TipoVehiculo tipo, LocalDate fecha) {
        return buscarVigente(tipo, fecha).orElseThrow(() -> NegocioException.conflicto("SIN_TARIFA_VIGENTE",
                "No hay tarifa vigente para " + tipo + " en " + fecha));
    }

    private Optional<Tarifa> buscarVigente(TipoVehiculo tipo, LocalDate fecha) {
        return tarifaRepo.findFirstByParqueaderoIdAndTipoVehiculoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                Parqueadero.PRINCIPAL, tipo, fecha);
    }
}
