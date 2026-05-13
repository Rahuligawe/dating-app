package com.rahul.adminservice.controller;

import com.rahul.adminservice.service.AdminAuthService;
import com.rahul.adminservice.service.AdminUserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Super-admin-only endpoints for managing read-only admin accounts.
 * Requires X-User-Role: ADMIN (set by gateway for OTP super-admin logins).
 * Sub-admins with canManageAdmins permission can also call these.
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminManagementController {

    private final AdminUserManagementService mgmtService;
    private final AdminAuthService           authService;

    private void requireSuperOrManageAdmins(HttpServletRequest req) {
        String role = req.getHeader("X-User-Role");
        if ("ADMIN".equals(role)) {
            // Check if sub-admin with canManageAdmins permission
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    var claims = authService.parseClaims(auth.substring(7));
                    String subAdminId = claims.get("sub_admin_id", String.class);
                    if (subAdminId != null) {
                        // Sub-admin — check canManageAdmins
                        String perms = claims.get("permissions", String.class);
                        if (perms == null || !perms.contains("\"canManageAdmins\":true")) {
                            throw new RuntimeException("Insufficient permissions.");
                        }
                    }
                    // If no sub_admin_id → super admin → OK
                    return;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception ignored) {}
            }
            return; // X-User-Role: ADMIN without sub_admin_id → super admin
        }
        throw new RuntimeException("Access Denied");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException e) {
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }

    // ── List all admin users ──────────────────────────────────────────────────

    @GetMapping("/api/admin/admins")
    public ResponseEntity<List<Map<String, Object>>> listAdmins(HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        return ResponseEntity.ok(mgmtService.listAdmins());
    }

    // ── Create admin user ─────────────────────────────────────────────────────

    @PostMapping("/api/admin/admins")
    public ResponseEntity<?> createAdmin(@RequestBody Map<String, String> body, HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        try {
            String createdBy = "super-admin";
            var user = mgmtService.createAdmin(
                    body.get("username"), body.get("password"),
                    body.get("displayName"), body.get("permissionsJson"), createdBy);
            return ResponseEntity.ok(Map.of(
                    "id",          user.getId(),
                    "username",    user.getUsername(),
                    "displayName", user.getDisplayName(),
                    "message",     "Admin user created successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Update permissions ────────────────────────────────────────────────────

    @PutMapping("/api/admin/admins/{adminId}/permissions")
    public ResponseEntity<?> updatePermissions(
            @PathVariable String adminId,
            @RequestBody Map<String, String> body,
            HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        try {
            mgmtService.updatePermissions(adminId, body.get("permissionsJson"));
            return ResponseEntity.ok(Map.of("message", "Permissions updated. User must re-login."));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Lock / Unlock ─────────────────────────────────────────────────────────

    @PutMapping("/api/admin/admins/{adminId}/lock")
    public ResponseEntity<?> lockAccount(
            @PathVariable String adminId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        mgmtService.lockAccount(adminId, reason);
        return ResponseEntity.ok(Map.of("message", "Account locked."));
    }

    @PutMapping("/api/admin/admins/{adminId}/unlock")
    public ResponseEntity<?> unlockAccount(@PathVariable String adminId, HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        mgmtService.unlockAccount(adminId);
        return ResponseEntity.ok(Map.of("message", "Account unlocked."));
    }

    // ── Deactivate ────────────────────────────────────────────────────────────

    @DeleteMapping("/api/admin/admins/{adminId}")
    public ResponseEntity<?> deactivateAdmin(@PathVariable String adminId, HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        mgmtService.deactivateAdmin(adminId);
        return ResponseEntity.ok(Map.of("message", "Admin deactivated."));
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @PutMapping("/api/admin/admins/{adminId}/reset-password")
    public ResponseEntity<?> resetPassword(
            @PathVariable String adminId,
            @RequestBody Map<String, String> body,
            HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        try {
            authService.resetPassword(adminId, body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Password reset. User must log in again."));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/admins/sessions")
    public ResponseEntity<?> allSessions(HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        return ResponseEntity.ok(mgmtService.getAllActiveSessions());
    }

    @GetMapping("/api/admin/admins/{adminId}/sessions")
    public ResponseEntity<?> adminSessions(@PathVariable String adminId, HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        return ResponseEntity.ok(mgmtService.getSessionsForAdmin(adminId));
    }

    @DeleteMapping("/api/admin/admins/sessions/{sessionId}")
    public ResponseEntity<?> revokeSession(@PathVariable String sessionId, HttpServletRequest req) {
        requireSuperOrManageAdmins(req);
        mgmtService.revokeSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session revoked."));
    }
}
