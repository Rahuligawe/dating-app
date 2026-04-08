package com.rahul.radarservice.controller;

import com.rahul.radarservice.dto.RadarDtos.*;
import com.rahul.radarservice.entity.*;
import com.rahul.radarservice.service.RadarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/radar")
@RequiredArgsConstructor
public class RadarController {

    private final RadarService radarService;

    @PostMapping("/live")
    public ResponseEntity<RadarPost> goLive(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody GoLiveRequest request) {
        return ResponseEntity.ok(radarService.goLive(userId, request));
    }

    @DeleteMapping("/live")
    public ResponseEntity<Map<String, String>> stopLive(
            @RequestHeader("X-User-Id") String userId) {
        radarService.stopLive(userId);
        return ResponseEntity.ok(Map.of("message", "Stopped live"));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<RadarPost>> getNearbyRadar(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radius) {
        return ResponseEntity.ok(
                radarService.getNearbyRadar(userId, lat, lng, radius));
    }

    @PostMapping("/{radarId}/meet")
    public ResponseEntity<Map<String, Object>> sendMeetRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String radarId) {
        return ResponseEntity.ok(
                radarService.sendMeetRequest(radarId, userId));
    }

    @PostMapping("/meet/respond")
    public ResponseEntity<Map<String, String>> respondToMeetRequest(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody RespondMeetRequest request) {
        return ResponseEntity.ok(
                radarService.respondToMeetRequest(userId, request));
    }

    @GetMapping("/{radarId}/meet/pending")
    public ResponseEntity<List<RadarMeetRequest>> getPendingRequests(
            @PathVariable String radarId) {
        return ResponseEntity.ok(
                radarService.getPendingMeetRequests(radarId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of("status", "UP", "service", "radar-service"));
    }
}