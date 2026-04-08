package com.rahul.eventservice.service;

import com.rahul.eventservice.dto.EventDtos.*;
import com.rahul.eventservice.entity.*;
import com.rahul.eventservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository      eventRepository;
    private final EventLikeRepository  eventLikeRepository;
    private final EventCommentRepository eventCommentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ─── Create Event ─────────────────────────────────────────

    @Transactional
    public UserEvent createEvent(String userId,
                                 CreateEventRequest request) {
        UserEvent event = eventRepository.save(UserEvent.builder()
                .userId(userId)
                .eventType(request.getEventType())
                .title(request.getTitle())
                .description(request.getDescription())
                .locationName(request.getLocationName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .eventDate(request.getEventDate())
                .visibility(request.getVisibility() != null
                        ? request.getVisibility()
                        : UserEvent.Visibility.MATCHES)
                .build());

        // Notify matches about new event
        kafkaTemplate.send("event.created", userId, Map.of(
                "eventId",    event.getId(),
                "userId",     userId,
                "eventType",  event.getEventType().name(),
                "title",      event.getTitle(),
                "visibility", event.getVisibility().name()
        ));

        log.info("Event created: {} by user: {}", event.getId(), userId);
        return event;
    }

    // ─── Get Feed ─────────────────────────────────────────────

    public List<UserEvent> getFeedForUser(String userId) {
        return eventRepository.findFeedForUser(userId);
    }

    // ─── Get My Events ────────────────────────────────────────

    public List<UserEvent> getMyEvents(String userId) {
        return eventRepository.findByUserIdOrderByEventDateAsc(userId);
    }

    // ─── Like Event ───────────────────────────────────────────

    @Transactional
    public Map<String, Object> likeEvent(String eventId, String userId) {
        if (eventLikeRepository.existsByEventIdAndUserId(eventId, userId)) {
            return Map.of("message", "Already liked", "liked", false);
        }

        eventLikeRepository.save(EventLike.builder()
                .eventId(eventId)
                .userId(userId)
                .build());

        eventRepository.incrementLikes(eventId);

        kafkaTemplate.send("event.liked", eventId, Map.of(
                "eventId", eventId,
                "userId",  userId
        ));

        return Map.of("message", "Liked successfully", "liked", true);
    }

    // ─── Comment on Event ─────────────────────────────────────

    @Transactional
    public EventComment addComment(String eventId,
                                   String userId,
                                   AddCommentRequest request) {
        // Verify event exists
        eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        EventComment comment = eventCommentRepository.save(
                EventComment.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .comment(request.getComment())
                        .build());

        eventRepository.incrementComments(eventId);

        return comment;
    }

    // ─── Get Comments ─────────────────────────────────────────

    public List<EventComment> getComments(String eventId) {
        return eventCommentRepository
                .findByEventIdOrderByCreatedAtAsc(eventId);
    }

    // ─── Delete Event ─────────────────────────────────────────

    @Transactional
    public void deleteEvent(String eventId, String userId) {
        UserEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (!event.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized to delete this event");
        }

        eventRepository.delete(event);
        log.info("Event deleted: {}", eventId);
    }
}