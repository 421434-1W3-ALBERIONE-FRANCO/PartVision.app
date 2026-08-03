package com.partvision.ai.vision;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea el cliente de Anthropic solo cuando el proveedor de vision es "claude".
 * La API key se lee de la variable de entorno ANTHROPIC_API_KEY.
 */
@Configuration
@ConditionalOnProperty(name = "ai.vision.provider", havingValue = "claude")
public class AnthropicClientConfig {

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.fromEnv();
    }
}
