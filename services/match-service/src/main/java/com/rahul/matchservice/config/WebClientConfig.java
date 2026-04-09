package com.rahul.matchservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.user-service.url:http://localhost:8082}")
    private String userServiceUrl;

    @Value("${app.chat-service.url:http://localhost:8085}")
    private String chatServiceUrl;

    @Bean("userServiceClient")
    public WebClient userServiceClient() {
        return WebClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    @Bean("chatServiceClient")
    public WebClient chatServiceClient() {
        return WebClient.builder()
                .baseUrl(chatServiceUrl)
                .build();
    }
}