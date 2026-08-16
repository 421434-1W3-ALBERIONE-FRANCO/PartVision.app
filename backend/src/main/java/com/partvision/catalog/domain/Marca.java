package com.partvision.catalog.domain;

import com.partvision.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.util.UUID;

@Entity
@Table(name = "marcas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Marca extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador publico (para URLs/respuestas). Lo genera la DB; el 'id' interno no se expone. */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Generated(event = EventType.INSERT)
    private UUID publicId;

    @Column(nullable = false, unique = true, length = 150)
    private String nombre;
}
