package com.rahul.moodservice.service;

import com.rahul.moodservice.dto.MoodDtos;
import com.rahul.moodservice.dto.MoodDtos.*;
import com.rahul.moodservice.entity.*;
import com.rahul.moodservice.entity.MoodJoinRequest.JoinStatus;
import com.rahul.moodservice.repository.*;
import com.rahul.moodservice.entity.MoodDislike;
import com.rahul.moodservice.stream.StreamPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoodService {

    private final MoodRepository            moodRepository;
    private final MoodLikeRepository        moodLikeRepository;
    private final MoodDislikeRepository     moodDislikeRepository;
    private final MoodCommentRepository     moodCommentRepository;
    private final MoodJoinRequestRepository moodJoinRequestRepository;
    private final StreamPublisher streamPublisher;
    @PersistenceContext
    private EntityManager entityManager;

    // ─── Post Mood ────────────────────────────────────────────

    @Transactional
    public MoodStatus postMood(String userId, PostMoodRequest request) {

        // Expire any existing active mood for this user
        moodRepository.deactivateUserMoods(userId);

        int hours = request.getDurationHours() != null
                ? request.getDurationHours() : 2;

        MoodStatus mood = moodRepository.save(MoodStatus.builder()
                .userId(userId)
                .moodType(request.getMoodType())
                .description(request.getDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .locationName(request.getLocationName())
                .distanceRangeKm(request.getDistanceRangeKm() != null
                        ? request.getDistanceRangeKm() : 15)
                .expiryTime(LocalDateTime.now().plusHours(hours))
                .isActive(true)
                .build());

        streamPublisher.publish("mood.posted", Map.of(
                "moodId",          mood.getId(),
                "userId",          userId,
                "moodType",        mood.getMoodType().name(),
                "description",     mood.getDescription() != null ? mood.getDescription() : "",
                "latitude",        mood.getLatitude(),
                "longitude",       mood.getLongitude(),
                "distanceRangeKm", mood.getDistanceRangeKm(),
                "expiryTime",      mood.getExpiryTime().toString()
        ));

        log.info("Mood posted by user: {} | type: {} | expires: {}",
                userId, mood.getMoodType(), mood.getExpiryTime());

        return mood;
    }

    // ─── Get Feed ─────────────────────────────────────────────

    public List<MoodStatus> getActiveMoodFeed(String userId) {
        return moodRepository.findActiveMoodsForFeed(
                userId, LocalDateTime.now());
    }

    // ─── Get My Active Mood ───────────────────────────────────

    public MoodStatus getMyActiveMood(String userId) {
        return moodRepository.findActiveByUserId(
                        userId, LocalDateTime.now())
                .orElse(null);
    }

    // ─── Get My Mood History (all, newest first) ──────────────

    public List<MoodStatus> getMyMoodHistory(String userId) {
        return moodRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ─── Get Single Mood by ID (with reaction state) ─────────

    public Map<String, Object> getMoodByIdWithReactions(String moodId, String userId) {
        MoodStatus mood = moodRepository.findById(moodId).orElse(null);
        if (mood == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("id",           mood.getId());
        m.put("userId",       mood.getUserId());
        m.put("moodType",     mood.getMoodType().name());
        m.put("description",  mood.getDescription());
        m.put("locationName", mood.getLocationName());
        m.put("likeCount",    mood.getLikeCount());
        m.put("dislikeCount", mood.getDislikeCount());
        m.put("commentCount", mood.getCommentCount());
        m.put("createdAt",    mood.getCreatedAt()  != null ? mood.getCreatedAt().toString()  : null);
        m.put("expiryTime",   mood.getExpiryTime() != null ? mood.getExpiryTime().toString() : null);
        m.put("isActive",     mood.getIsActive());
        m.put("likedByMe",    userId != null && moodLikeRepository.existsByMoodIdAndUserId(moodId, userId));
        m.put("dislikedByMe", userId != null && moodDislikeRepository.existsByMoodIdAndUserId(moodId, userId));
        return m;
    }

    // Keep raw entity method for internal use
    public MoodStatus getMoodById(String moodId) {
        return moodRepository.findById(moodId).orElse(null);
    }

    // ─── Like Mood (toggle) ───────────────────────────────────

    @Transactional
    public Map<String, Object> likeMood(String moodId, String userId) {
        boolean liked;

        MoodStatus mood = moodRepository.findById(moodId)
                .orElseThrow(() -> new RuntimeException("Mood not found"));

        if (mood.getUserId().equals(userId)) {
            throw new RuntimeException("You cannot like your own mood");
        }

        log.info("LIKE API HIT → moodId: {}, userId: {}", moodId, userId);
        if (moodLikeRepository.existsByMoodIdAndUserId(moodId, userId)) {
            // Already liked → remove like
            moodLikeRepository.deleteByMoodIdAndUserId(moodId, userId);
            moodRepository.decrementLikes(moodId);
            liked = false;
        } else {
            // Not liked → add like (dislike is independent, leave it alone)
            moodLikeRepository.save(MoodLike.builder().moodId(moodId).userId(userId).build());
            moodRepository.incrementLikes(moodId);
            // mood.liked has no consumer — skip publishing
            liked = true;
        }

        entityManager.flush();
        entityManager.clear();

        /*MoodStatus mood = moodRepository.findById(moodId).orElse(null);
        if (mood == null) {
            throw new RuntimeException("Mood not found");
        }*/
        boolean dislikedByMe = moodDislikeRepository.existsByMoodIdAndUserId(moodId, userId);

        // Notify mood owner via FCM (non-fatal if Kafka unavailable)
        if (mood != null && !mood.getUserId().equals(userId) && liked) {
            Map<String, Object> notif = new HashMap<>();
            notif.put("userId", mood.getUserId());
            notif.put("type",   "MOOD_LIKE");
            notif.put("title",  "❤️ Someone liked your mood!");
            notif.put("body",   "Someone is interested in joining you!");
            Map<String, String> data = new HashMap<>();
            data.put("moodId",       moodId);
            data.put("likeCount",    String.valueOf(mood.getLikeCount()));
            data.put("dislikeCount", String.valueOf(mood.getDislikeCount()));
            notif.put("data", data);
            streamPublisher.publish("notification.send", notif);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("liked",       liked);
        result.put("likedByMe",   liked);
        result.put("dislikedByMe", dislikedByMe);
        result.put("message",     liked ? "Liked successfully" : "Unliked");
        result.put("likeCount",   mood != null ? mood.getLikeCount()    : 0);
        result.put("dislikeCount", mood != null ? mood.getDislikeCount() : 0);
        return result;
    }

    // ─── Dislike Mood (toggle) ────────────────────────────────

    @Transactional
    public Map<String, Object> dislikeMood(String moodId, String userId) {
        boolean disliked;

        if (moodDislikeRepository.existsByMoodIdAndUserId(moodId, userId)) {
            // Already disliked → remove dislike
            moodDislikeRepository.deleteByMoodIdAndUserId(moodId, userId);
            moodRepository.decrementDislikes(moodId);
            disliked = false;
        } else {
            // Not disliked → add dislike (like is independent, leave it alone)
            moodDislikeRepository.save(MoodDislike.builder().moodId(moodId).userId(userId).build());
            moodRepository.incrementDislikes(moodId);
            disliked = true;
        }

        MoodStatus mood = moodRepository.findById(moodId).orElse(null);
        boolean likedByMe = moodLikeRepository.existsByMoodIdAndUserId(moodId, userId);

        // Notify mood owner via FCM (non-fatal if Kafka unavailable)
        if (mood != null && !mood.getUserId().equals(userId) && disliked) {
            Map<String, Object> notif = new HashMap<>();
            notif.put("userId", mood.getUserId());
            notif.put("type",   "MOOD_DISLIKE");
            notif.put("title",  "👎 Someone reacted to your mood");
            notif.put("body",   "Someone reacted to your mood post");
            Map<String, String> data = new HashMap<>();
            data.put("moodId",       moodId);
            data.put("likeCount",    String.valueOf(mood.getLikeCount()));
            data.put("dislikeCount", String.valueOf(mood.getDislikeCount()));
            notif.put("data", data);
            streamPublisher.publish("notification.send", notif);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("disliked",    disliked);
        result.put("likedByMe",   likedByMe);
        result.put("dislikedByMe", disliked);
        result.put("message",     disliked ? "Disliked successfully" : "Un-disliked");
        result.put("likeCount",   mood != null ? mood.getLikeCount()    : 0);
        result.put("dislikeCount", mood != null ? mood.getDislikeCount() : 0);
        return result;
    }

    // ─── Comment on Mood ──────────────────────────────────────

    @Transactional
    public MoodDtos.MoodCommentResponse addComment(String moodId, String userId, AddCommentRequest request) {

        // 1. Save comment
        MoodComment saved = moodCommentRepository.save(
                MoodComment.builder()
                        .moodId(moodId)
                        .userId(userId)
                        .comment(request.getComment())
                        .build()
        );

        // 2. Increment comment count (🔥 FIX)
        moodRepository.incrementComments(moodId);

        // 3. Get updated mood (for latest count)
        MoodStatus mood = moodRepository.findById(moodId).orElse(null);

        // 4. ⚠️ TEMP FIX (jab tak user service connect nahi hai)
        String userName = "User";
        String userPhoto = null;

        // 👉 FUTURE (jab user service ready ho)
        // UserProfile user = userClient.getUser(userId);
        // userName = user.getName();
        // userPhoto = user.getProfilePhotoUrl();

        // 5. Return response with data
        return new MoodDtos.MoodCommentResponse(
                saved.getId(),
                saved.getMoodId(),
                saved.getUserId(),
                userName,
                userPhoto,
                saved.getComment(),
                saved.getCreatedAt().toString()
        );
    }

    public List<MoodComment> getComments(String moodId) {
        return moodCommentRepository
                .findByMoodIdOrderByCreatedAtAsc(moodId);
    }

    // ─── Join Request ─────────────────────────────────────────

    @Transactional
    public Map<String, Object> sendJoinRequest(String moodId,
                                               String fromUserId) {
        MoodStatus mood = moodRepository.findById(moodId)
                .orElseThrow(() ->
                        new RuntimeException("Mood not found"));

        if (mood.getUserId().equals(fromUserId)) {
            throw new RuntimeException("Cannot join your own mood");
        }

        if (moodJoinRequestRepository
                .existsByMoodIdAndFromUserId(moodId, fromUserId)) {
            return Map.of("message", "Request already sent");
        }

        MoodJoinRequest joinRequest = moodJoinRequestRepository.save(
                MoodJoinRequest.builder()
                        .moodId(moodId)
                        .fromUserId(fromUserId)
                        .status(JoinStatus.PENDING)
                        .build());

        // Notify mood owner
        streamPublisher.publish("notification.send", Map.of(
                "userId", mood.getUserId(),
                "type",   "MOOD_JOIN_REQUEST",
                "title",  "Someone wants to join! 🙋",
                "body",   "Someone wants to join your mood plan",
                "data",   Map.of(
                        "moodId",        moodId,
                        "fromUserId",    fromUserId,
                        "joinRequestId", joinRequest.getId().toString())
        ));

        return Map.of("message", "Join request sent",
                "requestId", joinRequest.getId());
    }

    // ─── Respond to Join Request ──────────────────────────────

    @Transactional
    public Map<String, String> respondToJoinRequest(
            String moodId,
            String ownerId,
            RespondJoinRequest request) {

        MoodJoinRequest joinReq = moodJoinRequestRepository
                .findByIdAndStatus(
                        Long.parseLong(request.getJoinRequestId()),
                        JoinStatus.PENDING)
                .orElseThrow(() ->
                        new RuntimeException("Join request not found"));

        joinReq.setStatus(request.getAccept()
                ? JoinStatus.ACCEPTED
                : JoinStatus.REJECTED);

        moodJoinRequestRepository.save(joinReq);

        if (request.getAccept()) {
            // Notify requester they were accepted
            streamPublisher.publish("notification.send", Map.of(
                    "userId", joinReq.getFromUserId(),
                    "type",   "MOOD_JOIN_ACCEPTED",
                    "title",  "Join Request Accepted! 🎉",
                    "body",   "Your request was accepted. Say hi!",
                    "data",   Map.of(
                            "moodId",   moodId,
                            "ownerId",  ownerId)
            ));
        }

        return Map.of("message", request.getAccept()
                ? "Request accepted" : "Request rejected");
    }

    // ─── Get Pending Join Requests (for mood owner) ───────────

    public List<MoodJoinRequest> getPendingJoinRequests(String moodId) {
        return moodJoinRequestRepository
                .findByMoodIdAndStatus(moodId, JoinStatus.PENDING);
    }

    // ─── Expire Moods every 15 minutes ───────────────────────

    @Scheduled(fixedDelay = 900_000)
    @Transactional
    public void expireMoods() {
        int expired = moodRepository.expireOldMoods(LocalDateTime.now());
        if (expired > 0) {
            log.info("Expired {} moods", expired);
        }
    }

    // ─── Get Feed with user reaction state ────────────────────────

    public List<Map<String, Object>> getActiveMoodFeedWithReactions(String userId) {
        List<MoodStatus> moods = moodRepository.findActiveMoodsForFeed(userId, LocalDateTime.now());

        return moods.stream().map(mood -> {
            Map<String, Object> moodMap = new HashMap<>();
            moodMap.put("id", mood.getId());
            moodMap.put("userId", mood.getUserId());
            moodMap.put("moodType", mood.getMoodType().name());
            moodMap.put("description", mood.getDescription());
            moodMap.put("locationName", mood.getLocationName());
            moodMap.put("likeCount", mood.getLikeCount());
            moodMap.put("dislikeCount", mood.getDislikeCount());
            moodMap.put("commentCount", mood.getCommentCount());
            moodMap.put("createdAt", mood.getCreatedAt() != null ? mood.getCreatedAt().toString() : null);
            moodMap.put("expiryTime", mood.getExpiryTime() != null ? mood.getExpiryTime().toString() : null);
            moodMap.put("isActive", mood.getIsActive());

            // Add user's reaction state
            moodMap.put("likedByMe", moodLikeRepository.existsByMoodIdAndUserId(mood.getId(), userId));
            moodMap.put("dislikedByMe", moodDislikeRepository.existsByMoodIdAndUserId(mood.getId(), userId));

            return moodMap;
        }).toList();
    }

}