package com.rahul.locationservice.repository;

import com.rahul.locationservice.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserLocationRepository
        extends JpaRepository<UserLocation, String> {

    Optional<UserLocation> findByUserId(String userId);
}