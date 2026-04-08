package com.rahul.locationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_locations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserLocation {

    @Id
    private String userId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private LocalDateTime lastUpdated;
}