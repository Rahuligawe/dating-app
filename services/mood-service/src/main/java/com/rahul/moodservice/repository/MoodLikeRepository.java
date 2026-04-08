package com.rahul.moodservice.repository;

import com.rahul.moodservice.entity.MoodLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface MoodLikeRepository
        extends JpaRepository<MoodLike, Long> {

    boolean existsByMoodIdAndUserId(String moodId, String userId);

    Optional<MoodLike> findByMoodIdAndUserId(String moodId, String userId);

    @Modifying
    @Transactional
    void deleteByMoodIdAndUserId(String moodId, String userId);
}