package com.partvision.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Habilita el procesamiento asíncrono y define el pool para las importaciones
 * masivas (que corren en segundo plano para no bloquear la request ni topar el
 * timeout del proxy). Un solo worker: las importaciones se procesan de a una.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "importExecutor")
    public Executor importExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("import-");
        executor.initialize();
        return executor;
    }
}
