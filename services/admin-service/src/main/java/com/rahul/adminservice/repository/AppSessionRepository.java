package com.rahul.adminservice.repository;

import com.rahul.adminservice.entity.AppSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AppSessionRepository extends JpaRepository<AppSession, String> {

    @Query("SELECT s FROM AppSession s WHERE s.userId = ?1 AND s.sessionStart >= ?2 ORDER BY s.sessionStart DESC")
    List<AppSession> findByUserIdSince(String userId, LocalDateTime since);

    // Returns [userId, totalSeconds, sessionCount] ordered by totalSeconds DESC
    @Query(value =
        "SELECT user_id, SUM(duration_seconds) AS total_sec, COUNT(*) AS cnt " +
        "FROM app_sessions WHERE session_start >= ?1 AND duration_seconds IS NOT NULL " +
        "GROUP BY user_id ORDER BY total_sec DESC LIMIT ?2",
        nativeQuery = true)
    List<Object[]> findTopUsersByDuration(LocalDateTime since, int limit);
}
