package com.rahul.locationservice.service;

import com.rahul.locationservice.entity.NearbySettings;
import com.rahul.locationservice.entity.UserLocation;
import com.rahul.locationservice.repository.NearbySettingsRepository;
import com.rahul.locationservice.repository.UserLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.rahul.locationservice.stream.StreamPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final UserLocationRepository     locationRepository;
    private final NearbySettingsRepository   nearbySettingsRepository;
    private final StreamPublisher streamPublisher;

    @Transactional
    public void updateLocation(String userId, double lat, double lng) {
        UserLocation location = locationRepository.findByUserId(userId)
                .orElse(UserLocation.builder().userId(userId).build());
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setLastUpdated(LocalDateTime.now());
        locationRepository.save(location);
    }

    public UserLocation getLocation(String userId) {
        return locationRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new RuntimeException("Location not found for user: " + userId));
    }

    @Transactional
    public NearbySettings updateNearbySettings(String userId,
                                                boolean enabled,
                                                int distanceKm) {
        NearbySettings settings = nearbySettingsRepository
                .findByUserId(userId)
                .orElse(NearbySettings.builder().userId(userId).build());
        settings.setEnabled(enabled);
        settings.setDistanceKm(distanceKm);
        settings.setUpdatedAt(LocalDateTime.now());
        return nearbySettingsRepository.save(settings);
    }

    public List<UserLocation> getNearbyUsers(String userId, double radiusKm) {
        UserLocation myLocation = locationRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new RuntimeException("Your location not found"));
        return locationRepository.findAll().stream()
                .filter(l -> !l.getUserId().equals(userId))
                .filter(l -> haversine(
                        myLocation.getLatitude(), myLocation.getLongitude(),
                        l.getLatitude(), l.getLongitude()) <= radiusKm)
                .toList();
    }

    @Scheduled(fixedDelayString = "${location.nearby-check-interval-ms:600000}")
    public void checkNearbyUsers() {
        List<UserLocation> allLocations = locationRepository.findAll();
        List<NearbySettings> allSettings = nearbySettingsRepository.findAll();

        for (NearbySettings settings : allSettings) {
            if (!settings.getEnabled()) continue;

            String userId = settings.getUserId();
            double alertDistanceKm = settings.getDistanceKm();

            UserLocation myLocation = allLocations.stream()
                    .filter(l -> l.getUserId().equals(userId))
                    .findFirst().orElse(null);

            if (myLocation == null) continue;

            for (UserLocation other : allLocations) {
                if (other.getUserId().equals(userId)) continue;

                double dist = haversine(
                        myLocation.getLatitude(), myLocation.getLongitude(),
                        other.getLatitude(), other.getLongitude());

                if (dist <= alertDistanceKm) {
                    streamPublisher.publish("location.nearby", Map.of(
                            "userId", userId,
                            "nearbyUserId", other.getUserId(),
                            "distanceKm", Math.round(dist * 10.0) / 10.0
                    ));
                }
            }
        }
        log.debug("Nearby check completed for {} users", allSettings.size());
    }

    public double haversine(double lat1, double lng1,
                             double lat2, double lng2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}