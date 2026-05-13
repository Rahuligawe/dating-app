package com.rahul.swipeservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SwipeException.class)
    public ResponseEntity<Map<String, String>> handleSwipeException(
            SwipeException ex) {
        // Return 429 for swipe limit so Android can detect it specifically
        boolean isLimitError = ex.getMessage() != null
                && ex.getMessage().contains("Daily swipe limit");
        HttpStatus status = isLimitError ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("error", ex.getMessage(), "code",
                        isLimitError ? "SWIPE_LIMIT_REACHED" : "SWIPE_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }
}