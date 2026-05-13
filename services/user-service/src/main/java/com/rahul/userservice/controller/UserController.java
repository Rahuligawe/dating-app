package com.rahul.userservice.controller;

import com.rahul.userservice.dto.UserDtos.*;
import com.rahul.userservice.entity.UserProfile;
import com.rahul.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/test-redis")
    public String testRedis() {
        redisTemplate.opsForValue().set("test", "Rahul");
        return redisTemplate.opsForValue().get("test");
    }

    // ─── Internal: called by auth-service after new user registration ────────
    // Secured with shared internal secret header (not JWT)
    @PostMapping("/internal/create")
    public ResponseEntity<Void> createUserProfile(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody Map<String, String> body) {
        if (!"auralink-internal-2024".equals(secret)) {
            return ResponseEntity.status(403).build();
        }
        String userId = body.get("userId");
        if (userId == null || userId.isBlank()) return ResponseEntity.badRequest().build();
        userService.createEmptyProfile(userId);
        return ResponseEntity.ok().build();
    }

    // ─── Internal: check if a user is blocked/active — called by auth-service ──
    @GetMapping("/internal/{userId}/status")
    public ResponseEntity<Map<String, Object>> getUserStatus(
            @PathVariable String userId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (!"auralink-internal-2024".equals(secret)) {
            return ResponseEntity.status(403).build();
        }
        return userService.getUserRepository().findById(userId)
                .map(p -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("isActive",  Boolean.TRUE.equals(p.getIsActive()));
                    m.put("isBlocked", Boolean.TRUE.equals(p.getIsBlocked()));
                    m.put("userId",    userId);
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.ok(Map.of("isActive", true, "isBlocked", false, "userId", userId)));
    }

    // userId comes from JWT via Gateway header
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.createOrUpdateProfile(userId, request));
    }

    // ─── Online Presence — Event Based ───────────────────────────────────────

    /**
     * App foreground mein aaya — online mark karo
     * Android MainActivity.onResume() se call hoga
     */
    @PostMapping("/me/online")
    public ResponseEntity<Void> markOnline(
            @RequestHeader("X-User-Id") String userId) {
        userService.markOnline(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * App background/close hua — offline mark karo
     * Android MainActivity.onStop() se call hoga
     */
    @PostMapping("/me/offline")
    public ResponseEntity<Void> markOffline(
            @RequestHeader("X-User-Id") String userId) {
        userService.markOffline(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Kisi user ka online status check karo
     * Android ChatsFragment mein dusre user ka dot color set karne ke liye
     */
    @GetMapping("/{userId}/online")
    public ResponseEntity<Map<String, Boolean>> isOnline(
            @PathVariable String userId) {
        return ResponseEntity.ok(Map.of("online", userService.isOnline(userId)));
    }

    @PostMapping("/me/photos")
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file) {
        String url = userService.uploadPhoto(userId, file);
        return ResponseEntity.ok(Map.of("photoUrl", url));
    }

    /**
     * Chat media upload — S3 pe upload karo under "chat/{userId}/" prefix.
     * Profile photos list mein add NAHI hota — ye sirf chat ke liye hai.
     */
    @PostMapping("/me/chat-upload")
    public ResponseEntity<Map<String, String>> uploadChatMedia(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file) {
        String url = userService.uploadChatMedia(userId, file);
        return ResponseEntity.ok(Map.of("photoUrl", url));
    }

    @DeleteMapping("/me/photos")
    public ResponseEntity<Void> deletePhoto(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String photoUrl) {
        userService.deletePhoto(userId, photoUrl);
        return ResponseEntity.noContent().build();
    }

    // Move the given photoUrl to position 0 → it becomes the profilePhotoUrl
    @PatchMapping("/me/profile-photo")
    public ResponseEntity<Void> setProfilePhoto(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String photoUrl = body.get("photoUrl");
        if (photoUrl == null || photoUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        userService.setProfilePhoto(userId, photoUrl);
        return ResponseEntity.ok().build();
    }

    // ─── Find User by Mobile — Konvo Talk peer search ─────────────────────────
    // Returns { userId, name, mobile, androidId, deviceName, deviceBrand, registeredOnKonvo }
    // registeredOnKonvo=false → Android shows "Send Invite" SMS option
    @GetMapping("/by-mobile")
    public ResponseEntity<KonvoUserResponse> getUserByMobile(
            @RequestParam String mobile) {
        return ResponseEntity.ok(userService.getUserByMobile(mobile));
    }

    @GetMapping("/discover")
    public ResponseEntity<List<UserProfileResponse>> getDiscoverProfiles(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getDiscoverProfiles(userId, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me/nearby-settings")
    public ResponseEntity<NearbyAlertSettings> updateNearbySettings(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody NearbyAlertSettings settings) {
        return ResponseEntity.ok(userService.updateNearbyAlertSettings(userId, settings));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    // ─── Delete Account ───────────────────────────────────────────────────────
    // Deletes: Oracle Object Storage photos + Redis presence + DB rows + Kafka event
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@RequestHeader("X-User-Id") String userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }
}
