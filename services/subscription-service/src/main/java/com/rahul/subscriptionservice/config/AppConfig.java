package com.rahul.subscriptionservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Plain RestTemplate for external API calls (Cashfree, etc.)
    @Bean("externalRestTemplate")
    public RestTemplate externalRestTemplate() {
        return new RestTemplate();
    }
}