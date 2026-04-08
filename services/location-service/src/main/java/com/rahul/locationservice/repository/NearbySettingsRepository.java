package com.rahul.locationservice.repository;

import com.rahul.locationservice.entity.NearbySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NearbySettingsRepository
        extends JpaRepository<NearbySettings, String> {

    Optional<NearbySettings> findByUserId(String userId);
}