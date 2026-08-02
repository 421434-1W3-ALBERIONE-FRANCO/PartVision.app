package com.partvision.inventory.domain;

import com.partvision.catalog.domain.Producto;
import com.partvision.common.audit.Auditable;
import com.partvision.location.domain.Ubicacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Saldo actual de un producto en una ubicacion concreta.
 * Es el "cache" del inventario; el historial vive en {@link MovimientoStock}.
 */
@Entity
@Table(name = "stock")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Column(nullable = false)
    private int cantidad;

    @Version
    @Column(nullable = false)
    private long version;
}
