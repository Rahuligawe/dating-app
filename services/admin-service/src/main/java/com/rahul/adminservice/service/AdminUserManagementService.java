package com.rahul.adminservice.service;

import com.rahul.adminservice.entity.AdminSession;
import com.rahul.adminservice.entity.AdminUser;
import com.rahul.adminservice.repository.AdminSessionRepository;
import com.rahul.adminservice.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserManagementService {

    private final AdminUserRepository    adminUserRepo;
    private final AdminSessionRepository sessionRepo;
    private final AdminAuthService       authService;

    // ── Create Admin User ─────────────────────────────────────────────────────

    @Transactional
    public AdminUser createAdmin(String username, String password, String displayName,
                                 String permissionsJson, String createdBy) {
        if (adminUserRepo.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }
        if (password == null || password.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters.");
        }
        String perms = (permissionsJson != null && !permissionsJson.isBlank())
                ? permissionsJson : AdminAuthService.defaultPermissionsJson();

        return adminUserRepo.save(AdminUser.builder()
                .username(username)
                .passwordHash(authService.encodePassword(password))
                .displayName(displayName)
                .permissionsJson(perms)
                .createdBy(createdBy)
                .build());
    }

    // ── List All Admin Users ──────────────────────────────────────────────────

    public List<Map<String, Object>> listAdmins() {
        return adminUserRepo.findAll().stream().map(u -> {
            long activeSessions = sessionRepo
                    .findByAdminUserIdAndIsRevokedFalseOrderByLoginAtDesc(u.getId()).size();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           u.getId());
            m.put("username",     u.getUsername());
            m.put("displayName",  u.getDisplayName());
            m.put("isActive",     u.isActive());
            m.put("isLocked",     u.isLocked());
            m.put("lastLoginAt",  u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null);
            m.put("createdAt",    u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
            m.put("createdBy",    u.getCreatedBy());
            m.put("activeSessions", activeSessions);
            m.put("permissionsJson", u.getPermissionsJson());
            return m;
        }).collect(Collectors.toList());
    }

    // ── Update Permissions ────────────────────────────────────────────────────

    @Transactional
    public void updatePermissions(String adminUserId, String permissionsJson) {
        AdminUser user = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        user.setPermissionsJson(permissionsJson);
        adminUserRepo.save(user);
        // Revoke all sessions — permissions changed, must re-login
        authService.revokeAllSessions(adminUserId);
    }

    // ── Lock / Unlock ─────────────────────────────────────────────────────────

    @Transactional
    public void lockAccount(String adminUserId, String reason) {
        AdminUser user = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        user.setLocked(true);
        user.setLockedUntil(null); // permanent lock until super admin unlocks
        adminUserRepo.save(user);
        authService.revokeAllSessions(adminUserId);
        log.info("Admin {} locked. Reason: {}", adminUserId, reason);
    }

    @Transactional
    public void unlockAccount(String adminUserId) {
        AdminUser user = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        user.setLocked(false);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        adminUserRepo.save(user);
    }

    // ── Deactivate / Activate ─────────────────────────────────────────────────

    @Transactional
    public void deactivateAdmin(String adminUserId) {
        AdminUser user = adminUserRepo.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        user.setActive(false);
        adminUserRepo.save(user);
        authService.revokeAllSessions(adminUserId);
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getSessionsForAdmin(String adminUserId) {
        return sessionRepo.findByAdminUserIdAndIsRevokedFalseOrderByLoginAtDesc(adminUserId)
                .stream().map(this::sessionToMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAllActiveSessions() {
        return sessionRepo.findByIsRevokedFalseOrderByLoginAtDesc()
                .stream().map(s -> {
                    Map<String, Object> m = sessionToMap(s);
                    adminUserRepo.findById(s.getAdminUserId()).ifPresent(u -> {
                        m.put("adminDisplayName", u.getDisplayName());
                        m.put("adminUsername", u.getUsername());
                    });
                    return m;
                }).collect(Collectors.toList());
    }

    // ── Force Logout specific session ─────────────────────────────────────────

    public void revokeSession(String sessionId) {
        authService.revokeSession(sessionId);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Map<String, Object> sessionToMap(AdminSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           s.getId());
        m.put("adminUserId",  s.getAdminUserId());
        m.put("ipAddress",    s.getIpAddress());
        m.put("deviceInfo",   s.getDeviceInfo());
        m.put("userAgent",    s.getUserAgent());
        m.put("loginAt",      s.getLoginAt() != null ? s.getLoginAt().toString() : null);
        m.put("lastActiveAt", s.getLastActiveAt() != null ? s.getLastActiveAt().toString() : null);
        return m;
    }
}
