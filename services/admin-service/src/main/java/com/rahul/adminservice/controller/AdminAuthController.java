package com.rahul.adminservice.controller;

import com.rahul.adminservice.service.AdminAuthService;
import com.rahul.adminservice.service.AdminUserManagementService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminAuthController {

    private final AdminAuthService            authService;
    private final AdminUserManagementService  mgmtService;

    // ── Login (username + password) ───────────────────────────────────────────

    @PostMapping("/api/admin/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String username   = body.get("username");
        String password   = body.get("password");
        String ipAddress  = req.getHeader("X-Real-IP");
        if (ipAddress == null) ipAddress = req.getRemoteAddr();
        String userAgent  = req.getHeader("User-Agent");

        try {
            Map<String, Object> result = authService.login(username, password, ipAddress, userAgent);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest req) {
        String token = extractToken(req);
        if (token != null) authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // ── Get current admin info ────────────────────────────────────────────────

    @GetMapping("/api/admin/auth/me")
    public ResponseEntity<?> me(HttpServletRequest req) {
        String token = extractToken(req);
        if (token == null) return ResponseEntity.status(401).body(Map.of("error", "No token"));
        try {
            Claims claims = authService.parseClaims(token);
            return ResponseEntity.ok(Map.of(
                    "adminId",    claims.getSubject(),
                    "name",       claims.get("name", String.class),
                    "username",   claims.get("username", String.class),
                    "permissions",claims.get("permissions", String.class)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }

    // ── My sessions ───────────────────────────────────────────────────────────

    @GetMapping("/api/admin/auth/sessions")
    public ResponseEntity<?> mySessions(HttpServletRequest req) {
        String adminId = extractSubAdminId(req);
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        return ResponseEntity.ok(mgmtService.getSessionsForAdmin(adminId));
    }

    // ── Change own password ───────────────────────────────────────────────────

    @PutMapping("/api/admin/auth/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String adminId = extractSubAdminId(req);
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            authService.changeOwnPassword(adminId, body.get("currentPassword"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Password changed. Please log in again."));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }

    private String extractSubAdminId(HttpServletRequest req) {
        String token = extractToken(req);
        if (token == null) return null;
        try {
            Claims claims = authService.parseClaims(token);
            return claims.get("sub_admin_id", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
