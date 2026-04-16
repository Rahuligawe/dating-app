package com.rahul.chatservice.config;

import com.rahul.chatservice.redis.RedisSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@Slf4j
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer container(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {

        // Resilient container — startup Redis failure se app crash nahi hoga.
        // Single-instance mein ChatService ka messagingTemplate.convertAndSend()
        // already WebSocket pe deliver kar deta hai; Redis pub/sub multi-instance
        // horizontal scaling ke liye hai. Agar Redis unavailable ho to silently
        // skip karo, auto-recovery hogi jab Redis available hoga.
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public void start() {
                try {
                    super.start();
                    log.info("Redis pub/sub container started successfully");
                } catch (Exception e) {
                    log.warn("Redis pub/sub unavailable at startup (messages via WebSocket direct): {}",
                            e.getMessage());
                }
            }
        };

        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(10_000); // 10 sec mein reconnect try karega
        // Chat messages
        container.addMessageListener(listenerAdapter, new ChannelTopic("chat"));
        // Presence/status updates — pattern matches status.{any-userId}
        container.addMessageListener(listenerAdapter, new PatternTopic("status.*"));

        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "receiveMessage");
    }
}