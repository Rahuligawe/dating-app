package com.rahul.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

public class AuthDtos {

    @Data
    public static class MobileLoginRequest {
        @NotBlank
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
        private String mobile;
    }

    @Data
    public static class OtpVerifyRequest {
        @NotBlank
        private String mobile;
        @NotBlank
        @Pattern(regexp = "^\\d{6}$")
        private String otp;
    }

    @Data
    public static class EmailSignupRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String password;
        @NotBlank
        private String name;
    }

    @Data
    public static class EmailLoginRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class GoogleLoginRequest {
        @NotBlank
        private String idToken;
    }

    @Data
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String userId;
        private Boolean isNewUser;
        private String tokenType = "Bearer";

        public AuthResponse(String accessToken, String refreshToken, String userId, Boolean isNewUser) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.isNewUser = isNewUser;
        }
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
    }
}
