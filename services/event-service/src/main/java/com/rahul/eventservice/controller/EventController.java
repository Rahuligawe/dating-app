package com.rahul.eventservice.controller;

import com.rahul.eventservice.dto.EventDtos.*;
import com.rahul.eventservice.entity.EventComment;
import com.rahul.eventservice.entity.UserEvent;
import com.rahul.eventservice.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<UserEvent> createEvent(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(userId, request));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<UserEvent>> getFeed(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(eventService.getFeedForUser(userId));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<UserEvent>> getMyEvents(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(eventService.getMyEvents(userId));
    }

    @PostMapping("/{eventId}/like")
    public ResponseEntity<Map<String, Object>> likeEvent(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String eventId) {
        return ResponseEntity.ok(eventService.likeEvent(eventId, userId));
    }

    @PostMapping("/{eventId}/comments")
    public ResponseEntity<EventComment> addComment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String eventId,
            @Valid @RequestBody AddCommentRequest request) {
        return ResponseEntity.ok(
                eventService.addComment(eventId, userId, request));
    }

    @GetMapping("/{eventId}/comments")
    public ResponseEntity<List<EventComment>> getComments(
            @PathVariable String eventId) {
        return ResponseEntity.ok(eventService.getComments(eventId));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Map<String, String>> deleteEvent(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String eventId) {
        eventService.deleteEvent(eventId, userId);
        return ResponseEntity.ok(
                Map.of("message", "Event deleted successfully"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of("status", "UP", "service", "event-service"));
    }
}