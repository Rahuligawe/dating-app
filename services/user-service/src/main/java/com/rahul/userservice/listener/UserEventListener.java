package com.rahul.userservice.listener;

import com.rahul.userservice.entity.UserProfile;
import com.rahul.userservice.repository.UserProfileRepository;
import com.rahul.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final UserProfileRepository userProfileRepository;
    private final UserService userService;

    // When auth creates a new user → create empty profile here
    @KafkaListener(topics = "user.created", groupId = "user-service")
    public void onUserCreated(Map<String, String> event) {
        String userId = event.get("userId");
        log.info("New user created event received: {}", userId);

        if (!userProfileRepository.existsById(userId)) {
            userProfileRepository.save(
                    UserProfile.builder()
                            .id(userId)
                            .isActive(true)
                            .isProfileComplete(false)
                            .isVerified(false)
                            .subscriptionType(UserProfile.SubscriptionType.FREE)
                            .build()
            );
            log.info("Empty profile created for user: {}", userId);
        }
    }

    // When subscription is upgraded → update plan on profile
    @KafkaListener(topics = "subscription.updated", groupId = "user-service")
    public void onSubscriptionUpdated(Map<String, String> event) {
        String userId = event.get("userId");
        String plan   = event.get("plan");
        log.info("Subscription updated for user {}: {}", userId, plan);
        userService.updateSubscription(userId, plan);
    }
}