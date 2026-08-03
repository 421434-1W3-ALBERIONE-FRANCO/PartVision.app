package com.partvision.ai.vision;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea el cliente de OpenAI solo cuando el proveedor de vision es "openai".
 * La API key se lee de la variable de entorno OPENAI_API_KEY.
 */
@Configuration
@ConditionalOnProperty(name = "ai.vision.provider", havingValue = "openai")
public class OpenAIClientConfig {

    @Bean
    public OpenAIClient openAIClient() {
        return OpenAIOkHttpClient.fromEnv();
    }
}
