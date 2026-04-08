package com.rahul.moodservice.repository;

import com.rahul.moodservice.entity.MoodComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoodCommentRepository
        extends JpaRepository<MoodComment, Long> {

    List<MoodComment> findByMoodIdOrderByCreatedAtAsc(String moodId);
}