package com.rahul.authservice.repository;

import com.rahul.authservice.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, String> {
    Optional<AuthUser> findByMobile(String mobile);
    Optional<AuthUser> findByEmail(String email);
    boolean existsByMobile(String mobile);
    boolean existsByEmail(String email);
}
