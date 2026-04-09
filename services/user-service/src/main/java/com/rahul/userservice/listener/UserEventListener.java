package com.rahul.userservice.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.userservice.service.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private static final String STREAM   = "stream:subscription.updated";
    private static final String GROUP    = "user-service";
    private static final String CONSUMER = "user-service-1";

    private final StringRedisTemplate    stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final UserService            userService;
    private final ObjectMapper           objectMapper;

    @PostConstruct
    public void initGroup() {
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(STREAM))) {
                stringRedisTemplate.opsForStream().add(STREAM, Map.of("init", "1"));
            }
            stringRedisTemplate.opsForStream().createGroup(STREAM, ReadOffset.latest(), GROUP);
            log.info("Consumer group '{}' created for stream {}", GROUP, STREAM);
        } catch (Exception e) {
            log.debug("Consumer group '{}' for {} already exists", GROUP, STREAM);
        }
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> subscriptionStreamContainer() {
        var options = StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofMillis(200))
                .build();

        var container = StreamMessageListenerContainer
                .<String, MapRecord<String, String, String>>create(connectionFactory, options);

        container.receive(
                Consumer.from(GROUP, CONSUMER),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
                message -> {
                    try {
                        Map<String, String> event = objectMapper.readValue(
                                message.getValue().get("payload"), new TypeReference<>() {});

                        String userId = event.get("userId");
                        String plan   = event.get("plan");
                        log.info("Subscription updated for user {}: {}", userId, plan);
                        userService.updateSubscription(userId, plan);

                        stringRedisTemplate.opsForStream()
                                .acknowledge(STREAM, GROUP, message.getId());
                    } catch (Exception e) {
                        log.error("Failed to process subscription.updated: {}", e.getMessage());
                    }
                }
        );

        container.start();
        return container;
    }
}
