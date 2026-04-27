package com.rahul.adminservice.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Read-only JdbcTemplate connections to each microservice's PostgreSQL database.
 * The admin service's own database (for ad_impressions) is configured via
 * spring.datasource.* in application.yml and managed by Spring Data JPA.
 */
@Configuration
public class DataSourceConfig {

    @Value("${DB_USER}") private String dbUser;
    @Value("${DB_PASS}") private String dbPass;

    private DataSource readOnlyDs(String url) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(dbUser);
        cfg.setPassword(dbPass);
        cfg.setDriverClassName("org.postgresql.Driver");
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(0);
        cfg.setReadOnly(true);
        cfg.setConnectionTimeout(10_000);
        cfg.setIdleTimeout(300_000);
        return new HikariDataSource(cfg);
    }

    // ── User DB (user_profiles, user_photos, user_interests) ──────────────────

    @Bean("userJdbc")
    public JdbcTemplate userJdbc(@Value("${USER_DB_URL}") String url) {
        return new JdbcTemplate(readOnlyDs(url));
    }

    // ── Auth DB (auth_users) ──────────────────────────────────────────────────

    @Bean("authJdbc")
    public JdbcTemplate authJdbc(@Value("${AUTH_DB_URL}") String url) {
        return new JdbcTemplate(readOnlyDs(url));
    }

    // ── Subscription DB (user_subscriptions, subscription_plans) ─────────────

    @Bean("subJdbc")
    public JdbcTemplate subJdbc(@Value("${SUBSCRIPTION_DB_URL}") String url) {
        return new JdbcTemplate(readOnlyDs(url));
    }

    // ── Swipe DB (swipes) ─────────────────────────────────────────────────────

    @Bean("swipeJdbc")
    public JdbcTemplate swipeJdbc(@Value("${SWIPE_DB_URL}") String url) {
        return new JdbcTemplate(readOnlyDs(url));
    }

    // ── Match DB (matches) ────────────────────────────────────────────────────

    @Bean("matchJdbc")
    public JdbcTemplate matchJdbc(@Value("${MATCH_DB_URL}") String url) {
        return new JdbcTemplate(readOnlyDs(url));
    }

    // ── Mood DB (mood_statuses, mood_comments, mood_likes) ────────────────────

    @Bean("moodJdbc")
    public JdbcTemplate moodJdbc(@Value("${MOOD_DB_URL}") String url) {
        return new JdbcTemplate(readOnlyDs(url));
    }
}
