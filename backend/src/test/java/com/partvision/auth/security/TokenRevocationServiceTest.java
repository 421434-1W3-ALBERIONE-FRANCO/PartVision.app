package com.partvision.auth.security;

import com.partvision.auth.domain.TokenRevocado;
import com.partvision.auth.repository.TokenRevocadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private TokenRevocadoRepository repository;
    @InjectMocks
    private TokenRevocationService service;

    @Test
    void revocar_guardaElJtiConSuExpiracion() {
        when(repository.existsById("jti-1")).thenReturn(false);
        Instant expira = Instant.now().plusSeconds(3600);

        service.revocar("jti-1", expira);

        ArgumentCaptor<TokenRevocado> captor = ArgumentCaptor.forClass(TokenRevocado.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getJti()).isEqualTo("jti-1");
        assertThat(captor.getValue().getExpiraEn()).isEqualTo(expira);
    }

    @Test
    void revocar_esIdempotente_noReinsertaSiYaEsta() {
        when(repository.existsById("jti-1")).thenReturn(true);

        service.revocar("jti-1", Instant.now());

        verify(repository, never()).save(any());
    }

    @Test
    void revocar_jtiNuloOVacio_noTocaElRepositorio() {
        service.revocar(null, Instant.now());
        service.revocar("  ", Instant.now());

        verifyNoInteractions(repository);
    }

    @Test
    void estaRevocado_reflejaElRepositorioYManejaNull() {
        when(repository.existsById("revocado")).thenReturn(true);

        assertThat(service.estaRevocado("revocado")).isTrue();
        assertThat(service.estaRevocado(null)).isFalse();
    }

    @Test
    void limpiarExpirados_borraPorFecha() {
        service.limpiarExpirados();
        verify(repository).deleteByExpiraEnBefore(any(Instant.class));
    }
}
