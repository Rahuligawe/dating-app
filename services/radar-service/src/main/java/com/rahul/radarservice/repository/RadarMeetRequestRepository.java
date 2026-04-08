package com.rahul.radarservice.repository;

import com.rahul.radarservice.entity.RadarMeetRequest;
import com.rahul.radarservice.entity.RadarMeetRequest.MeetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RadarMeetRequestRepository
        extends JpaRepository<RadarMeetRequest, Long> {

    boolean existsByRadarIdAndFromUserId(
            String radarId, String fromUserId);

    List<RadarMeetRequest> findByRadarIdAndStatus(
            String radarId, MeetStatus status);

    Optional<RadarMeetRequest> findByIdAndStatus(
            Long id, MeetStatus status);
}