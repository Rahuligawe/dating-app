-- ============================================================
-- DATING APP - COMPLETE PostgreSQL SCHEMA
-- All microservices databases
-- ============================================================

-- ─────────────────────────────────────────
-- 1. AUTH SERVICE DATABASE: dating_auth
-- ─────────────────────────────────────────
\c dating_auth;

CREATE TABLE auth_users (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    mobile      VARCHAR(15) UNIQUE,
    email       VARCHAR(255) UNIQUE,
    password    VARCHAR(255),
    provider    VARCHAR(20) NOT NULL CHECK (provider IN ('EMAIL', 'MOBILE', 'GOOGLE')),
    role        VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE otp_codes (
    id         BIGSERIAL PRIMARY KEY,
    mobile     VARCHAR(15) NOT NULL,
    code       CHAR(6) NOT NULL,
    is_used    BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_otp_mobile ON otp_codes(mobile);
CREATE INDEX idx_auth_mobile ON auth_users(mobile);
CREATE INDEX idx_auth_email ON auth_users(email);


-- ─────────────────────────────────────────
-- 2. USER SERVICE DATABASE: dating_users
-- ─────────────────────────────────────────
\c dating_users;

CREATE TABLE user_profiles (
    id                   VARCHAR(36) PRIMARY KEY,
    name                 VARCHAR(100),
    date_of_birth        DATE,
    age                  SMALLINT,
    gender               VARCHAR(10) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    bio                  VARCHAR(500),
    location_lat         DECIMAL(10,8),
    location_long        DECIMAL(11,8),
    city                 VARCHAR(100),
    gender_preference    VARCHAR(10) CHECK (gender_preference IN ('MALE','FEMALE','BOTH')),
    max_distance_km      SMALLINT DEFAULT 50,
    min_age_preference   SMALLINT DEFAULT 18,
    max_age_preference   SMALLINT DEFAULT 45,
    subscription_type    VARCHAR(10) DEFAULT 'FREE' CHECK (subscription_type IN ('FREE','PREMIUM','PLATINUM','ULTRA')),
    is_verified          BOOLEAN DEFAULT FALSE,
    verification_photo   VARCHAR(500),
    is_active            BOOLEAN DEFAULT TRUE,
    is_profile_complete  BOOLEAN DEFAULT FALSE,
    created_at           TIMESTAMP DEFAULT NOW(),
    updated_at           TIMESTAMP DEFAULT NOW()
);

CREATE TABLE user_photos (
    id        BIGSERIAL PRIMARY KEY,
    user_id   VARCHAR(36) NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    photo_url VARCHAR(500) NOT NULL,
    sort_order SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE user_interests (
    id       BIGSERIAL PRIMARY KEY,
    user_id  VARCHAR(36) NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    interest VARCHAR(100) NOT NULL
);

CREATE TABLE user_looking_for (
    id                VARCHAR(36) NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    relationship_type VARCHAR(30) NOT NULL CHECK (relationship_type IN (
        'FRIENDSHIP','DATING','LONG_TERM','MARRIAGE','CASUAL','PARTY_PARTNER','TRAVEL_PARTNER'
    ))
);

CREATE INDEX idx_users_gender ON user_profiles(gender);
CREATE INDEX idx_users_active ON user_profiles(is_active);
CREATE INDEX idx_users_age ON user_profiles(age);
CREATE INDEX idx_users_location ON user_profiles(location_lat, location_long);
CREATE INDEX idx_user_photos_user ON user_photos(user_id);


-- ─────────────────────────────────────────
-- 3. SWIPE SERVICE DATABASE: dating_swipes
-- ─────────────────────────────────────────
\c dating_swipes;

CREATE TABLE swipes (
    id           BIGSERIAL PRIMARY KEY,
    from_user_id VARCHAR(36) NOT NULL,
    to_user_id   VARCHAR(36) NOT NULL,
    action       VARCHAR(15) NOT NULL CHECK (action IN ('LIKE','DISLIKE','SUPER_LIKE')),
    created_at   TIMESTAMP DEFAULT NOW(),
    UNIQUE (from_user_id, to_user_id)
);

CREATE INDEX idx_swipe_from ON swipes(from_user_id);
CREATE INDEX idx_swipe_to ON swipes(to_user_id);
CREATE INDEX idx_swipe_action ON swipes(action);


-- ─────────────────────────────────────────
-- 4. MATCH SERVICE DATABASE: dating_matches
-- ─────────────────────────────────────────
\c dating_matches;

CREATE TABLE matches (
    id         VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user1_id   VARCHAR(36) NOT NULL,
    user2_id   VARCHAR(36) NOT NULL,
    is_active  BOOLEAN DEFAULT TRUE,
    matched_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user1_id, user2_id)
);

CREATE INDEX idx_match_user1 ON matches(user1_id);
CREATE INDEX idx_match_user2 ON matches(user2_id);


-- ─────────────────────────────────────────
-- 5. EVENT SERVICE DATABASE: dating_events
-- ─────────────────────────────────────────
\c dating_events;

CREATE TABLE user_events (
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id       VARCHAR(36) NOT NULL,
    event_type    VARCHAR(20) NOT NULL CHECK (event_type IN ('TRAVEL','POOJA','PARTY','MEETUP','BIRTHDAY','CUSTOM')),
    title         VARCHAR(200) NOT NULL,
    description   VARCHAR(1000),
    location_name VARCHAR(200),
    latitude      DECIMAL(10,8),
    longitude     DECIMAL(11,8),
    event_date    DATE NOT NULL,
    visibility    VARCHAR(15) DEFAULT 'MATCHES' CHECK (visibility IN ('MATCHES','FRIENDS','PUBLIC')),
    like_count    INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE event_likes (
    id         BIGSERIAL PRIMARY KEY,
    event_id   VARCHAR(36) NOT NULL REFERENCES user_events(id) ON DELETE CASCADE,
    user_id    VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(event_id, user_id)
);

CREATE TABLE event_comments (
    id         BIGSERIAL PRIMARY KEY,
    event_id   VARCHAR(36) NOT NULL REFERENCES user_events(id) ON DELETE CASCADE,
    user_id    VARCHAR(36) NOT NULL,
    comment    VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_event_user ON user_events(user_id);
CREATE INDEX idx_event_date ON user_events(event_date);
CREATE INDEX idx_event_type ON user_events(event_type);


-- ─────────────────────────────────────────
-- 6. LOCATION SERVICE DATABASE: dating_location
-- ─────────────────────────────────────────
\c dating_location;

CREATE TABLE user_locations (
    user_id      VARCHAR(36) PRIMARY KEY,
    latitude     DECIMAL(10,8) NOT NULL,
    longitude    DECIMAL(11,8) NOT NULL,
    last_updated TIMESTAMP DEFAULT NOW()
);

CREATE TABLE nearby_settings (
    user_id     VARCHAR(36) PRIMARY KEY,
    enabled     BOOLEAN DEFAULT TRUE,
    distance_km SMALLINT DEFAULT 15,
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_location_coords ON user_locations(latitude, longitude);


-- ─────────────────────────────────────────
-- 7. MOOD SERVICE DATABASE: dating_mood
-- ─────────────────────────────────────────
\c dating_mood;

CREATE TABLE mood_status (
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id           VARCHAR(36) NOT NULL,
    mood_type         VARCHAR(20) NOT NULL,
    description       VARCHAR(300),
    latitude          DECIMAL(10,8),
    longitude         DECIMAL(11,8),
    location_name     VARCHAR(200),
    distance_range_km SMALLINT DEFAULT 15,
    expiry_time       TIMESTAMP NOT NULL,
    is_active         BOOLEAN DEFAULT TRUE,
    like_count        INT DEFAULT 0,
    comment_count     INT DEFAULT 0,
    created_at        TIMESTAMP DEFAULT NOW()
);

CREATE TABLE mood_likes (
    id         BIGSERIAL PRIMARY KEY,
    mood_id    VARCHAR(36) NOT NULL REFERENCES mood_status(id) ON DELETE CASCADE,
    user_id    VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(mood_id, user_id)
);

CREATE TABLE mood_comments (
    id         BIGSERIAL PRIMARY KEY,
    mood_id    VARCHAR(36) NOT NULL REFERENCES mood_status(id) ON DELETE CASCADE,
    user_id    VARCHAR(36) NOT NULL,
    comment    VARCHAR(300) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE mood_join_requests (
    id           BIGSERIAL PRIMARY KEY,
    mood_id      VARCHAR(36) NOT NULL REFERENCES mood_status(id) ON DELETE CASCADE,
    from_user_id VARCHAR(36) NOT NULL,
    status       VARCHAR(15) DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    created_at   TIMESTAMP DEFAULT NOW(),
    UNIQUE(mood_id, from_user_id)
);

CREATE INDEX idx_mood_user ON mood_status(user_id);
CREATE INDEX idx_mood_active ON mood_status(is_active, expiry_time);


-- ─────────────────────────────────────────
-- 8. RADAR SERVICE DATABASE: dating_radar
-- ─────────────────────────────────────────
\c dating_radar;

CREATE TABLE radar_posts (
    id                 VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id            VARCHAR(36) NOT NULL,
    mood               VARCHAR(20) NOT NULL CHECK (mood IN ('COFFEE','WALK','PARTY','DATE','FOOD','GYM','CUSTOM')),
    custom_description VARCHAR(200),
    latitude           DECIMAL(10,8) NOT NULL,
    longitude          DECIMAL(11,8) NOT NULL,
    distance_range_km  SMALLINT DEFAULT 10,
    expiry_time        TIMESTAMP NOT NULL,
    is_active          BOOLEAN DEFAULT TRUE,
    created_at         TIMESTAMP DEFAULT NOW()
);

CREATE TABLE radar_meet_requests (
    id           BIGSERIAL PRIMARY KEY,
    radar_id     VARCHAR(36) NOT NULL REFERENCES radar_posts(id) ON DELETE CASCADE,
    from_user_id VARCHAR(36) NOT NULL,
    status       VARCHAR(15) DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    created_at   TIMESTAMP DEFAULT NOW(),
    UNIQUE(radar_id, from_user_id)
);

CREATE INDEX idx_radar_user ON radar_posts(user_id);
CREATE INDEX idx_radar_active ON radar_posts(is_active, expiry_time);
CREATE INDEX idx_radar_location ON radar_posts(latitude, longitude);


-- ─────────────────────────────────────────
-- 9. NOTIFICATION SERVICE DATABASE: dating_notifications
-- ─────────────────────────────────────────
\c dating_notifications;

CREATE TABLE device_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL,
    token      VARCHAR(500) NOT NULL UNIQUE,
    platform   VARCHAR(10) NOT NULL CHECK (platform IN ('ANDROID','IOS')),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE notification_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL,
    type       VARCHAR(50) NOT NULL,
    title      VARCHAR(200),
    body       VARCHAR(500),
    is_read    BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_device_token_user ON device_tokens(user_id);
CREATE INDEX idx_notif_user ON notification_logs(user_id);


-- ─────────────────────────────────────────
-- 10. SUBSCRIPTION SERVICE DATABASE: dating_subscriptions
-- ─────────────────────────────────────────
\c dating_subscriptions;

CREATE TABLE user_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL UNIQUE,
    plan       VARCHAR(10) NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE','PREMIUM','PLATINUM','ULTRA')),
    payment_id VARCHAR(200),
    start_date TIMESTAMP,
    end_date   TIMESTAMP,
    is_active  BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_sub_user ON user_subscriptions(user_id);
CREATE INDEX idx_sub_plan ON user_subscriptions(plan);


-- ─────────────────────────────────────────
-- CREATE ALL DATABASES (run as superuser first)
-- ─────────────────────────────────────────
-- CREATE DATABASE dating_auth;
-- CREATE DATABASE dating_users;
-- CREATE DATABASE dating_swipes;
-- CREATE DATABASE dating_matches;
-- CREATE DATABASE dating_events;
-- CREATE DATABASE dating_location;
-- CREATE DATABASE dating_mood;
-- CREATE DATABASE dating_radar;
-- CREATE DATABASE dating_notifications;
-- CREATE DATABASE dating_subscriptions;
-- CREATE USER dating_user WITH PASSWORD 'dating_pass';
-- GRANT ALL PRIVILEGES ON ALL DATABASES TO dating_user;
