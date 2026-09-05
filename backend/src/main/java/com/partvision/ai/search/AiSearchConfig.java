package com.partvision.ai.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "partvision.search.ai.enabled", havingValue = "true")
class AiSearchConfig {

    @Bean
    RestClient aiSearchRestClient(AiSearchProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeoutMs());
        factory.setReadTimeout((int) properties.timeoutMs());

        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(factory)
                .build();
    }
}
