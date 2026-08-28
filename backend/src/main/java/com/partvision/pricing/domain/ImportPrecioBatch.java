package com.partvision.pricing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_precio_batch")
@Getter @Setter @NoArgsConstructor
public class ImportPrecioBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String proveedor;

    @Column(nullable = false, length = 20)
    private String fuente;

    @Column(length = 255)
    private String archivo;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int aplicados;

    @Column(nullable = false)
    private int omitidos;

    @Column(nullable = false)
    private int conflictos;

    @Column(nullable = false, length = 20)
    private String estado = "APLICADO";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
