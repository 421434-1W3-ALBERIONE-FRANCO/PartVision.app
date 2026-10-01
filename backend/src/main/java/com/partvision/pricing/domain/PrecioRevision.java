package com.partvision.pricing.domain;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un precio que cambio demasiado de golpe y espera que una persona lo apruebe o lo descarte. */
@Entity
@Table(name = "precio_revisiones")
@Getter @Setter @NoArgsConstructor
public class PrecioRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sincronizacion_id", nullable = false)
    private Long sincronizacionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "precio_lista", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioLista;

    @Column(name = "costo_actual", precision = 12, scale = 2)
    private BigDecimal costoActual;

    @Column(name = "costo_nuevo", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoNuevo;

    @Column(name = "variacion_pct", precision = 10, scale = 2)
    private BigDecimal variacionPct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRevisionPrecio estado = EstadoRevisionPrecio.PENDIENTE;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;
}
