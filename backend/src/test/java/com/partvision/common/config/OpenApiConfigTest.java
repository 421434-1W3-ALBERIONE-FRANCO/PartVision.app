package com.partvision.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void defineMetadatosDeLaApi() {
        OpenAPI openAPI = new OpenApiConfig().partVisionOpenAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("PartVision API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v0.1.0");
    }
}
