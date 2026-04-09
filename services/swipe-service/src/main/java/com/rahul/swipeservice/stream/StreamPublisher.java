package com.rahul.swipeservice.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .ofMap(Map.of("payload", json))
                            .withStreamKey("stream:" + topic)
            );
            log.debug("Published to stream:{}", topic);
        } catch (Exception e) {
            log.warn("Stream publish failed [{}]: {}", topic, e.getMessage());
        }
    }
}
