package com.rahul.swipeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwipedUserDto {
    private String userId;
    private String name;
    private Integer age;
    private String city;
    private String profilePhotoUrl;
    private String direction;   // "LIKE" or "DISLIKE"
    private LocalDateTime swipedAt;
}