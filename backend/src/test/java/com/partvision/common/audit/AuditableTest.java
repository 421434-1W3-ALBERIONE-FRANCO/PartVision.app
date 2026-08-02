package com.partvision.common.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditableTest {

    /** Subclase concreta minima para instanciar la base @MappedSuperclass. */
    static class SampleEntity extends Auditable {
    }

    @Test
    void exponeCamposDeAuditoria() {
        Instant now = Instant.now();
        SampleEntity entity = new SampleEntity();

        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(2L);

        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        assertThat(entity.getCreatedBy()).isEqualTo(1L);
        assertThat(entity.getUpdatedBy()).isEqualTo(2L);
    }
}
