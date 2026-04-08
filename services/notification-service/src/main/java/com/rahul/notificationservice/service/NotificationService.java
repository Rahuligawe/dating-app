package com.rahul.notificationservice.service;

import com.google.firebase.messaging.*;
import com.rahul.notificationservice.entity.DeviceToken;
import com.rahul.notificationservice.entity.NotificationLog;
import com.rahul.notificationservice.repository.DeviceTokenRepository;
import com.rahul.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final FirebaseMessaging          firebaseMessaging;
    private final DeviceTokenRepository      deviceTokenRepository;
    private final NotificationLogRepository  notificationLogRepository;

    // ─── Register Device Token ────────────────────────────────

    @Transactional
    public void registerToken(String userId, String token,
                              String platform) {
        // If token already exists update userId
        deviceTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> {
                    existing.setUserId(userId);
                    deviceTokenRepository.save(existing);
                },
                () -> deviceTokenRepository.save(
                        DeviceToken.builder()
                                .userId(userId)
                                .token(token)
                                .platform(DeviceToken.Platform.valueOf(
                                        platform.toUpperCase()))
                                .build()
                )
        );
        log.info("Device token registered for user: {}", userId);
    }

    // ─── Send Push Notification ───────────────────────────────

    public void sendPushNotification(String userId,
                                     String title,
                                     String body,
                                     String type,
                                     Map<String, String> data) {

        List<DeviceToken> tokens =
                deviceTokenRepository.findAllByUserId(userId);

        if (tokens.isEmpty()) {
            log.debug("No device tokens for user: {}", userId);
            saveNotificationLog(userId, type, title, body);
            return;
        }

        for (DeviceToken deviceToken : tokens) {
            try {
                Message.Builder builder = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("type", type);

                // Add extra data if present
                if (data != null) {
                    data.forEach(builder::putData);
                }

                // Android high priority
                builder.setAndroidConfig(
                        AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build());

                String response = firebaseMessaging.send(builder.build());
                log.debug("FCM sent to user {}: {}", userId, response);

            } catch (FirebaseMessagingException e) {
                log.error("FCM failed for user {}: {}",
                        userId, e.getMessage());

                // Remove invalid token
                if (e.getMessagingErrorCode() ==
                        MessagingErrorCode.UNREGISTERED) {
                    deviceTokenRepository.delete(deviceToken);
                    log.info("Removed invalid token for user: {}", userId);
                }
            }
        }

        saveNotificationLog(userId, type, title, body);
    }

    // ─── Get Notifications ────────────────────────────────────

    public List<NotificationLog> getNotifications(String userId) {
        return notificationLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ─── Unread Count ─────────────────────────────────────────

    public long getUnreadCount(String userId) {
        return notificationLogRepository
                .countByUserIdAndIsReadFalse(userId);
    }

    // ─── Mark All Read ────────────────────────────────────────

    @Transactional
    public void markAllAsRead(String userId) {
        notificationLogRepository.markAllAsRead(userId);
    }

    // ─── Helper ───────────────────────────────────────────────

    private void saveNotificationLog(String userId, String type,
                                     String title, String body) {
        notificationLogRepository.save(NotificationLog.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .build());
    }
}