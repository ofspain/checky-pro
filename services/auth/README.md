# Auth Service

Java 21 · Spring Boot · Spring Security · Spring Authorization Server (OIDC/OAuth2 issuer).

Owns: registration, login, MFA (TOTP), token issuance (short-lived JWT + rotating refresh),
merchant API keys, `auth` Postgres schema.

Publishes: `user.registered`, `user.suspended`.
