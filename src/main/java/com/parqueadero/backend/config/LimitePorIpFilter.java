package com.parqueadero.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de peticiones por IP en los endpoints de autenticación (ADR 0003): ventana deslizante en memoria de
 * {@code seguridad.limite-ip.ventana-segundos} con como mucho {@code seguridad.limite-ip.max-peticiones} peticiones
 * por IP y ruta. Al superarlo responde 429 (ProblemDetail) con {@code Retry-After} sin llegar al login.
 *
 * La IP es la de la conexión. Detrás de un proxy inverso todas las peticiones llegan desde el proxy: entonces hay que
 * configurar {@code seguridad.limite-ip.cabecera-ip-cliente} (p. ej. {@code X-Real-IP}) con una cabecera que el proxy
 * <b>sobrescriba</b> siempre; si el backend es accesible sin pasar por el proxy, esa cabecera se puede falsificar.
 *
 * Es por instancia y se pierde al reiniciar (suficiente para una sola instancia; con varias haría falta Redis).
 * No es un @Component: se registra solo en la cadena de Spring Security (SecurityConfig).
 */
public class LimitePorIpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimitePorIpFilter.class);
    static final Set<String> RUTAS = Set.of("/api/admin/login", "/api/auth/login", "/api/auth/activar");
    /** Por encima de este número de claves se purgan las que no tienen peticiones dentro de la ventana. */
    private static final int MAX_CLAVES = 10_000;

    private final Map<String, Deque<Long>> peticiones = new ConcurrentHashMap<>();
    private final int maxPeticiones;
    private final long ventanaMs;
    private final String cabeceraIp;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public LimitePorIpFilter(int maxPeticiones, Duration ventana, String cabeceraIp, Clock clock,
                             ObjectMapper objectMapper) {
        if (maxPeticiones < 1 || ventana.isNegative() || ventana.isZero()) {
            throw new IllegalArgumentException("seguridad.limite-ip: max-peticiones >= 1 y ventana > 0");
        }
        this.maxPeticiones = maxPeticiones;
        this.ventanaMs = ventana.toMillis();
        this.cabeceraIp = cabeceraIp == null || cabeceraIp.isBlank() ? null : cabeceraIp.trim();
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !"POST".equals(req.getMethod()) || !RUTAS.contains(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        long ahora = clock.millis();
        long esperaMs = registrar(ipCliente(req) + "|" + req.getRequestURI(), ahora);
        if (esperaMs > 0) {
            long segundos = Math.max(1, (esperaMs + 999) / 1000);
            log.warn("Límite por IP superado en {}", req.getRequestURI());
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", String.valueOf(segundos));
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos desde esta dirección. Intente de nuevo en " + segundos + " s.");
            objectMapper.writeValue(res.getOutputStream(), pd);
            return;
        }
        chain.doFilter(req, res);
    }

    /** Registra la petición si cabe en la ventana. Devuelve 0 si se admite, o los ms hasta que quepa otra. */
    long registrar(String clave, long ahora) {
        if (peticiones.size() > MAX_CLAVES) {
            purgar(ahora);
        }
        Deque<Long> cola = peticiones.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (cola) {
            while (!cola.isEmpty() && cola.peekFirst() <= ahora - ventanaMs) {
                cola.pollFirst();
            }
            if (cola.size() >= maxPeticiones) {
                return cola.peekFirst() + ventanaMs - ahora;
            }
            cola.addLast(ahora);
            return 0;
        }
    }

    private void purgar(long ahora) {
        peticiones.entrySet().removeIf(e -> {
            Deque<Long> cola = e.getValue();
            synchronized (cola) {
                return cola.isEmpty() || cola.peekLast() <= ahora - ventanaMs;
            }
        });
    }

    private String ipCliente(HttpServletRequest req) {
        if (cabeceraIp != null) {
            String valor = req.getHeader(cabeceraIp);
            if (valor != null && !valor.isBlank()) {
                // Con listas (X-Forwarded-For) vale el último elemento, el que añade nuestro proxy; los anteriores
                // los puede inventar el cliente.
                String[] partes = valor.split(",");
                return partes[partes.length - 1].trim();
            }
        }
        return req.getRemoteAddr();
    }
}
