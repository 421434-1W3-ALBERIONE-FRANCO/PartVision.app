package com.partvision.auth.service;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.LoginRequest;
import com.partvision.auth.dto.LoginResponse;
import com.partvision.auth.dto.RegisterRequest;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.repository.RolRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.auth.security.JwtService;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Rol asignado a los usuarios auto-registrados. */
    private static final String DEFAULT_ROLE = "OPERARIO";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("El username ya existe: " + request.username());
        }
        Rol rol = rolRepository.findByNombre(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Rol por defecto no configurado: " + DEFAULT_ROLE));

        Usuario usuario = Usuario.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nombre(request.nombre())
                .activo(true)
                .roles(new HashSet<>(Set.of(rol)))
                .build();

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));
        if (!usuario.isActivo()) {
            throw new InvalidCredentialsException("Usuario inactivo");
        }
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }
        String token = jwtService.generateToken(usuario);
        return new LoginResponse(token, jwtService.getExpirationMs() / 1000);
    }
}
