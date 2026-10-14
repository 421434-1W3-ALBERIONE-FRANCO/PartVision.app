package com.partvision;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PartVisionApplication {

    public static void main(String[] args) {
        // Todo lo que se guarda sin zona (LocalDateTime) es UTC, y asi sale al JSON
        // (FechaUtcSerializer). El servidor ya corre en UTC; esto lo asegura tambien en una PC
        // con otra zona, donde LocalDateTime.now() daria la hora local y se mezclaria.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(PartVisionApplication.class, args);
    }

    @PostConstruct
    void ensureMultipartTmpDir() {
        try { Files.createDirectories(Path.of("/tmp/partvision-uploads")); }
        catch (IOException ignored) {}
    }
}
