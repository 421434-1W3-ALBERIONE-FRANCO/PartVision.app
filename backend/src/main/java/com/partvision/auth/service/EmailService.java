package com.partvision.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${partvision.mail.from}")
    private String fromAddress;

    @Value("${partvision.mail.base-url}")
    private String baseUrl;

    @Async
    public void enviarResetPassword(String destinatario, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String asunto = "PartVision - Restablecer contraseña";
        String cuerpo = """
                <html><body style="font-family: Arial, sans-serif; color: #333;">
                <h2>Restablecer contraseña</h2>
                <p>Recibimos una solicitud para restablecer tu contraseña en PartVision.</p>
                <p>Hacé clic en el siguiente enlace para crear una nueva contraseña:</p>
                <p><a href="%s" style="display:inline-block;padding:12px 24px;background:#1976d2;color:#fff;text-decoration:none;border-radius:4px;">Restablecer contraseña</a></p>
                <p>Si no podés hacer clic en el botón, copiá y pegá este enlace en tu navegador:</p>
                <p style="word-break:break-all;color:#666;">%s</p>
                <p style="color:#999;font-size:12px;">Este enlace expira en 30 minutos. Si no solicitaste este cambio, ignorá este email.</p>
                </body></html>
                """.formatted(resetUrl, resetUrl);
        enviar(destinatario, asunto, cuerpo);
    }

    @Async
    public void enviarRecuperacion2FA(String destinatario, String codigo) {
        String asunto = "PartVision - Código de recuperación 2FA";
        String cuerpo = """
                <html><body style="font-family: Arial, sans-serif; color: #333;">
                <h2>Recuperación de segundo factor</h2>
                <p>Recibimos una solicitud para desactivar el segundo factor de autenticación (2FA) de tu cuenta en PartVision.</p>
                <p>Tu código de verificación es:</p>
                <p style="font-size:32px;font-weight:bold;letter-spacing:8px;text-align:center;color:#1976d2;">%s</p>
                <p style="color:#999;font-size:12px;">Este código expira en 15 minutos. Si no solicitaste esto, ignorá este email y asegurá tu cuenta.</p>
                </body></html>
                """.formatted(codigo);
        enviar(destinatario, asunto, cuerpo);
    }

    private static String enmascarar(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private void enviar(String destinatario, String asunto, String cuerpoHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            mailSender.send(message);
            log.info("Email enviado a {}: {}", enmascarar(destinatario), asunto);
        } catch (MessagingException | MailException e) {
            log.error("Error enviando email a {}: {}", enmascarar(destinatario), e.getMessage());
        }
    }
}
