package com.rahul.eventservice.repository;

import com.rahul.eventservice.entity.EventComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventCommentRepository
        extends JpaRepository<EventComment, Long> {

    List<EventComment> findByEventIdOrderByCreatedAtAsc(String eventId);
}