package com.partvision.ai.domain;

import com.partvision.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Auditoria de una extraccion IA. El solicitante y la fecha vienen de
 * {@link Auditable} (created_by / created_at).
 */
@Entity
@Table(name = "ai_extractions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExtraction extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Referencia a la imagen en el object storage (no el blob). */
    @Column(name = "imagen_key", nullable = false, length = 500)
    private String imagenKey;

    @Column(nullable = false, length = 100)
    private String modelo;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    /** Datos que sugirio la IA (con null donde no detecto nada). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_sugeridos", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> datosSugeridos = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoExtraccion estado;

    /** Producto oficial creado al confirmar (null hasta entonces). */
    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "usuario_confirmador_id")
    private Long usuarioConfirmadorId;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;
}
