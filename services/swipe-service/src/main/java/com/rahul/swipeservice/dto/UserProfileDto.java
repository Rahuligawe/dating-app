package com.rahul.swipeservice.dto;

import lombok.Data;

// [Fix] User-service se response map karne ke liye
// user-service ke UserProfile entity fields ke saath match karna chahiye
@Data
public class UserProfileDto {
    private String id;
    private String name;
    private Integer age;
    private String city;
    private String bio;
    private String profilePhotoUrl;
    private String gender;
}