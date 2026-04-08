package com.rahul.moodservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoodServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MoodServiceApplication.class, args);
    }
}