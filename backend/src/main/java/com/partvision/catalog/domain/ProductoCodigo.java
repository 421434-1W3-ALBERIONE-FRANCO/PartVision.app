package com.partvision.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Codigo asociado a un producto (barras EAN, codigo interno, de proveedor, etc.).
 * Un producto puede tener varios; cada codigo es unico en todo el catalogo (D1).
 */
@Entity
@Table(name = "producto_codigos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoCodigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, unique = true, length = 100)
    private String codigo;

    @Column(length = 30)
    private String tipo;
}
