package com.rahul.chatservice.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.chatservice.service.ChatService;
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
public class ChatEventListener {

    private static final String STREAM   = "stream:chat.create.conversation";
    private static final String GROUP    = "chat-service";
    private static final String CONSUMER = "chat-service-1";

    private final StringRedisTemplate    stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final ChatService            chatService;
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
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> chatStreamContainer() {
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
                        String payload = message.getValue().get("payload");
                        Map<String, String> event = objectMapper.readValue(
                                payload, new TypeReference<>() {});

                        String matchId = event.get("matchId");
                        String user1Id = event.get("user1Id");
                        String user2Id = event.get("user2Id");

                        log.info("Creating conversation for match: {}", matchId);
                        chatService.createConversation(matchId, user1Id, user2Id);

                        stringRedisTemplate.opsForStream()
                                .acknowledge(STREAM, GROUP, message.getId());
                    } catch (Exception e) {
                        log.error("Failed to process chat.create.conversation: {}", e.getMessage());
                    }
                }
        );

        container.start();
        return container;
    }
}
