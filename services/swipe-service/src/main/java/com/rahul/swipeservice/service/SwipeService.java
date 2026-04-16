package com.rahul.swipeservice.service;

import com.rahul.swipeservice.dto.SwipedUserDto;
import com.rahul.swipeservice.dto.UserProfileDto;
import com.rahul.swipeservice.entity.Swipe;
import com.rahul.swipeservice.entity.Swipe.SwipeAction;
import com.rahul.swipeservice.exception.SwipeException;
import com.rahul.swipeservice.repository.SwipeRepository;
import com.rahul.swipeservice.stream.StreamPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwipeService {

    private final SwipeRepository swipeRepository;
    private final StreamPublisher streamPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    //RestTemplate inject karo — AppConfig mein @Bean banao
    private final RestTemplate restTemplate;

    @Value("${app.user-service.url:http://dating-user:8082}")
    private String userServiceUrl;

    //private static final int FREE_DAILY_LIMIT = 50;

    @Transactional
    public Map<String, Object> swipe(String fromUserId, String toUserId,
                                     SwipeAction action) {

        // Prevent self-swipe
        if (fromUserId.equals(toUserId)) {
            throw new SwipeException("Cannot swipe on yourself");
        }

        // Check if a swipe already exists between these two users
        java.util.Optional<Swipe> existingOpt =
                swipeRepository.findByFromUserIdAndToUserId(fromUserId, toUserId);

        if (existingOpt.isPresent()) {
            Swipe existing = existingOpt.get();
            if (existing.getAction() == action) {
                // Same action again — nothing to do
                return Map.of("status", "already_swiped", "isMatch", false);
            }
            // Different action (e.g. LIKE → DISLIKE = "unlike") — UPDATE the row
            existing.setAction(action);
            swipeRepository.save(existing);
            log.info("Swipe updated: {} → {} action={}", fromUserId, toUserId, action);
            return Map.of("status", "updated", "isMatch", false);
        }

        // Check daily limit (FREE users only — premium check via Redis flag)
        checkDailyLimit(fromUserId);

        // Save new swipe
        Swipe swipe = swipeRepository.save(Swipe.builder()
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .action(action)
                .build());

        // Increment daily count
        String countKey = "swipe:count:" + fromUserId + ":" + LocalDate.now();
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1) {
            redisTemplate.expire(countKey, 1, TimeUnit.DAYS);
        }

        // Notify target user about the like
        if (action == SwipeAction.LIKE || action == SwipeAction.SUPER_LIKE) {
            sendProfileLikeNotification(fromUserId, toUserId);
        }

        // Check mutual like → trigger match
        boolean isMatch = false;
        if (action == SwipeAction.LIKE || action == SwipeAction.SUPER_LIKE) {
            boolean theyLikedMe =
                    swipeRepository.existsByFromUserIdAndToUserIdAndAction(
                            toUserId, fromUserId, SwipeAction.LIKE)
                            || swipeRepository.existsByFromUserIdAndToUserIdAndAction(
                            toUserId, fromUserId, SwipeAction.SUPER_LIKE);

            if (theyLikedMe) {
                isMatch = true;
                streamPublisher.publish("match.create", Map.of(
                        "user1Id", fromUserId,
                        "user2Id", toUserId
                ));
                log.info("Match triggered between {} and {}", fromUserId, toUserId);
            }
        }

        return Map.of(
                "swipeId", swipe.getId(),
                "action",  action.name(),
                "isMatch", isMatch
        );
    }

    public List<Map<String, Object>> getLikedByMe(String userId) {
        return swipeRepository
                .findByFromUserIdAndAction(userId, SwipeAction.LIKE)
                .stream()
                .map(s -> Map.<String, Object>of(
                        "userId",    s.getToUserId(),
                        "swipedAt",  s.getCreatedAt().toString()))
                .toList();
    }

    public List<Map<String, Object>> getDislikedByMe(String userId) {
        return swipeRepository
                .findByFromUserIdAndAction(userId, SwipeAction.DISLIKE)
                .stream()
                .map(s -> Map.<String, Object>of(
                        "userId",    s.getToUserId(),
                        "swipedAt",  s.getCreatedAt().toString()))
                .toList();
    }

    public List<Map<String, Object>> getPendingRequests(String userId) {
        return swipeRepository
                .findPendingLikesForUser(userId)
                .stream()
                .map(s -> Map.<String, Object>of(
                        "userId",   s.getFromUserId(),
                        "likedAt",  s.getCreatedAt().toString(),
                        "action",   s.getAction().name()))
                .toList();
    }

    private void checkDailyLimit(String userId) {
        // Premium check — Redis se
        String premiumKey = "user:premium:" + userId;
        Object isPremium = redisTemplate.opsForValue().get(premiumKey);
        if (isPremium != null && Boolean.parseBoolean(isPremium.toString())) return;

        // [Fix] Daily limit — subscription-service DB se fetch karo
        int dailyLimit = getDailySwipeLimit(userId);

        String countKey = "swipe:count:" + userId + ":" + LocalDate.now();
        Object count = redisTemplate.opsForValue().get(countKey);
        if (count != null && Integer.parseInt(count.toString()) >= dailyLimit) {
            throw new SwipeException(
                    "Daily swipe limit of " + dailyLimit
                            + " reached. Upgrade to Premium for unlimited swipes.");
        }
    }

    private int getDailySwipeLimit(String userId) {
        try {
            // subscription-service se user ka plan fetch karo
            String subUrl = "http://subscription-service/api/subscriptions/my";
            // X-User-Id header ke saath call karna padega — RestTemplate headers
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-User-Id", userId);
            org.springframework.http.HttpEntity<Void> entity =
                    new org.springframework.http.HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> sub = restTemplate.exchange(
                    "http://subscription-service/api/subscriptions/my",
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    java.util.Map.class
            ).getBody();

            if (sub == null) return 50; // fallback

            String plan = (String) sub.get("plan");
            if (plan == null) return 50;

            // Plan ke hisaab se limit
            return switch (plan.toUpperCase()) {
                case "PREMIUM", "ULTRA" -> Integer.MAX_VALUE; // unlimited
                default -> fetchFreeDailyLimit(); // DB se FREE plan ka limit
            };

        } catch (Exception e) {
            log.warn("Could not fetch subscription for daily limit, using default 50: {}", e.getMessage());
            return 50; // safe fallback
        }
    }

    private int fetchFreeDailyLimit() {
        try {
            // subscription-service se FREE plan features fetch karo
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> features = restTemplate.getForObject(
                    "http://subscription-service/api/subscriptions/plans/FREE/features",
                    java.util.Map.class
            );
            if (features != null && features.get("dailySwipes") != null) {
                return ((Number) features.get("dailySwipes")).intValue();
            }
        } catch (Exception e) {
            log.warn("Could not fetch FREE plan limit from DB: {}", e.getMessage());
        }
        return 50; // hardcoded fallback agar service down ho
    }

    // [Profile Screen] Total swipe count
    // [Fix] countByFromUserId — entity field "fromUserId" hai
    public long getTotalSwipeCount(String userId) {
        return swipeRepository.countByFromUserId(userId);
    }

    // [Swipes Screen] Jinhe maine LIKE kiya
    // [Fix] SwipeAction.LIKE use karo — "direction" field nahi hai entity mein
    public List<SwipedUserDto> getMyLikes(String userId) {
        List<Swipe> swipes = swipeRepository
                .findByFromUserIdAndActionOrderByCreatedAtDesc(userId, SwipeAction.LIKE);
        return swipes.stream()
                .map(s -> enrichWithProfile(s.getToUserId(), "LIKE", s))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // [Swipes Screen] Jinhe maine DISLIKE kiya
    public List<SwipedUserDto> getMyRejected(String userId) {
        List<Swipe> swipes = swipeRepository
                .findByFromUserIdAndActionOrderByCreatedAtDesc(userId, SwipeAction.DISLIKE);
        return swipes.stream()
                .map(s -> enrichWithProfile(s.getToUserId(), "DISLIKE", s))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // [Likes Screen - PAID] Jinne mujhe LIKE kiya
    // [Fix] findByToUserIdAndAction — toUserId field hai entity mein
    public List<SwipedUserDto> getLikedMe(String userId) {
        List<Swipe> swipes = swipeRepository
                .findByToUserIdAndActionOrderByCreatedAtDesc(userId, SwipeAction.LIKE);
        return swipes.stream()
                .map(s -> enrichWithProfile(s.getFromUserId(), "LIKE", s))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // [Likes Screen - FREE] Sirf count
    // [Fix] countByToUserIdAndAction — correct field names
    public long getLikedMeCount(String userId) {
        return swipeRepository.countByToUserIdAndAction(userId, SwipeAction.LIKE);
    }

    // ── Helper: user-service se profile fetch karo ───────────────────────
    // [Fix] UserProfileDto use karo, getToUserId/getFromUserId se userId pass karo
    private SwipedUserDto enrichWithProfile(String targetUserId, String direction, Swipe swipe) {
        try {
            // Direct HTTP to user-service — Eureka disabled, use container URL
            String url = userServiceUrl + "/api/users/" + targetUserId + "/profile";
            log.info("[SwipeService] Fetching profile for userId: {}", targetUserId);

            UserProfileDto profile = restTemplate.getForObject(url, UserProfileDto.class);

            if (profile == null) {
                log.warn("[SwipeService] Profile null for userId: {}", targetUserId);
                // [Fix] Profile null aaye to bhi basic info return karo
                return SwipedUserDto.builder()
                        .userId(targetUserId)
                        .name("User")
                        .direction(direction)
                        .swipedAt(swipe.getCreatedAt())
                        .build();
            }

            log.info("[SwipeService] Got profile: name={}, age={}", profile.getName(), profile.getAge());

            return SwipedUserDto.builder()
                    .userId(targetUserId)
                    .name(profile.getName())
                    .age(profile.getAge())
                    .city(profile.getCity())
                    .profilePhotoUrl(profile.getProfilePhotoUrl())
                    .direction(direction)
                    .swipedAt(swipe.getCreatedAt())
                    .build();

        } catch (Exception e) {
            log.error("[SwipeService] Error fetching profile for userId: {}, error: {}",
                    targetUserId, e.getMessage());
            // [Fix] Exception pe bhi basic card return karo — empty list nahi
            return SwipedUserDto.builder()
                    .userId(targetUserId)
                    .name("User")
                    .direction(direction)
                    .swipedAt(swipe.getCreatedAt())
                    .build();
        }
    }

    /** Internal — returns all userIds that this userId has LIKED or SUPER_LIKED */
    public List<String> getLikedUserIds(String userId) {
        List<Swipe> likes = swipeRepository.findByFromUserIdAndAction(
                userId, SwipeAction.LIKE);
        List<Swipe> superLikes = swipeRepository.findByFromUserIdAndAction(
                userId, SwipeAction.SUPER_LIKE);
        return java.util.stream.Stream
                .concat(likes.stream(), superLikes.stream())
                .map(Swipe::getToUserId)
                .distinct()
                .collect(Collectors.toList());
    }

    public Map<String, Long> getUserLikesCount(String userId) {
        long likes    = swipeRepository.countByToUserIdAndAction(userId, SwipeAction.LIKE);
        long dislikes = swipeRepository.countByToUserIdAndAction(userId, SwipeAction.DISLIKE);
        return Map.of("likes", likes, "dislikes", dislikes);
    }

    // ── Profile Like Notification ──────────────────────────────────────────────

    private void sendProfileLikeNotification(String fromUserId, String toUserId) {
        try {
            // Fetch liker's name from user-service
            String likerUrl = userServiceUrl + "/api/users/" + fromUserId + "/profile";
            UserProfileDto likerProfile = restTemplate.getForObject(likerUrl, UserProfileDto.class);
            String likerName = (likerProfile != null && likerProfile.getName() != null)
                    ? likerProfile.getName() : "Someone";

            // Check if the liked user is on a paid plan
            boolean isPaid = isUserPaid(toUserId);

            String title = isPaid ? likerName + " likes you! 💘" : "Someone likes you! 💘";
            String body  = isPaid
                    ? "Tap to see their profile"
                    : "Upgrade to Premium to see who it is! 💎";

            streamPublisher.publish("notification.send", Map.of(
                    "userId", toUserId,
                    "type",   "PROFILE_LIKE",
                    "title",  title,
                    "body",   body,
                    "data",   Map.of("fromUserId", fromUserId)
            ));
        } catch (Exception e) {
            log.warn("Could not send profile like notification: {}", e.getMessage());
        }
    }

    private boolean isUserPaid(String userId) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-User-Id", userId);
            org.springframework.http.HttpEntity<Void> entity =
                    new org.springframework.http.HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> sub = restTemplate.exchange(
                    "http://subscription-service/api/subscriptions/my",
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    java.util.Map.class
            ).getBody();

            if (sub == null) return false;
            String plan = (String) sub.get("plan");
            return plan != null && !plan.equalsIgnoreCase("FREE");
        } catch (Exception e) {
            log.warn("Could not fetch subscription for user {}: {}", userId, e.getMessage());
            return false; // default to free if subscription-service unreachable
        }
    }
}