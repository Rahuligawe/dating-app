package com.rahul.notificationservice.repository;

import com.rahul.notificationservice.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository
        extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findAllByUserId(String userId);

    Optional<DeviceToken> findByToken(String token);

    Optional<DeviceToken> findByUserId(String userId);
}