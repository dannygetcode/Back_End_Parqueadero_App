package com.parqueadero.backend.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // Clave secreta de al menos 256 bits (32 bytes). Guárdala idealmente en env
    // vars.
    private static final String SECRET = "ParkingAeremus231!PathProjectEngineer!";
    private static final long EXPIRATION_MS = 1000 * 60 * 60 * 4; // 4 horas

    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    public String generarToken(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + EXPIRATION_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validarToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    public String extraerSubject(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
