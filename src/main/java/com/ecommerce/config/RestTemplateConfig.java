package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Only consumer today is MLClient's call to the FastAPI /predict endpoint — timeouts
     * are named after that call so they're tunable per environment without a redeploy.
     */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${ml.service.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${ml.service.read-timeout-ms:10000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}