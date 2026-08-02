package com.partvision.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentacion OpenAPI / Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI partVisionOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("PartVision API")
                .description("Sistema de gestion de inventario y digitalizacion")
                .version("v0.1.0"));
    }
}
