package com.parqueadero.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Simulador de cámara (ADR 0004): cabecera X-Api-Key comparada en tiempo constante con CAMARA_API_KEY.
 * Si coincide, autentica con ROLE_SISTEMA. Sin clave configurada no autentica a nadie (falla cerrado).
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Api-Key";

    private final byte[] claveEsperada;

    public ApiKeyAuthenticationFilter(String apiKey) {
        this.claveEsperada = (apiKey == null || apiKey.isBlank()) ? null : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String recibida = req.getHeader(CABECERA);
        if (claveEsperada != null && recibida != null
                && MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
            var principal = new UsuarioAutenticado(null, Rol.SISTEMA);
            var auth = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(Rol.SISTEMA.autoridad())));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }
}
