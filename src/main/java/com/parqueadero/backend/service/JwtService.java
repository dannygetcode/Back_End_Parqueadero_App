package com.parqueadero.backend.service;

import com.parqueadero.backend.config.Rol;
import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.TokenDTO;
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

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiracion.admin:4h}") Duration expiracionAdmin,
                      @Value("${jwt.expiracion.usuario:12h}") Duration expiracionUsuario,
                      Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracionAdmin = expiracionAdmin;
        this.expiracionUsuario = expiracionUsuario;
        this.clock = clock;
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
            return Optional.of(new UsuarioAutenticado(Long.valueOf(claims.getSubject()), rol));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }
}
