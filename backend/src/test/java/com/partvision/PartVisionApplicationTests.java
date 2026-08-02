package com.partvision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

/**
 * Prueba de humo: verifica que todo el contexto (JPA, auditoria, Flyway,
 * OpenAPI) arranca correctamente contra un PostgreSQL real y que las
 * migraciones se aplican sin error.
 *
 * Requiere Docker. Si no esta disponible, el test se salta (no rompe el build);
 * la cobertura la garantizan los tests unitarios y de la capa web.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIf(value = "dockerDisponible", disabledReason = "Docker no disponible")
class PartVisionApplicationTests {

    @Test
    void contextLoads() {
        // Si el contexto no levanta o Flyway falla, este test falla.
    }

    static boolean dockerDisponible() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
