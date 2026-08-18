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
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.InvalidCredentialsException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.common.exception.TwoFactorRequiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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
    private final TwoFactorService twoFactorService;

    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        return crearUsuario(request);
    }

    /** Crea un usuario con rol OPERARIO. Reutilizable desde el controller de admin. */
    @Transactional
    public UsuarioResponse crearUsuario(RegisterRequest request) {
        if (usuarioRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("El username ya existe: " + request.username());
        }
        if (request.email() != null && !request.email().isBlank()
                && usuarioRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("El email ya está registrado");
        }
        String rolNombre = (request.rol() == null || request.rol().isBlank())
                ? DEFAULT_ROLE
                : request.rol().trim().toUpperCase();
        Rol rol = rolRepository.findByNombre(rolNombre)
                .orElseThrow(() -> new BusinessException("Rol invalido: " + rolNombre));

        Usuario usuario = Usuario.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nombre(request.nombre())
                .email(request.email() != null && !request.email().isBlank() ? request.email() : null)
                .activo(true)
                .roles(new HashSet<>(Set.of(rol)))
                .build();

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    /** Lista todos los usuarios del sistema. */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    /** Reemplaza el rol de un usuario existente. Valida que el rol exista. */
    @Transactional
    public UsuarioResponse cambiarRol(Long id, String rolNombre) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        String nombre = (rolNombre == null ? "" : rolNombre.trim().toUpperCase());
        Rol rol = rolRepository.findByNombre(nombre)
                .orElseThrow(() -> new BusinessException("Rol invalido: " + nombre));
        usuario.setRoles(new HashSet<>(Set.of(rol)));
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    /**
     * Elimina un usuario por completo (hard delete). Sus roles se borran en cascada
     * (FK ON DELETE CASCADE). No puede eliminarse a sí mismo (lo valida el controller).
     */
    @Transactional
    public void eliminarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        usuarioRepository.delete(usuario);
    }

    /** Activa o desactiva un usuario. No puede desactivarse a sí mismo (lo valida el controller). */
    @Transactional
    public UsuarioResponse toggleActivo(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        usuario.setActivo(!usuario.isActivo());
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarEmail(Long id, String email) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (email != null && !email.isBlank()) {
            usuarioRepository.findByEmail(email).ifPresent(otro -> {
                if (!otro.getId().equals(id)) {
                    throw new DuplicateResourceException("El email ya está registrado");
                }
            });
        }
        usuario.setEmail(email != null && !email.isBlank() ? email : null);
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public void cambiarPasswordPropia(Long userId, String passwordActual, String nuevaPassword, String code) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        if (!passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
            throw new InvalidCredentialsException("La contraseña actual es incorrecta");
        }
        if (usuario.isTotpEnabled()) {
            if (code == null || code.isBlank()) {
                throw new BusinessException("Se requiere el código 2FA para cambiar la contraseña");
            }
            if (!twoFactorService.verificarLogin(usuario, code)) {
                throw new InvalidCredentialsException("Código de 2FA inválido");
            }
        }
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));
        if (!usuario.isActivo()) {
            throw new InvalidCredentialsException("Usuario inactivo");
        }
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }
        if (usuario.isTotpEnabled()) {
            if (request.code() == null || request.code().isBlank()) {
                throw new TwoFactorRequiredException("Ingresá el código de tu autenticador");
            }
            if (!twoFactorService.verificarLogin(usuario, request.code())) {
                throw new InvalidCredentialsException("Código de 2FA inválido");
            }
        }
        String token = jwtService.generateToken(usuario);
        return new LoginResponse(token, jwtService.getExpirationMs() / 1000);
    }
}
