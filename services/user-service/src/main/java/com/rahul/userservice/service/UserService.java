package com.rahul.userservice.service;

import com.rahul.userservice.dto.UserDtos.*;
import com.rahul.userservice.entity.UserProfile;
import com.rahul.userservice.exception.UserException;
import com.rahul.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final PhotoUploadService photoUploadService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;

    // Redis key prefix for online status
    private static final String ONLINE_KEY = "presence:";

    // ─── Get Profile ──────────────────────────────────────────────────────────

    public UserProfileResponse getProfile(String userId) {
        return userProfileRepository.findById(userId)
                .map(this::toResponse)
                .orElseGet(() -> toResponse(createEmptyProfile(userId)));
    }

    @Transactional
    public UserProfile createEmptyProfile(String userId) {
        // Idempotent — already exists to return existing, nahi to create karo
        return userProfileRepository.findById(userId).orElseGet(() ->
                userProfileRepository.save(
                        UserProfile.builder()
                                .id(userId)
                                .isActive(true)
                                .isProfileComplete(false)
                                .isVerified(false)
                                .subscriptionType(UserProfile.SubscriptionType.FREE)
                                .build()
                )
        );
    }

    // ─── Online Status — Event Based ──────────────────────────────────────────

    /**
     * App FOREGROUND — user online mark karo
     * Android: MainActivity.onResume() se call hoga
     * Redis mein key set karo with 5 min TTL (safety net)
     */
    public void markOnline(String userId) {
        try {
            redisTemplate.opsForValue().set(
                    ONLINE_KEY + userId,
                    "1",
                    5, TimeUnit.MINUTES   // safety TTL — agar app crash ho to auto-offline
            );
        } catch (Exception e) {
            log.warn("Redis set failed for markOnline (non-fatal): {}", e.getMessage());
        }
        publishStatus(userId, true);
        log.debug("User online: {}", userId);
    }

    /**
     * App BACKGROUND/CLOSE — user offline mark karo
     * Android: MainActivity.onStop() se call hoga
     * Redis se key delete karo
     */
    public void markOffline(String userId) {
        try {
            redisTemplate.delete(ONLINE_KEY + userId);
        } catch (Exception e) {
            log.warn("Redis delete failed for markOffline (non-fatal): {}", e.getMessage());
        }
        publishStatus(userId, false);
        log.debug("User offline: {}", userId);
    }

    private void publishStatus(String userId, boolean online) {
        try {
            String json = "{\"userId\":\"" + userId + "\",\"online\":" + online + "}";
            stringRedisTemplate.convertAndSend("status." + userId, json);
        } catch (Exception e) {
            log.warn("Redis status publish failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Kisi user ka online status check karo
     * Android: ChatsFragment mein dusre user ka status check karne ke liye
     * Redis mein key exist kare = online, nahi = offline
     */
    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ONLINE_KEY + userId));
    }

    // ─── Create / Update Profile ──────────────────────────────────────────────

    @Transactional
    public UserProfileResponse createOrUpdateProfile(String userId,
                                                     UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElse(UserProfile.builder().id(userId).build());

        if (request.getName() != null)
            profile.setName(request.getName());

        if (request.getCity() != null)
            profile.setCity(request.getCity());

        // Age is always calculated from dateOfBirth — never set directly
        if (request.getDateOfBirth() != null) {
            profile.setDateOfBirth(request.getDateOfBirth());
            int calculatedAge = LocalDate.now().getYear() - request.getDateOfBirth().getYear();
            profile.setAge(calculatedAge);
        }

        if (request.getGender() != null)
            profile.setGender(request.getGender());

        if (request.getBio() != null)
            profile.setBio(request.getBio());

        if (request.getInterests() != null)
            profile.setInterests(request.getInterests());

        if (request.getGenderPreference() != null)
            profile.setGenderPreference(request.getGenderPreference());

        if (request.getLookingFor() != null)
            profile.setLookingFor(request.getLookingFor());

        // Partner age preference (minAgePreference / maxAgePreference)
        if (request.getMinAgePreference() != null)
            profile.setMinAgePreference(request.getMinAgePreference());

        if (request.getMaxAgePreference() != null)
            profile.setMaxAgePreference(request.getMaxAgePreference());

        if (request.getMaxDistanceKm() != null)
            profile.setMaxDistanceKm(request.getMaxDistanceKm());

        profile.setIsProfileComplete(isProfileComplete(profile));

        UserProfile saved = userProfileRepository.save(profile);

        try {
            kafkaTemplate.send("user.profile.updated", userId, Map.of(
                    "userId", userId,
                    "gender", saved.getGender() != null ? saved.getGender().name() : "",
                    "genderPreference", saved.getGenderPreference() != null
                            ? saved.getGenderPreference().name() : "",
                    "maxDistanceKm", saved.getMaxDistanceKm() != null ? saved.getMaxDistanceKm() : 50,
                    "locationLat", saved.getLocationLat() != null ? saved.getLocationLat() : 0.0,
                    "locationLong", saved.getLocationLong() != null ? saved.getLocationLong() : 0.0
            ));
        } catch (Exception e) {
            log.warn("Kafka send failed (non-fatal): {}", e.getMessage());
        }

        return toResponse(saved);
    }

    // ─── Photo Upload ─────────────────────────────────────────────────────────

    @Transactional
    public String uploadPhoto(String userId, MultipartFile file) {
        UserProfile profile = findUser(userId);

        if (profile.getPhotos() != null
                && profile.getPhotos().size() >= getMaxPhotos(profile)) {
            throw new UserException("Photo limit reached for your plan");
        }

        String photoUrl = photoUploadService.upload(userId, file);
        List<String> photos = profile.getPhotos() != null
                ? new java.util.ArrayList<>(profile.getPhotos())
                : new java.util.ArrayList<>();
        photos.add(photoUrl);
        profile.setPhotos(photos);
        userProfileRepository.save(profile);

        return photoUrl;
    }

    @Transactional
    public void deletePhoto(String userId, String photoUrl) {
        UserProfile profile = findUser(userId);
        List<String> photos = new java.util.ArrayList<>(profile.getPhotos());
        photos.remove(photoUrl);
        profile.setPhotos(photos);
        userProfileRepository.save(profile);
        photoUploadService.delete(photoUrl);
    }

    // ─── Set Profile Photo ────────────────────────────────────────────────────
    // Moves the given photoUrl to position 0 in the photos list.
    // profilePhotoUrl is always derived from photos.get(0) in toResponse().
    @Transactional
    public void setProfilePhoto(String userId, String photoUrl) {
        UserProfile profile = findUser(userId);
        List<String> photos = new java.util.ArrayList<>(
                profile.getPhotos() != null ? profile.getPhotos() : new java.util.ArrayList<>());

        if (!photos.contains(photoUrl)) {
            throw new com.rahul.userservice.exception.UserException(
                    "Photo not found in your profile");
        }

        photos.remove(photoUrl);   // remove from current position
        photos.add(0, photoUrl);   // put at front → becomes profilePhotoUrl
        profile.setPhotos(photos);
        userProfileRepository.save(profile);
    }

    // ─── Discover Feed ────────────────────────────────────────────────────────

    public List<UserProfileResponse> getDiscoverProfiles(String userId, int page, int size) {
        UserProfile me = userProfileRepository.findById(userId).orElse(null);
        if (me == null || me.getGenderPreference() == null) return List.of();

        UserProfile.Gender pref = switch (me.getGenderPreference()) {
            case MALE -> UserProfile.Gender.MALE;
            case FEMALE -> UserProfile.Gender.FEMALE;
            case BOTH -> null;
        };

        int minAge = (me.getMinAgePreference() != null) ? me.getMinAgePreference() : 18;
        int maxAge = (me.getMaxAgePreference() != null) ? me.getMaxAgePreference() : 99; // [Fix] 99 max

        return userProfileRepository.findDiscoverProfiles(
                userId,
                pref,
                minAge,
                maxAge,
                PageRequest.of(page, size)
        ).stream()
                .map(this::toResponse)
                .toList();
    }

    // ─── Nearby Settings ──────────────────────────────────────────────────────

    public NearbyAlertSettings updateNearbyAlertSettings(String userId,
                                                         NearbyAlertSettings settings) {
        try {
            kafkaTemplate.send("user.nearby.settings", userId, settings);
        } catch (Exception e) {
            log.warn("Kafka send failed (non-fatal): {}", e.getMessage());
        }
        return settings;
    }

    // ─── Delete Account ───────────────────────────────────────────────────────

    @Transactional
    public void deleteAccount(String userId) {
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        if (profile == null) {
            log.warn("deleteAccount called for non-existent user: {}", userId);
            return;
        }

        // ── Step 1: Oracle Object Storage — all photos delete karo ───────────
        if (profile.getPhotos() != null) {
            for (String photoUrl : profile.getPhotos()) {
                try { photoUploadService.delete(photoUrl); }
                catch (Exception e) {
                    log.warn("Photo delete failed ({}): {}", photoUrl, e.getMessage());
                }
            }
        }
        if (profile.getVerificationPhotoUrl() != null) {
            try { photoUploadService.delete(profile.getVerificationPhotoUrl()); }
            catch (Exception e) {
                log.warn("VerificationPhoto delete failed: {}", e.getMessage());
            }
        }

        // ── Step 2: Redis presence clear karo ────────────────────────────────
        try { redisTemplate.delete(ONLINE_KEY + userId); }
        catch (Exception e) {
            log.warn("Redis delete failed (non-fatal): {}", e.getMessage());
        }

        // ── Step 3: Supabase — sab related tables se data delete karo ────────
        // Shared PostgreSQL DB hai, isliye direct SQL se sab clean karo.
        // Order important hai — FK violations se bachne ke liye leaf tables pehle.

        deleteFromAllTables(userId);

        // ── Step 4: user_profiles + @ElementCollection tables (JPA cascade) ──
        // user_photos, user_interests, user_looking_for — JPA automatically delete karta hai
        userProfileRepository.deleteById(userId);

        // ── Step 5: Kafka event — future listeners ke liye ───────────────────
        try {
            kafkaTemplate.send("user.deleted", userId, Map.of("userId", userId));
        } catch (Exception e) {
            log.warn("Kafka user.deleted send failed (non-fatal): {}", e.getMessage());
        }

        log.info("Account fully deleted — userId: {}", userId);
    }

    private void deleteFromAllTables(String userId) {
        // Mood data — user ke moods pe jo comments/likes/dislikes hain wo pehle
        safeDelete("DELETE FROM mood_comments WHERE mood_id IN (SELECT id FROM mood_status WHERE user_id = ?)", userId);
        safeDelete("DELETE FROM mood_likes    WHERE mood_id IN (SELECT id FROM mood_status WHERE user_id = ?)", userId);
        safeDelete("DELETE FROM mood_dislikes WHERE mood_id IN (SELECT id FROM mood_status WHERE user_id = ?)", userId);

        // User ne dusron ke moods pe jo reactions kiye
        safeDelete("DELETE FROM mood_comments WHERE user_id = ?", userId);
        safeDelete("DELETE FROM mood_likes    WHERE user_id = ?", userId);
        safeDelete("DELETE FROM mood_dislikes WHERE user_id = ?", userId);

        // User ke apne moods
        safeDelete("DELETE FROM mood_status WHERE user_id = ?", userId);

        // Swipes — user ne kiye aur user pe kiye gaye dono
        safeDelete("DELETE FROM swipes WHERE from_user_id = ? OR to_user_id = ?", userId, userId);

        // Matches
        safeDelete("DELETE FROM matches WHERE user1_id = ? OR user2_id = ?", userId, userId);

        // Subscription data
        safeDelete("DELETE FROM referral_usages    WHERE owner_user_id = ? OR buyer_user_id = ?", userId, userId);
        safeDelete("DELETE FROM referral_codes     WHERE owner_user_id = ?", userId);
        safeDelete("DELETE FROM points_transactions WHERE user_id = ?", userId);
        safeDelete("DELETE FROM points_wallets     WHERE user_id = ?", userId);
        safeDelete("DELETE FROM user_subscriptions WHERE user_id = ?", userId);

        // Device & notification data
        safeDelete("DELETE FROM device_tokens      WHERE user_id = ?", userId);
        safeDelete("DELETE FROM notification_logs  WHERE user_id = ?", userId);

        // Location / nearby
        safeDelete("DELETE FROM user_locations   WHERE user_id = ?", userId);
        safeDelete("DELETE FROM nearby_settings  WHERE user_id = ?", userId);

        // Auth account
        safeDelete("DELETE FROM auth_users WHERE id = ?", userId);
    }

    private void safeDelete(String sql, Object... args) {
        try {
            int rows = jdbcTemplate.update(sql, args);
            log.debug("Deleted {} row(s): {}", rows, sql.substring(0, Math.min(60, sql.length())));
        } catch (Exception e) {
            log.warn("safeDelete failed (non-fatal) — SQL: {} | Error: {}", sql, e.getMessage());
        }
    }

    // ─── Update Subscription (called from Kafka listener) ────────────────────

    @Transactional
    public void updateSubscription(String userId, String plan) {
        userProfileRepository.findById(userId).ifPresent(profile -> {
            profile.setSubscriptionType(UserProfile.SubscriptionType.valueOf(plan));
            userProfileRepository.save(profile);
            log.info("Subscription updated for user {}: {}", userId, plan);
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UserProfile findUser(String userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserException("User not found: " + userId));
    }

    /*private boolean isProfileComplete(UserProfile p) {
        return p.getName() != null
                && p.getAge() != null
                && p.getGender() != null
                && p.getBio() != null
                && p.getPhotos() != null
                && !p.getPhotos().isEmpty();
    }*/

    // minimum requirement so max profiles can be seen
    private boolean isProfileComplete(UserProfile p) {
        return p.getName() != null
                && p.getAge() != null
                && p.getGender() != null;
    }

    private int getMaxPhotos(UserProfile p) {
        if (p.getSubscriptionType() == null) return 5;
        return switch (p.getSubscriptionType()) {
            case FREE     -> 5;
            case PREMIUM  -> 10;
            case ULTRA    -> 20;
        };
    }

    private UserProfileResponse toResponse(UserProfile p) {
        return UserProfileResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .age(p.getAge())
                .gender(p.getGender())
                .genderPreference(p.getGenderPreference())
                .bio(p.getBio())
                .photos(p.getPhotos())
                .interests(p.getInterests())
                .lookingFor(p.getLookingFor())
                .city(p.getCity())
                .minAgePreference(p.getMinAgePreference())
                .maxAgePreference(p.getMaxAgePreference())
                .maxDistanceKm(p.getMaxDistanceKm())
                .isVerified(p.getIsVerified())
                .subscriptionType(p.getSubscriptionType())
                .isProfileComplete(p.getIsProfileComplete())
                .profilePhotoUrl(p.getPhotos() != null && !p.getPhotos().isEmpty()
                        ? p.getPhotos().get(0) : null)
                .build();
    }
}