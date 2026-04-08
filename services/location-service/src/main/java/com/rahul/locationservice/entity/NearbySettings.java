package com.rahul.locationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nearby_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NearbySettings {

    @Id
    private String userId;

    private Boolean enabled = true;

    private Integer distanceKm = 15;

    private LocalDateTime updatedAt;
}