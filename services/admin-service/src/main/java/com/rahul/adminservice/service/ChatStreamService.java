package com.rahul.adminservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ChatStreamService {

    // conversationId → active SSE emitters watching that conversation
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void addEmitter(String conversationId, SseEmitter emitter) {
        emitters.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        log.debug("SSE emitter added for conversation {}, total: {}", conversationId,
                emitters.get(conversationId).size());
    }

    public void removeEmitter(String conversationId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(conversationId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) emitters.remove(conversationId);
        }
    }

    public void broadcast(String conversationId, Object payload) {
        Set<SseEmitter> set = emitters.get(conversationId);
        if (set == null || set.isEmpty()) return;

        Set<SseEmitter> dead = ConcurrentHashMap.newKeySet();
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                dead.add(emitter);
                log.debug("Dead SSE emitter removed for conversation {}", conversationId);
            }
        }
        set.removeAll(dead);
    }
}
