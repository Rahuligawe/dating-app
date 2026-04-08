package com.rahul.chatservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Clients subscribe to /topic/...
        config.enableSimpleBroker("/topic", "/queue");
        // Client sends messages to /app/...
        config.setApplicationDestinationPrefixes("/app");
        // User specific messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Raw WebSocket endpoint for native Android clients (no SockJS overhead)
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");
    }
}