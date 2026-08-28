package com.partvision.pricing.domain;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "historial_precios")
@Getter @Setter @NoArgsConstructor
public class HistorialPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportPrecioBatch batch;

    @Column(name = "precio_costo_anterior", precision = 12, scale = 2)
    private BigDecimal precioCostoAnterior;

    @Column(name = "precio_venta_anterior", precision = 12, scale = 2)
    private BigDecimal precioVentaAnterior;

    @Column(name = "precio_costo_nuevo", precision = 12, scale = 2)
    private BigDecimal precioCostoNuevo;

    @Column(name = "precio_venta_nuevo", precision = 12, scale = 2)
    private BigDecimal precioVentaNuevo;

    @Column(name = "margen_aplicado", precision = 8, scale = 4)
    private BigDecimal margenAplicado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
