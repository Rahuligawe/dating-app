package com.rahul.authservice.service;

import com.rahul.authservice.dto.AuthDtos.*;
import com.rahul.authservice.entity.AuthUser;
import com.rahul.authservice.entity.OtpCode;
import com.rahul.authservice.exception.AuthException;
import com.rahul.authservice.repository.AuthUserRepository;
import com.rahul.authservice.repository.OtpRepository;
import com.rahul.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final OtpRepository otpRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.otp.expiry-minutes}")
    private int otpExpiryMinutes;

    @Value("${app.user-service.url:https://creative-art-production.up.railway.app}")
    private String userServiceUrl;

    @Value("${sms.fast2sms.api-key:}")
    private String fast2smsApiKey;

    @Value("${sms.twofactor.api-key:}")
    private String twoFactorApiKey;

    @Value("${sms.msg91.auth-key:}")
    private String msg91AuthKey;

    @Value("${sms.msg91.template-id:}")
    private String msg91TemplateId;

    // ─── Mobile OTP Flow ───────────────────────────────────────────────

    @Transactional
    public String sendOtp(MobileLoginRequest request) {
        String mobile = request.getMobile();

        // Rate limiting via Redis (non-fatal if Redis is unavailable)
        String rateLimitKey = "otp:rate:" + mobile;
        try {
            Long count = redisTemplate.opsForValue().increment(rateLimitKey);
            if (count != null && count == 1) {
                redisTemplate.expire(rateLimitKey, 1, TimeUnit.HOURS);
            }
            if (count != null && count > 5) {
                throw new AuthException("Too many OTP requests. Try after 1 hour.");
            }
        } catch (AuthException e) {
            throw e; // re-throw rate limit exception as-is
        } catch (Exception e) {
            log.warn("Redis rate-limit check failed (non-fatal, skipping): {}", e.getMessage());
        }

        // Generate OTP
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Invalidate previous OTPs
        otpRepository.invalidateAllForMobile(mobile);

        // Save new OTP
        otpRepository.save(OtpCode.builder()
                .mobile(mobile)
                .code(otp)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .build());

        // Send OTP via SMS — priority: MSG91 → Fast2SMS → 2Factor (Voice)
        if (msg91AuthKey != null && !msg91AuthKey.isBlank()) {
            sendSmsViaMsg91(mobile, otp);
        } else if (fast2smsApiKey != null && !fast2smsApiKey.isBlank()) {
            sendSmsViaFast2SMS(mobile, otp);
        } else {
            sendSmsViaTwoFactor(mobile, otp);
        }

        return "OTP sent successfully";
    }

    /**
     * Sends OTP via Fast2SMS Quick route (requires ₹100+ recharge on account).
     * Dashboard: https://www.fast2sms.com → Dev API → API Key → set FAST2SMS_API_KEY env var.
     */
    private void sendSmsViaFast2SMS(String mobile, String otp) {
        try {
            String number = mobile.replaceAll("^\\+?91", ""); // Fast2SMS needs 10-digit number
            String message = "Your AuraLink OTP is " + otp + ". Valid for " + otpExpiryMinutes + " minutes. Do not share. -AuraLink";
            String url = "https://www.fast2sms.com/dev/bulkV2"
                    + "?authorization=" + fast2smsApiKey
                    + "&route=q"
                    + "&numbers=" + number
                    + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8)
                    + "&flash=0";
            var response = restTemplate.getForEntity(url, String.class);
            log.info("Fast2SMS response for {}: {}", mobile, response.getBody());
        } catch (Exception e) {
            log.error("Failed to send Fast2SMS OTP to {}: {}", mobile, e.getMessage());
        }
    }

    /**
     * Sends OTP via MSG91 (primary — reliable DLT-compliant delivery in India).
     * Sign up: https://msg91.com → free 100 SMS on signup → API → Auth Key.
     * Also create an OTP template and copy its Template ID.
     */
    private void sendSmsViaMsg91(String mobile, String otp) {
        try {
            String number = "91" + mobile.replaceAll("^\\+?91", ""); // MSG91 needs 91XXXXXXXXXX
            String url = "https://control.msg91.com/api/v5/otp"
                    + "?template_id=" + msg91TemplateId
                    + "&mobile=" + number
                    + "&authkey=" + msg91AuthKey
                    + "&otp=" + otp;
            var response = restTemplate.getForEntity(url, String.class);
            log.info("MSG91 SMS response for {}: {}", mobile, response.getBody());
        } catch (Exception e) {
            log.error("Failed to send MSG91 SMS OTP to {}: {}", mobile, e.getMessage());
        }
    }

    /**
     * Fallback: Sends OTP via 2Factor.in VOICE call (free tier — no DLT registration needed).
     * 2Factor calls the user's phone and speaks the OTP aloud.
     * Sign up free at https://2factor.in → My Account → API Key → set TWO_FACTOR_API_KEY env var.
     */
    private void sendSmsViaTwoFactor(String mobile, String otp) {
        if (twoFactorApiKey == null || twoFactorApiKey.isBlank()) {
            log.warn("No SMS provider configured — OTP for {}: {}", mobile, otp);
            return;
        }
        try {
            String number = mobile.replaceAll("^\\+?91", "");
            // Using VOICE OTP — bypasses DLT/carrier SMS blocking on free accounts
            String url = "https://2factor.in/API/V1/" + twoFactorApiKey + "/VOICE/" + number + "/" + otp;
            var response = restTemplate.getForEntity(url, String.class);
            log.info("2Factor VOICE OTP response for {}: {}", mobile, response.getBody());
        } catch (Exception e) {
            log.error("Failed to send 2Factor VOICE OTP to {}: {}", mobile, e.getMessage());
        }
    }

    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        OtpCode otpCode = otpRepository
                .findByMobileAndCodeAndUsedFalse(request.getMobile(), request.getOtp())
                .orElseThrow(() -> new AuthException("Invalid OTP"));

        if (otpCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("OTP expired");
        }

        // Mark OTP used
        otpRepository.invalidateAllForMobile(request.getMobile());

        // Find or create user
        boolean isNew = !authUserRepository.existsByMobile(request.getMobile());
        AuthUser user = authUserRepository.findByMobile(request.getMobile())
                .orElseGet(() -> authUserRepository.save(AuthUser.builder()
                        .mobile(request.getMobile())
                        .provider(AuthUser.AuthProvider.MOBILE)
                        .isVerified(true)
                        .build()));

        if (isNew) {
            // Async Kafka event (works when all services are in same network)
            try { kafkaTemplate.send("user.created", user.getId(), buildUserCreatedEvent(user)); }
            catch (Exception e) { log.warn("Kafka user.created failed (non-fatal): {}", e.getMessage()); }

            // Direct HTTP call — works even when Kafka is unavailable (e.g., Railway deployment)
            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("X-Internal-Secret", "auralink-internal-2024");
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                var entity = new org.springframework.http.HttpEntity<>(
                        Map.of("userId", user.getId()), headers);
                restTemplate.postForEntity(userServiceUrl + "/api/users/internal/create", entity, Void.class);
                log.info("User profile created via HTTP for userId: {}", user.getId());
            } catch (Exception e) {
                log.warn("User profile HTTP create failed (non-fatal, will auto-create on first login): {}",
                        e.getMessage());
            }
        }

        // Check if user is blocked by admin
        checkNotBlocked(user.getId());

        return buildAuthResponse(user, isNew);
    }

    // ─── Email Flow ────────────────────────────────────────────────────

    @Transactional
    public AuthResponse emailSignup(EmailSignupRequest request) {
        if (authUserRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email already registered");
        }

        AuthUser user = authUserRepository.save(AuthUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(AuthUser.AuthProvider.EMAIL)
                .isVerified(false)
                .build());

        try { kafkaTemplate.send("user.created", user.getId(), buildUserCreatedEvent(user)); }
        catch (Exception e) { log.warn("Kafka user.created failed (non-fatal): {}", e.getMessage()); }
        return buildAuthResponse(user, true);
    }

    public AuthResponse emailLogin(EmailLoginRequest request) {
        AuthUser user = authUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException("Invalid credentials");
        }

        return buildAuthResponse(user, false);
    }

    // ─── Token Refresh ─────────────────────────────────────────────────

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtService.isTokenValid(request.getRefreshToken())) {
            throw new AuthException("Invalid refresh token");
        }

        String userId = jwtService.extractUserId(request.getRefreshToken());
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        // Check if user is blocked by admin
        checkNotBlocked(userId);

        return buildAuthResponse(user, false);
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private void checkNotBlocked(String userId) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-Internal-Secret", "auralink-internal-2024");
            var entity = new org.springframework.http.HttpEntity<>(null, headers);
            var resp = restTemplate.exchange(
                    userServiceUrl + "/api/users/internal/" + userId + "/status",
                    org.springframework.http.HttpMethod.GET, entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            if (resp.getBody() != null) {
                Object blocked = resp.getBody().get("isBlocked");
                if (Boolean.TRUE.equals(blocked)) {
                    throw new AuthException("ACCOUNT_SUSPENDED: Your account has been suspended due to a complaint received. Once a decision has been taken, your account will be unblocked. You can contact the admin to raise an unblock request.");
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            // Non-fatal — if user-service is unavailable, allow login
            log.warn("Could not check user block status for {}: {}", userId, e.getMessage());
        }
    }

    private AuthResponse buildAuthResponse(AuthUser user, boolean isNew) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        return new AuthResponse(accessToken, refreshToken, user.getId(), isNew);
    }

    private java.util.Map<String, Object> buildUserCreatedEvent(AuthUser user) {
        return java.util.Map.of(
                "userId", user.getId(),
                "mobile", user.getMobile() != null ? user.getMobile() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "provider", user.getProvider().name()
        );
    }
}
