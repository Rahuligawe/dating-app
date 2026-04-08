package com.rahul.eventservice.repository;

import com.rahul.eventservice.entity.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventRepository
        extends JpaRepository<UserEvent, String> {

    List<UserEvent> findByUserIdOrderByEventDateAsc(String userId);

    // Feed: events from all users visible to matches/public
    // ordered by most recent
    @Query("""
        SELECT e FROM UserEvent e
        WHERE e.visibility IN ('PUBLIC', 'MATCHES')
          AND e.userId != :userId
        ORDER BY e.createdAt DESC
    """)
    List<UserEvent> findFeedForUser(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE UserEvent e SET e.likeCount = e.likeCount + 1 " +
            "WHERE e.id = :eventId")
    void incrementLikes(@Param("eventId") String eventId);

    @Modifying
    @Query("UPDATE UserEvent e SET e.commentCount = e.commentCount + 1 " +
            "WHERE e.id = :eventId")
    void incrementComments(@Param("eventId") String eventId);
}