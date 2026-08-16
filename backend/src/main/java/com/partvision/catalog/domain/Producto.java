package com.partvision.catalog.domain;

import com.partvision.common.audit.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "productos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Producto extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador publico (para URLs/respuestas). Lo genera la DB; el 'id' interno no se expone. */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Generated(event = EventType.INSERT)
    private UUID publicId;

    /** Codigo de pieza / SKU. Opcional; unico por marca cuando existe (D2). */
    @Column(length = 100)
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(nullable = false, length = 500)
    private String descripcion;

    /** Proveedor de origen (ej: catalogo importado). Opcional. */
    @Column(length = 150)
    private String proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductoEstado estado;

    /** Atributos variables no normalizados (voltaje, medidas, origen, etc.). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalles_extra", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> detallesExtra = new HashMap<>();

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ProductoCodigo> codigos = new HashSet<>();

    public void addCodigo(ProductoCodigo codigo) {
        codigo.setProducto(this);
        this.codigos.add(codigo);
    }
}
