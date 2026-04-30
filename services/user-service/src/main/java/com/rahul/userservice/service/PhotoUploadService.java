package com.rahul.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class PhotoUploadService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.endpoint}")
    private String endpoint;

    public PhotoUploadService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Upload for chat messages — stored under "chat/{userId}/" prefix.
     * This does NOT touch the user's profile photos list.
     */
    public String uploadForChat(String userId, MultipartFile file) {
        try {
            String key = "chat/" + userId + "/"
                    + UUID.randomUUID()
                    + getExtension(file.getOriginalFilename());

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .acl(ObjectCannedACL.PUBLIC_READ)
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );

            String publicUrl = buildPublicUrl(key);
            log.info("Chat media uploaded for user {}: {}", userId, publicUrl);
            return publicUrl;

        } catch (IOException e) {
            log.error("Chat media upload failed for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload chat media: " + e.getMessage());
        }
    }

    public String upload(String userId, MultipartFile file) {
        try {
            String key = "users/" + userId + "/"
                    + UUID.randomUUID()
                    + getExtension(file.getOriginalFilename());

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .acl(ObjectCannedACL.PUBLIC_READ)
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );

            String publicUrl = buildPublicUrl(key);
            log.info("Photo uploaded for user {}: {}", userId, publicUrl);
            return publicUrl;

        } catch (IOException e) {
            log.error("Photo upload failed for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload photo: " + e.getMessage());
        }
    }

    public void delete(String photoUrl) {
        try {
            String key = photoUrl.substring(photoUrl.indexOf("/o/") + 3);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("Photo deleted: {}", key);
        } catch (Exception e) {
            log.error("Photo delete failed for URL {}: {}", photoUrl, e.getMessage());
        }
    }

    /**
     * Oracle Object Storage public URL construct karo
     * Endpoint: https://{namespace}.compat.objectstorage.{region}.oraclecloud.com
     * Public URL: https://objectstorage.{region}.oraclecloud.com/n/{namespace}/b/{bucket}/o/{key}
     */
    private String buildPublicUrl(String key) {
        String withoutHttps = endpoint.replace("https://", "");
        String namespace = withoutHttps.split("\\.")[0];
        String regionPart = withoutHttps.split("objectstorage\\.")[1].split("\\.oraclecloud")[0];

        return "https://objectstorage." + regionPart + ".oraclecloud.com"
                + "/n/" + namespace
                + "/b/" + bucket
                + "/o/" + key;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}