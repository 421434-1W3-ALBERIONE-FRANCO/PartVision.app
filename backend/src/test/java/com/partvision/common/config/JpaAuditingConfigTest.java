package com.partvision.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    @Test
    void seInstancia() {
        assertThat(new JpaAuditingConfig()).isNotNull();
    }
}
