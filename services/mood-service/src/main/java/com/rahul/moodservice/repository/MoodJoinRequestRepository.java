package com.rahul.moodservice.repository;

import com.rahul.moodservice.entity.MoodJoinRequest;
import com.rahul.moodservice.entity.MoodJoinRequest.JoinStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MoodJoinRequestRepository
        extends JpaRepository<MoodJoinRequest, Long> {

    boolean existsByMoodIdAndFromUserId(
            String moodId, String fromUserId);

    List<MoodJoinRequest> findByMoodIdAndStatus(
            String moodId, JoinStatus status);

    Optional<MoodJoinRequest> findByIdAndStatus(
            Long id, JoinStatus status);
}