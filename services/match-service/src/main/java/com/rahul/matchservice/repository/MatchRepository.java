package com.rahul.matchservice.repository;

import com.rahul.matchservice.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, String> {

    boolean existsByUser1IdAndUser2Id(String user1Id, String user2Id);

    List<Match> findByUser1IdOrUser2Id(String user1Id, String user2Id);

    @Query("""
        SELECT m FROM Match m
        WHERE (m.user1Id = :u1 AND m.user2Id = :u2)
           OR (m.user1Id = :u2 AND m.user2Id = :u1)
    """)
    Optional<Match> findMatchBetween(
            @Param("u1") String user1Id,
            @Param("u2") String user2Id);

    long countByUser1IdOrUser2Id(String user1Id, String user2Id);

    @Query("SELECT COUNT(m) FROM Match m WHERE (m.user1Id = :userId OR m.user2Id = :userId) AND m.isActive = true")
    long countActiveByUserId(@Param("userId") String userId);
}