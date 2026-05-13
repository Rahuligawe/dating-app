package com.rahul.adminservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.adminservice.entity.AdminSession;
import com.rahul.adminservice.entity.AdminUser;
import com.rahul.adminservice.repository.AdminSessionRepository;
import com.rahul.adminservice.repository.AdminUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final AdminUserRepository   adminUserRepo;
    private final AdminSessionRepository sessionRepo;
    private final PasswordEncoder        passwordEncoder;
    private final StringRedisTemplate    redisTemplate;
    private final ObjectMapper           objectMapper;

    @Value("${admin.jwt.secret}")
    private String jwtSecret;

    @Value("${admin.jwt.expiry-hours:720}")
    private int expiryHours;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        // Ensure minimum 32 bytes for HMAC-SHA256
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── DEFAULT READ-ONLY PERMISSIONS ─────────────────────────────────────────

    public static String defaultPermissionsJson() {
        return """
            {"dashboard":{"viewStats":true,"viewRevenue":false,"viewCharts":true,"viewAds":false},\
            "users":{"view":true,"create":false,"delete":false,"viewChats":false,"managePoints":false},\
            "revenue":false,"canManageAdmins":false}""";
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> login(String username, String password,
                                     String ipAddress, String userAgent) {
        // Rate-limit by IP (10 attempts per 5 min)
        String ipKey = "admin:rate:" + ipAddress;
        String attempts = redisTemplate.opsForValue().get(ipKey);
        if (attempts != null && Integer.parseInt(attempts) >= 10) {
            throw new RuntimeException("Too many login attempts from this IP. Try again later.");
        }

        AdminUser user = adminUserRepo.findByUsername(username)
                .orElseThrow(() -> {
                    incrementIpRate(ipKey);
                    return new RuntimeException("Invalid username or password.");
                });

        // Check account status
        if (!user.isActive()) throw new RuntimeException("Account is deactivated.");
        if (user.isLocked()) {
            if (user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil())) {
                throw new RuntimeException("Account locked until " + user.getLockedUntil() + ". Contact super admin.");
            }
            // Auto-unlock if time has passed
            user.setLocked(false);
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        }

        // Validate password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            incrementIpRate(ipKey);
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= 5) {
                user.setLocked(true);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                adminUserRepo.save(user);
                throw new RuntimeException("Too many failed attempts. Account locked for 15 minutes.");
            }
            adminUserRepo.save(user);
            throw new RuntimeException("Invalid username or password. Attempt " + user.getFailedAttempts() + "/5.");
        }

        // Success — reset failed attempts
        user.setFailedAttempts(0);
        user.setLocked(false);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        adminUserRepo.save(user);

        // Generate JWT
        String token = generateToken(user);
        String tokenHash = sha256(token);

        // Device info from UA
        String deviceInfo = parseDeviceInfo(userAgent);

        // Save session record
        sessionRepo.save(AdminSession.builder()
                .adminUserId(user.getId())
                .tokenHash(tokenHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceInfo(deviceInfo)
                .lastActiveAt(LocalDateTime.now())
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("adminId", user.getId());
        result.put("displayName", user.getDisplayName());
        result.put("username", user.getUsername());
        result.put("permissions", user.getPermissionsJson());
        return result;
    }

    // ── TOKEN VALIDATION ──────────────────────────────────────────────────────

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRevoked(String token) {
        String hash = sha256(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey("admin:revoked:" + hash));
    }

    public void updateLastActive(String adminUserId, String token) {
        try {
            String hash = sha256(token);
            sessionRepo.findByTokenHash(hash).ifPresent(s -> {
                s.setLastActiveAt(LocalDateTime.now());
                sessionRepo.save(s);
            });
        } catch (Exception e) {
            log.debug("updateLastActive: {}", e.getMessage());
        }
    }

    // ── LOGOUT / REVOKE ───────────────────────────────────────────────────────

    @Transactional
    public void logout(String token) {
        revokeToken(token);
    }

    @Transactional
    public void revokeSession(String sessionId) {
        sessionRepo.findById(sessionId).ifPresent(s -> {
            s.setRevoked(true);
            sessionRepo.save(s);
            // Blacklist in Redis (TTL: expiryHours)
            redisTemplate.opsForValue().set("admin:revoked:" + s.getTokenHash(), "1",
                    expiryHours, TimeUnit.HOURS);
        });
    }

    @Transactional
    public void revokeAllSessions(String adminUserId) {
        sessionRepo.revokeAllByAdminUserId(adminUserId);
        // Can't blacklist all in Redis easily — sessions marked in DB
        // On next request, DB check will catch it
        redisTemplate.opsForValue().set("admin:revoke_all:" + adminUserId, "1",
                expiryHours, TimeUnit.HOURS);
    }

    public boolean isAdminRevoked(String adminUserId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("admin:revoke_all:" + adminUserId));
    }

    // ── PASSWORD RESET ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(String adminUserId, String newPassword) {
        AdminUser user = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLocked(false);
        user.setLockedUntil(null);
        adminUserRepo.save(user);
        // Revoke all existing sessions on password reset
        revokeAllSessions(adminUserId);
    }

    @Transactional
    public void changeOwnPassword(String adminUserId, String currentPassword, String newPassword) {
        AdminUser user = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserRepo.save(user);
        revokeAllSessions(adminUserId);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    String generateToken(AdminUser user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + (long) expiryHours * 3600 * 1000);
        return Jwts.builder()
                .subject(user.getId())
                .claim("role", "ADMIN")
                .claim("sub_admin_id", user.getId())
                .claim("name", user.getDisplayName())
                .claim("username", user.getUsername())
                .claim("permissions", user.getPermissionsJson())
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey)
                .compact();
    }

    public String encodePassword(String raw) {
        return passwordEncoder.encode(raw);
    }

    private void incrementIpRate(String ipKey) {
        redisTemplate.opsForValue().increment(ipKey);
        redisTemplate.expire(ipKey, 5, TimeUnit.MINUTES);
    }

    private void revokeToken(String token) {
        try {
            String hash = sha256(token);
            sessionRepo.findByTokenHash(hash).ifPresent(s -> {
                s.setRevoked(true);
                sessionRepo.save(s);
            });
            redisTemplate.opsForValue().set("admin:revoked:" + hash, "1", expiryHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("revokeToken error: {}", e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input.substring(0, Math.min(64, input.length()));
        }
    }

    private String parseDeviceInfo(String userAgent) {
        if (userAgent == null) return "Unknown";
        if (userAgent.contains("Mobile") || userAgent.contains("Android")) return "Mobile";
        if (userAgent.contains("Tablet") || userAgent.contains("iPad")) return "Tablet";
        return "Desktop";
    }
}
