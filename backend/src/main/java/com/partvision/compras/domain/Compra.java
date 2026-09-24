package com.partvision.compras.domain;

import com.partvision.common.audit.Auditable;
import com.partvision.location.domain.Ubicacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "compras")
public class Compra extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_factura", nullable = false, unique = true)
    private String numeroFactura;

    @Column(name = "fecha_factura", nullable = false)
    private LocalDate fechaFactura;

    private String proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompraEstado estado = CompraEstado.EN_TRANSITO;

    /**
     * Lo ultimo que dijo la planilla del cliente: {@code EN_TRANSITO} o {@code POR_UBICAR}
     * (que en la planilla se llama INGRESADA). Nunca {@code INGRESADA}, que es un estado
     * nuestro: la planilla no sabe si el stock se cargo.
     *
     * <p>Que nuestro {@link #estado} difiera de este significa que lo movio el panel. Que este
     * valor cambie entre un envio y el siguiente significa que la planilla cambio de opinion.
     * Con esas dos cosas alcanza para no pelear: la planilla pisa el estado solo cuando cambia.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_planilla", nullable = false)
    private CompraEstado estadoPlanilla = CompraEstado.EN_TRANSITO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_ingreso_id")
    private Ubicacion ubicacionIngreso;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CompraLinea> lineas = new ArrayList<>();

    public void addLinea(CompraLinea linea) {
        lineas.add(linea);
        linea.setCompra(this);
    }
}
