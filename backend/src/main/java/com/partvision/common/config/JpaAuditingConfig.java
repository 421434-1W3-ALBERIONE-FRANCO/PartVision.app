package com.partvision.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita la auditoria de Spring Data JPA (@CreatedDate, @CreatedBy, etc.).
 * El proveedor del "quien" es {@link AuditorAwareImpl}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
public class JpaAuditingConfig {
}
