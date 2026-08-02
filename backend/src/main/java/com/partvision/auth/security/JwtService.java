package com.partvision.auth.security;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Genera y valida tokens JWT firmados con HMAC-SHA.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generateToken(Usuario usuario) {
        Instant now = Instant.now();
        List<String> roles = usuario.getRoles().stream().map(Rol::getNombre).toList();
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("uid", usuario.getId())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida la firma y expiracion del token y devuelve sus claims.
     *
     * @throws io.jsonwebtoken.JwtException si el token es invalido o expiro
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
