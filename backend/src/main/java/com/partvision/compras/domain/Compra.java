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
