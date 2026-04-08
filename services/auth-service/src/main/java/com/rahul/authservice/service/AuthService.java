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

import java.time.LocalDateTime;
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

    @Value("${app.otp.expiry-minutes}")
    private int otpExpiryMinutes;

    // ─── Mobile OTP Flow ───────────────────────────────────────────────

    @Transactional
    public String sendOtp(MobileLoginRequest request) {
        String mobile = request.getMobile();

        // Rate limiting via Redis
        String rateLimitKey = "otp:rate:" + mobile;
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, 1, TimeUnit.HOURS);
        }
        if (count != null && count > 5) {
            throw new AuthException("Too many OTP requests. Try after 1 hour.");
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

        // Send via SMS (Kafka event → SMS service)
        kafkaTemplate.send("otp.send", mobile, otp);
        log.info("OTP sent to mobile: {}", mobile);

        // In DEV mode return otp directly, in PROD remove this
        return "OTP sent successfully. [DEV: " + otp + "]";
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
            kafkaTemplate.send("user.created", user.getId(), buildUserCreatedEvent(user));
        }

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

        kafkaTemplate.send("user.created", user.getId(), buildUserCreatedEvent(user));
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

        return buildAuthResponse(user, false);
    }

    // ─── Helpers ───────────────────────────────────────────────────────

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
