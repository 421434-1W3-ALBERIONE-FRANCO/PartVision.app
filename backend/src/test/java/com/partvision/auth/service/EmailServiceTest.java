package com.partvision.auth.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "test@partvision.com");
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:4200");
    }

    @Test
    void enviarResetPassword_construyeYEnvia() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        service.enviarResetPassword("user@test.com", "abc123");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void enviarRecuperacion2FA_construyeYEnvia() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        service.enviarRecuperacion2FA("user@test.com", "123456");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
