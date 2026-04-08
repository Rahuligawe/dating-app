package com.rahul.moodservice.repository;

import com.rahul.moodservice.entity.MoodDislike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MoodDislikeRepository
        extends JpaRepository<MoodDislike, Long> {

    boolean existsByMoodIdAndUserId(String moodId, String userId);

    Optional<MoodDislike> findByMoodIdAndUserId(String moodId, String userId);

    void deleteByMoodIdAndUserId(String moodId, String userId);
}
