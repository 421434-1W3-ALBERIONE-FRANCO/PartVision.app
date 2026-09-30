package com.partvision.compras.domain;

import com.partvision.catalog.domain.Producto;
import com.partvision.common.audit.Auditable;
import com.partvision.location.domain.Ubicacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "compra_lineas")
public class CompraLinea extends Auditable {

    /**
     * Por encima de esta cantidad la linea no entra al stock sin que alguien la mire. No es un
     * limite del negocio sino un aviso: la planilla a veces trae una fila mal armada (el
     * 2026-09-24 llego una con 1.197.421 unidades). Lo mas alto legitimo visto hasta entonces
     * eran 240 retenes, asi que 300 deja margen sin dejar pasar un disparate.
     */
    public static final int CANTIDAD_PARA_REVISAR = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private Integer cantidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_ingreso_id")
    private Ubicacion ubicacionIngreso;

    /** Null si la cantidad es normal. Ver {@link RevisionLinea}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private RevisionLinea revision;

    /** Queda registrada, pero no entra al stock ni a los totales. */
    public boolean descartada() {
        return revision == RevisionLinea.DESCARTADA;
    }

    public boolean pendienteDeRevision() {
        return revision == RevisionLinea.PENDIENTE;
    }
}
