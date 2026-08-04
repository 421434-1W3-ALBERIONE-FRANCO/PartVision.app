package com.partvision.ai.vision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Crea el cliente REST de Gemini solo cuando el proveedor de vision es "gemini".
 * A diferencia de Claude/OpenAI no hay SDK: se usa la API REST oficial de Google
 * (generativelanguage.googleapis.com). La API key se lee de GEMINI_API_KEY.
 */
@Configuration
@ConditionalOnProperty(name = "ai.vision.provider", havingValue = "gemini")
public class GeminiClientConfig {

    @Bean
    public RestClient geminiRestClient(
            @Value("${ai.vision.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
            String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
