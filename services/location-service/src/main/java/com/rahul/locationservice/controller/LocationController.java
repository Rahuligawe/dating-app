package com.rahul.locationservice.controller;

import com.rahul.locationservice.dto.LocationDtos.*;
import com.rahul.locationservice.entity.NearbySettings;
import com.rahul.locationservice.entity.UserLocation;
import com.rahul.locationservice.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PutMapping("/me")
    public ResponseEntity<Map<String, String>> updateLocation(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateLocationRequest request) {
        locationService.updateLocation(
                userId, request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(
                Map.of("message", "Location updated successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserLocation> getMyLocation(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(locationService.getLocation(userId));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<UserLocation>> getNearbyUsers(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "15") double radiusKm) {
        return ResponseEntity.ok(
                locationService.getNearbyUsers(userId, radiusKm));
    }

    @PutMapping("/me/nearby-settings")
    public ResponseEntity<NearbySettings> updateNearbySettings(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody NearbySettingsRequest request) {
        return ResponseEntity.ok(locationService.updateNearbySettings(
                userId,
                request.getEnabled(),
                request.getDistanceKm()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of("status", "UP", "service", "location-service"));
    }
}