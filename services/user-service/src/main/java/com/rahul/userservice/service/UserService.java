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

    // Redis key prefix for online status
    private static final String ONLINE_KEY = "presence:";

    // ─── Get Profile ──────────────────────────────────────────────────────────

    public UserProfileResponse getProfile(String userId) {
        return toResponse(findUser(userId));
    }

    // ─── Online Status — Event Based ──────────────────────────────────────────

    /**
     * App FOREGROUND — user online mark karo
     * Android: MainActivity.onResume() se call hoga
     * Redis mein key set karo with 5 min TTL (safety net)
     */
    public void markOnline(String userId) {
        redisTemplate.opsForValue().set(
                ONLINE_KEY + userId,
                "1",
                5, TimeUnit.MINUTES   // safety TTL — agar app crash ho to auto-offline
        );
        publishStatus(userId, true);
        log.debug("User online: {}", userId);
    }

    /**
     * App BACKGROUND/CLOSE — user offline mark karo
     * Android: MainActivity.onStop() se call hoga
     * Redis se key delete karo
     */
    public void markOffline(String userId) {
        redisTemplate.delete(ONLINE_KEY + userId);
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

        kafkaTemplate.send("user.profile.updated", userId, Map.of(
                "userId", userId,
                "gender", saved.getGender() != null ? saved.getGender().name() : "",
                "genderPreference", saved.getGenderPreference() != null
                        ? saved.getGenderPreference().name() : "",
                "maxDistanceKm", saved.getMaxDistanceKm() != null ? saved.getMaxDistanceKm() : 50,
                "locationLat", saved.getLocationLat() != null ? saved.getLocationLat() : 0.0,
                "locationLong", saved.getLocationLong() != null ? saved.getLocationLong() : 0.0
        ));

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
        UserProfile me = findUser(userId);

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
        kafkaTemplate.send("user.nearby.settings", userId, settings);
        return settings;
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