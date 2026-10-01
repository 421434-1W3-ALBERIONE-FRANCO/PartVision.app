package com.partvision.pricing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Una corrida de la actualizacion automatica de precios: la constancia de lo que paso. */
@Entity
@Table(name = "sincronizaciones_precios")
@Getter @Setter @NoArgsConstructor
public class SincronizacionPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenSincronizacion origen;

    @Column(nullable = false)
    private boolean forzada;

    @Column(name = "iniciada_en", nullable = false)
    private LocalDateTime iniciadaEn = LocalDateTime.now();

    @Column(name = "terminada_en")
    private LocalDateTime terminadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoSincronizacion resultado = ResultadoSincronizacion.EN_CURSO;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Column(columnDefinition = "TEXT")
    private String problemas;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "filas_lista", nullable = false)
    private int filasLista;

    @Column(nullable = false)
    private int actualizados;

    @Column(name = "sin_cambio", nullable = false)
    private int sinCambio;

    @Column(name = "no_encontrados", nullable = false)
    private int noEncontrados;

    @Column(name = "filas_invalidas", nullable = false)
    private int filasInvalidas;

    @Column(name = "en_revision", nullable = false)
    private int enRevision;

    @Column(name = "con_precio_propio")
    private Integer conPrecioPropio;
}
