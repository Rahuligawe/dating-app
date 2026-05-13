package com.rahul.adminservice.repository;

import com.rahul.adminservice.entity.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    Optional<AdminSession> findByTokenHash(String tokenHash);
    List<AdminSession> findByAdminUserIdAndIsRevokedFalseOrderByLoginAtDesc(String adminUserId);
    List<AdminSession> findByIsRevokedFalseOrderByLoginAtDesc();

    @Modifying @Transactional
    @Query("UPDATE AdminSession s SET s.isRevoked = true WHERE s.adminUserId = :adminUserId")
    void revokeAllByAdminUserId(String adminUserId);
}
