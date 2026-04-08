package com.rahul.authservice.controller;

import com.rahul.authservice.dto.AuthDtos.*;
import com.rahul.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/mobile/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody MobileLoginRequest request) {
        String message = authService.sendOtp(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/mobile/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/email/signup")
    public ResponseEntity<AuthResponse> emailSignup(@Valid @RequestBody EmailSignupRequest request) {
        return ResponseEntity.ok(authService.emailSignup(request));
    }

    @PostMapping("/email/login")
    public ResponseEntity<AuthResponse> emailLogin(@Valid @RequestBody EmailLoginRequest request) {
        return ResponseEntity.ok(authService.emailLogin(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "auth-service"));
    }
}
