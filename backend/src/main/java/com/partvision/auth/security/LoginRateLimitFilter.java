package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting para frenar fuerza bruta en el login: cada IP tiene un "balde" de N intentos
 * que se recarga por completo cada X tiempo (token bucket de bucket4j). Al agotarse, responde
 * 429 sin siquiera tocar la base ni verificar credenciales.
 *
 * <p>Se registra scopeado SOLO a {@code POST /api/v1/auth/login} (ver {@code RateLimitConfig}),
 * asi no afecta el resto de la API. La IP se toma de {@code X-Forwarded-For} (estamos detras del
 * proxy nginx/Render); es defensa en profundidad —un atacante podria rotar ese header—, se
 * complementa con BCrypt y (a futuro) 2FA.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final int capacity;
    private final Duration refill;
    private final ObjectMapper objectMapper;

    // Un balde por IP. Para el volumen de un login esta bien en memoria; si algun dia se
    // necesita acotar el crecimiento o compartir entre instancias, migrar a Caffeine/Redis.
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(int capacity, Duration refill, ObjectMapper objectMapper) {
        this.capacity = capacity;
        this.refill = refill;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // El endpoint de login es POST; cualquier otra cosa no se limita.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        Bucket bucket = buckets.computeIfAbsent(clientIp(request), k -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            tooManyRequests(request, response);
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refill)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void tooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(refill.toSeconds()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", "Demasiados intentos de inicio de sesion. Espera un momento e intenta de nuevo.");
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
