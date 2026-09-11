package com.partvision.pricing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion_precios")
@Getter @Setter @NoArgsConstructor
public class ConfiguracionPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String proveedor;

    /** Margen de reventa: se aplica sobre el costo para obtener el precio de venta. */
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal margen;

    /**
     * Recargo del proveedor sobre el precio de lista del archivo importado, para
     * llegar al costo real de compra. Cero cuando el archivo ya trae ese precio.
     */
    @Column(name = "ajuste_lista", nullable = false, precision = 8, scale = 4)
    private BigDecimal ajusteLista = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
