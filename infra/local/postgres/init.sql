-- One database per service (infra/README.md "Database layout"). Runs once, on an empty data volume.
-- The app roles are created here with their local passwords; each service's V2 migration then
-- finds them already present and only grants. Local-only passwords, never used elsewhere.
CREATE DATABASE auth;
CREATE DATABASE crypto;
CREATE DATABASE notification;
CREATE DATABASE payment;
CREATE ROLE crypto_app LOGIN PASSWORD 'crypto-app-local-only';
CREATE ROLE notification_app LOGIN PASSWORD 'notification-app-local-only';
CREATE ROLE payment_app LOGIN PASSWORD 'payment-app-local-only';
