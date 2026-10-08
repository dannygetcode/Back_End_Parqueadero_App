package com.parqueadero.backend.service;

import com.parqueadero.backend.config.Rol;
import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.TokenDTO;
import com.parqueadero.backend.repository.AdministradorRepository;
import com.parqueadero.backend.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * JWT HS256 (ADR 0003): sub = id (usuario o administrador), claim rol = ADMIN | USUARIO.
 * El secreto (JWT_SECRET) debe tener 32 bytes o más: si no, Keys.hmacShaKeyFor falla y la aplicación no arranca.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROL = "rol";

    private final Key key;
    private final Duration expiracionAdmin;
    private final Duration expiracionUsuario;
    private final Clock clock;
    private final UsuarioRepository usuarioRepo;
    private final AdministradorRepository administradorRepo;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiracion.admin:4h}") Duration expiracionAdmin,
                      @Value("${jwt.expiracion.usuario:12h}") Duration expiracionUsuario,
                      Clock clock, UsuarioRepository usuarioRepo, AdministradorRepository administradorRepo) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracionAdmin = expiracionAdmin;
        this.expiracionUsuario = expiracionUsuario;
        this.clock = clock;
        this.usuarioRepo = usuarioRepo;
        this.administradorRepo = administradorRepo;
    }

    public TokenDTO generarToken(Long id, Rol rol) {
        if (rol == Rol.SISTEMA) {
            throw new IllegalArgumentException("SISTEMA se autentica con API key, no con JWT");
        }
        Instant ahora = clock.instant();
        Instant expira = ahora.plus(rol == Rol.ADMIN ? expiracionAdmin : expiracionUsuario);
        String token = Jwts.builder()
                .setSubject(String.valueOf(id))
                .claim(CLAIM_ROL, rol.name())
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(expira))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        return new TokenDTO(token, rol.name(), Fechas.local(expira));
    }

    /** Valida firma y expiración. Vacío si el token no es válido o no trae un rol de JWT conocido. */
    public Optional<UsuarioAutenticado> validar(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setClock(() -> Date.from(clock.instant()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            Rol rol = Rol.valueOf(claims.get(CLAIM_ROL, String.class));
            if (rol == Rol.SISTEMA) {
                return Optional.empty();
            }
            Long id = Long.valueOf(claims.getSubject());
            if (revocado(id, rol, claims.getIssuedAt())) {
                return Optional.empty();
            }
            return Optional.of(new UsuarioAutenticado(id, rol));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    /**
     * Revocación (V3): el token se rechaza si se emitió antes del último cambio de credenciales de la cuenta.
     * El iat del JWT tiene precisión de segundos, así que se compara contra ese cambio truncado a segundos
     * (un login hecho en el mismo segundo del cambio sigue siendo válido).
     */
    private boolean revocado(Long id, Rol rol, Date emitido) {
        if (emitido == null) {
            return true;
        }
        Optional<Instant> cambio = rol == Rol.ADMIN
                ? administradorRepo.credencialesCambiadasEn(id)
                : usuarioRepo.credencialesCambiadasEn(id);
        return cambio.map(c -> emitido.toInstant().isBefore(c.truncatedTo(ChronoUnit.SECONDS))).orElse(false);
    }
}
