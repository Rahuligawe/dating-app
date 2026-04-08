-- Run this as postgres superuser first
CREATE DATABASE dating_auth;
CREATE DATABASE dating_users;
CREATE DATABASE dating_swipes;
CREATE DATABASE dating_matches;
CREATE DATABASE dating_events;
CREATE DATABASE dating_location;
CREATE DATABASE dating_mood;
CREATE DATABASE dating_radar;
CREATE DATABASE dating_notifications;
CREATE DATABASE dating_subscriptions;

CREATE USER dating_user WITH PASSWORD 'dating_pass';
ALTER USER dating_user CREATEDB;

GRANT ALL PRIVILEGES ON DATABASE dating_auth TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_users TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_swipes TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_matches TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_events TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_location TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_mood TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_radar TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_notifications TO dating_user;
GRANT ALL PRIVILEGES ON DATABASE dating_subscriptions TO dating_user;
