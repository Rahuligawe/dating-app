package com.rahul.matchservice.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.matchservice.service.MatchService;
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
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamConsumerConfig {

    private static final String STREAM          = "stream:match.create";
    private static final String UNMATCH_STREAM  = "stream:match.unmatch";
    private static final String GROUP           = "match-service";
    private static final String CONSUMER        = "match-service-1";

    private final StringRedisTemplate       stringRedisTemplate;
    private final RedisConnectionFactory    connectionFactory;
    private final MatchService              matchService;
    private final ObjectMapper              objectMapper;

    @PostConstruct
    public void initGroup() {
        for (String stream : List.of(STREAM, UNMATCH_STREAM)) {
            try {
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(stream))) {
                    stringRedisTemplate.opsForStream().add(stream, Map.of("init", "1"));
                }
                stringRedisTemplate.opsForStream().createGroup(stream, ReadOffset.latest(), GROUP);
                log.info("Consumer group '{}' created for stream {}", GROUP, stream);
            } catch (Exception e) {
                log.debug("Consumer group '{}' for {} already exists", GROUP, stream);
            }
        }
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> matchStreamContainer() {
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
                        matchService.processMatchCreate(event);
                        stringRedisTemplate.opsForStream()
                                .acknowledge(STREAM, GROUP, message.getId());
                    } catch (Exception e) {
                        log.error("Failed to process match.create: {}", e.getMessage());
                    }
                }
        );

        container.start();
        return container;
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> unmatchStreamContainer() {
        var options = StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofMillis(200))
                .build();

        var container = StreamMessageListenerContainer
                .<String, MapRecord<String, String, String>>create(connectionFactory, options);

        container.receive(
                Consumer.from(GROUP, CONSUMER + "-unmatch"),
                StreamOffset.create(UNMATCH_STREAM, ReadOffset.lastConsumed()),
                message -> {
                    try {
                        String payload = message.getValue().get("payload");
                        Map<String, String> event = objectMapper.readValue(
                                payload, new TypeReference<>() {});
                        matchService.unmatchByUserIds(event.get("user1Id"), event.get("user2Id"));
                        stringRedisTemplate.opsForStream()
                                .acknowledge(UNMATCH_STREAM, GROUP, message.getId());
                    } catch (Exception e) {
                        log.error("Failed to process match.unmatch: {}", e.getMessage());
                    }
                }
        );

        container.start();
        return container;
    }
}
