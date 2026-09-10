package com.partvision;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PartVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartVisionApplication.class, args);
    }

    @PostConstruct
    void ensureMultipartTmpDir() {
        try { Files.createDirectories(Path.of("/tmp/partvision-uploads")); }
        catch (IOException ignored) {}
    }
}
