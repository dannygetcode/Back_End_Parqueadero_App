package com.parqueadero.backend.service;

import com.parqueadero.backend.config.Rol;
import com.parqueadero.backend.dto.ActivacionDTO;
import com.parqueadero.backend.dto.AdminLoginRequest;
import com.parqueadero.backend.dto.LoginUsuarioDTO;
import com.parqueadero.backend.dto.TokenDTO;
import com.parqueadero.backend.entity.Administrador;
import com.parqueadero.backend.entity.Usuario;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.AdministradorRepository;
import com.parqueadero.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Los métodos de login no hacen rollback al lanzar NegocioException: el contador de intentos fallidos y el bloqueo
 * deben quedar guardados aunque la respuesta sea 401/423.
 * La cuenta se lee con SELECT ... FOR UPDATE: los intentos simultáneos sobre la misma cuenta se serializan y ninguno
 * pierde el incremento del contador (sin el bloqueo, N peticiones paralelas leían el mismo valor y escribían +1).
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String MSG_ADMIN = "Usuario o contraseña incorrectos";
    private static final String MSG_LOGIN = "Teléfono o PIN incorrectos";
    private static final String MSG_ACTIVAR = "Teléfono o código incorrectos";

    private final AdministradorRepository administradorRepo;
    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder encoder;
    private final PoliticaIntentos intentos;
    private final JwtService jwtService;
    private final Clock clock;

    @Override
    @Transactional(noRollbackFor = NegocioException.class)
    public TokenDTO loginAdmin(AdminLoginRequest request) {
        Administrador admin = administradorRepo.bloquearPorUsuario(request.username()).orElse(null);
        if (admin == null) {
            encoder.matches(request.password(), hashRelleno());
            throw NegocioException.noAutenticado(MSG_ADMIN);
        }
        intentos.exigirNoBloqueado(admin.getBloqueadoHasta());
        if (!encoder.matches(request.password(), admin.getPasswordHash())) {
            PoliticaIntentos.Estado e = intentos.registrarFallo(admin.getIntentosFallidos());
            admin.setIntentosFallidos(e.intentosFallidos());
            admin.setBloqueadoHasta(e.bloqueadoHasta());
            throw NegocioException.noAutenticado(MSG_ADMIN);
        }
        admin.setIntentosFallidos(0);
        admin.setBloqueadoHasta(null);
        return jwtService.generarToken(admin.getId(), Rol.ADMIN);
    }

    @Override
    @Transactional(noRollbackFor = NegocioException.class)
    public TokenDTO loginUsuario(LoginUsuarioDTO request) {
        Usuario u = usuarioRepo.bloquearPorTelefono(request.telefono()).orElse(null);
        if (u == null || !u.isValidado() || u.getPinHash() == null) {
            encoder.matches(request.pin(), hashRelleno());
            throw NegocioException.noAutenticado(MSG_LOGIN);
        }
        intentos.exigirNoBloqueado(u.getBloqueadoHasta());
        if (!encoder.matches(request.pin(), u.getPinHash())) {
            registrarFallo(u);
            throw NegocioException.noAutenticado(MSG_LOGIN);
        }
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        return jwtService.generarToken(u.getId(), Rol.USUARIO);
    }

    @Override
    @Transactional(noRollbackFor = NegocioException.class)
    public void activar(ActivacionDTO request) {
        if (ReglasPin.esTrivial(request.pin())) {
            throw NegocioException.invalido("El PIN es demasiado fácil de adivinar");
        }
        Usuario u = usuarioRepo.bloquearPorTelefono(request.telefono()).orElse(null);
        if (u == null) {
            encoder.matches(request.codigo(), hashRelleno());
            throw NegocioException.noAutenticado(MSG_ACTIVAR);
        }
        intentos.exigirNoBloqueado(u.getBloqueadoHasta());
        Instant ahora = clock.instant();
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        boolean vigente = u.getCodigoValidacionHash() != null
                && u.getCodigoValidacionExpiraEn() != null
                && u.getCodigoValidacionExpiraEn().isAfter(ahora);
        if (!vigente || !encoder.matches(codigo, u.getCodigoValidacionHash())) {
            registrarFallo(u);
            throw NegocioException.noAutenticado(MSG_ACTIVAR);
        }
        u.setPinHash(encoder.encode(request.pin()));
        u.setValidadoEn(ahora);
        u.setConsentimientoDatosEn(ahora);
        u.setConsentimientoVersion(request.versionConsentimiento().trim());
        u.setCodigoValidacionHash(null);
        u.setCodigoValidacionExpiraEn(null);
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
    }

    @Override
    @Transactional
    public void crearAdministradorInicialSiFalta(String usuario, String password) {
        if (administradorRepo.count() > 0) {
            return;
        }
        if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "No hay administrador y faltan ADMIN_USERNAME/ADMIN_PASSWORD para crearlo");
        }
        Administrador admin = new Administrador();
        admin.setUsuario(usuario.trim());
        admin.setPasswordHash(encoder.encode(password));
        administradorRepo.save(admin);
        log.info("Administrador inicial creado");
    }

    /** Hash BCrypt de relleno para igualar el tiempo de respuesta cuando la cuenta no existe. */
    private volatile String hashRelleno;

    private String hashRelleno() {
        if (hashRelleno == null) {
            hashRelleno = encoder.encode("relleno-" + System.nanoTime());
        }
        return hashRelleno;
    }

    private void registrarFallo(Usuario u) {
        PoliticaIntentos.Estado e = intentos.registrarFallo(u.getIntentosFallidos());
        u.setIntentosFallidos(e.intentosFallidos());
        u.setBloqueadoHasta(e.bloqueadoHasta());
    }
}
