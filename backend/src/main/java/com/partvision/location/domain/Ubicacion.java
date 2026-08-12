package com.partvision.location.domain;

import com.partvision.common.audit.Auditable;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ubicaciones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ubicacion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Ubicacion parent;

    /** Opcional en el modelo plano; el cliente puede clasificar o dejarlo sin tipo. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoUbicacion tipo;

    @Column(nullable = false, length = 50)
    private String codigo;

    /** Nota libre opcional (color, referencia fisica, etc.). */
    @Column(length = 300)
    private String descripcion;

    /** Camino materializado desde la raiz (ej "A/1/4/2"). */
    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private boolean activo;
}
