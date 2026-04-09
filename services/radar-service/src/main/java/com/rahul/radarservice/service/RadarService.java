package com.rahul.radarservice.service;

import com.rahul.radarservice.dto.RadarDtos.*;
import com.rahul.radarservice.entity.*;
import com.rahul.radarservice.entity.RadarMeetRequest.MeetStatus;
import com.rahul.radarservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.rahul.radarservice.stream.StreamPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RadarService {

    private final RadarRepository           radarRepository;
    private final RadarMeetRequestRepository meetRequestRepository;
    private final StreamPublisher streamPublisher;

    // ─── Go Live ──────────────────────────────────────────────

    @Transactional
    public RadarPost goLive(String userId, GoLiveRequest request) {

        // Deactivate previous radar
        radarRepository.deactivateForUser(userId);

        int minutes = request.getDurationMinutes() != null
                ? request.getDurationMinutes() : 60;

        RadarPost post = radarRepository.save(RadarPost.builder()
                .userId(userId)
                .mood(request.getMood())
                .customDescription(request.getCustomDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .distanceRangeKm(request.getDistanceRangeKm() != null
                        ? request.getDistanceRangeKm() : 10)
                .expiryTime(LocalDateTime.now().plusMinutes(minutes))
                .isActive(true)
                .build());

        log.info("Radar live: userId={} mood={} expires={}", userId, post.getMood(), post.getExpiryTime());

        log.info("User {} went live on radar | mood: {} | expires: {}",
                userId, post.getMood(), post.getExpiryTime());

        return post;
    }

    // ─── Stop Live ────────────────────────────────────────────

    @Transactional
    public void stopLive(String userId) {
        radarRepository.deactivateForUser(userId);
        log.info("User {} stopped radar live", userId);
    }

    // ─── Get Nearby Radar Posts ───────────────────────────────

    public List<RadarPost> getNearbyRadar(String userId,
                                          double lat,
                                          double lng,
                                          double radiusKm) {
        return radarRepository
                .findActiveRadarPosts(userId, LocalDateTime.now())
                .stream()
                .filter(p -> haversine(lat, lng,
                        p.getLatitude(), p.getLongitude()) <= radiusKm)
                .toList();
    }

    // ─── Send Meet Request ────────────────────────────────────

    @Transactional
    public Map<String, Object> sendMeetRequest(String radarId,
                                               String fromUserId) {
        RadarPost post = radarRepository.findById(radarId)
                .orElseThrow(() ->
                        new RuntimeException("Radar post not found"));

        if (post.getUserId().equals(fromUserId)) {
            throw new RuntimeException("Cannot send meet request to yourself");
        }

        if (!post.getIsActive()) {
            throw new RuntimeException("This radar post has expired");
        }

        if (meetRequestRepository.existsByRadarIdAndFromUserId(
                radarId, fromUserId)) {
            return Map.of("message", "Meet request already sent");
        }

        RadarMeetRequest meetRequest = meetRequestRepository.save(
                RadarMeetRequest.builder()
                        .radarId(radarId)
                        .fromUserId(fromUserId)
                        .toUserId(post.getUserId())
                        .status(MeetStatus.PENDING)
                        .build());

        // Notify radar post owner
        streamPublisher.publish("notification.send", Map.of(
                "userId", post.getUserId(),
                "type",   "MEET_REQUEST",
                "title",  "Someone wants to meet! ⚡",
                "body",   "Someone nearby sent you a meet request",
                "data",   Map.of(
                        "radarId",       radarId,
                        "fromUserId",    fromUserId,
                        "meetRequestId", meetRequest.getId().toString())
        ));

        return Map.of(
                "message",       "Meet request sent successfully",
                "meetRequestId", meetRequest.getId()
        );
    }

    // ─── Respond to Meet Request ──────────────────────────────

    @Transactional
    public Map<String, String> respondToMeetRequest(
            String userId,
            RespondMeetRequest request) {

        RadarMeetRequest meetReq = meetRequestRepository
                .findByIdAndStatus(
                        request.getMeetRequestId(),
                        MeetStatus.PENDING)
                .orElseThrow(() ->
                        new RuntimeException("Meet request not found"));

        if (!meetReq.getToUserId().equals(userId)) {
            throw new RuntimeException("Not authorized");
        }

        meetReq.setStatus(request.getAccept()
                ? MeetStatus.ACCEPTED
                : MeetStatus.REJECTED);

        meetRequestRepository.save(meetReq);

        if (request.getAccept()) {
            streamPublisher.publish("notification.send", Map.of(
                    "userId", meetReq.getFromUserId(),
                    "type",   "MEET_ACCEPTED",
                    "title",  "Meet Request Accepted! 🎉",
                    "body",   "Your meet request was accepted!",
                    "data",   Map.of(
                            "radarId", meetReq.getRadarId(),
                            "userId",  userId)
            ));
        }

        return Map.of("message", request.getAccept()
                ? "Meet request accepted" : "Meet request rejected");
    }

    // ─── Get Pending Meet Requests ────────────────────────────

    public List<RadarMeetRequest> getPendingMeetRequests(
            String radarId) {
        return meetRequestRepository
                .findByRadarIdAndStatus(radarId, MeetStatus.PENDING);
    }

    // ─── Expire Radar Posts every 1 minute ───────────────────

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireRadarPosts() {
        radarRepository.expireOldPosts(LocalDateTime.now());
    }

    // ─── Haversine Distance ───────────────────────────────────

    private double haversine(double lat1, double lng1,
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