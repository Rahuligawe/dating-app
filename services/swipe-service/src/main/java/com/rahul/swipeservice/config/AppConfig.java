package com.rahul.swipeservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    // Plain RestTemplate — Docker DNS se dating-user:8082 directly resolve hoga
    // @LoadBalanced removed — Eureka disabled hai, load balancer fail karta tha
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}