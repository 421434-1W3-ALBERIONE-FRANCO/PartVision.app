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
 * Rate limiting por IP para frenar fuerza bruta: cada IP tiene un "balde" de N intentos que se
 * recarga por completo cada X tiempo (token bucket de bucket4j). Al agotarse responde 429 sin
 * tocar la base ni el cuerpo del request.
 *
 * <p>Se registra scopeado a rutas puntuales (ver {@code RateLimitConfig}): login, recuperacion
 * de cuenta y la recepcion publica de compras. Como estamos detras de nginx, la IP real sale de
 * {@code X-Forwarded-For} (ver {@link #clientIp}). Se complementa con BCrypt, 2FA y la API key
 * segun la ruta.
 */
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final String MENSAJE_LOGIN =
            "Demasiados intentos de inicio de sesion. Espera un momento e intenta de nuevo.";

    private final int capacity;
    private final Duration refill;
    private final ObjectMapper objectMapper;
    private final String mensaje;

    // Un balde por IP. Para este volumen esta bien en memoria; si algun dia se necesita
    // acotar el crecimiento o compartir entre instancias, migrar a Caffeine/Redis.
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public IpRateLimitFilter(int capacity, Duration refill, ObjectMapper objectMapper) {
        this(capacity, refill, objectMapper, MENSAJE_LOGIN);
    }

    public IpRateLimitFilter(int capacity, Duration refill, ObjectMapper objectMapper, String mensaje) {
        this.capacity = capacity;
        this.refill = refill;
        this.objectMapper = objectMapper;
        this.mensaje = mensaje;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Las rutas limitadas son POST; cualquier otra cosa no se limita.
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

    /**
     * IP con la que se cuenta el limite. nginx arma el header con
     * {@code $proxy_add_x_forwarded_for}: copia lo que mando el cliente y AGREGA al final la
     * direccion desde la que realmente se conecto. Todo lo anterior al ultimo valor lo escribe
     * el cliente, asi que leer el primero dejaba esquivar el limite cambiando el header en cada
     * request. El ultimo es el unico que pone nginx.
     *
     * <p>Y solo se le cree al header si la conexion viene del propio host (nginx corre ahi):
     * si alguna vez el backend vuelve a quedar expuesto, un request directo no puede elegir
     * con que IP se lo cuenta.
     */
    static String clientIp(HttpServletRequest request) {
        // El mapa de baldes no admite claves null: sin direccion, todos comparten uno.
        String remota = request.getRemoteAddr() != null ? request.getRemoteAddr() : "desconocida";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || !esLoopback(remota)) {
            return remota;
        }
        // Con limite -1 split nunca devuelve un array vacio (",".split(",") si lo haria).
        String[] saltos = forwarded.split(",", -1);
        String ultima = saltos[saltos.length - 1].trim();
        return ultima.isEmpty() ? remota : ultima;
    }

    private static boolean esLoopback(String ip) {
        return ip.startsWith("127.") || ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1") || ip.startsWith("::ffff:127.");
    }

    private void tooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(refill.toSeconds()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", mensaje);
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
