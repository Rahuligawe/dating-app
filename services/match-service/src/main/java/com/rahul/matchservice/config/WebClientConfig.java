package com.rahul.matchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * WebClient for calling user-service internal API.
     * Uses Eureka service name — gateway routes it automatically.
     */
    @Bean("userServiceClient")
    public WebClient userServiceClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8082")  // user-service port
                .build();
    }

    /**
     * WebClient for calling chat-service internal API.
     */
    @Bean("chatServiceClient")
    public WebClient chatServiceClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8085")  // chat-service port
                .build();
    }
}