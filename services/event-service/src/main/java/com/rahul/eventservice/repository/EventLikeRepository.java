package com.rahul.eventservice.repository;

import com.rahul.eventservice.entity.EventLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLikeRepository
        extends JpaRepository<EventLike, Long> {

    boolean existsByEventIdAndUserId(String eventId, String userId);
}