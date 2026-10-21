# Notification Service

Java 21 · Spring Boot. Idempotent Kafka consumer.

Owns: templates, channel preferences, delivery log (dispute-grade: "was the merchant notified?"),
`notifications` Postgres schema.

Channels at launch: email + in-app (SSE/websocket). Next: HMAC-signed merchant webhooks, mobile push.
Consumes: `invoice.created`, `payment.*`, `receipt.issued`, `user.registered`.

## Local development database

notification-service runs in **its own database** (open-issues sweep, 2026-10-20). Its V1 narrows
`search_path` to `notifications` and uses `CITEXT`, so `citext` must live in the `notifications`
schema, and PostgreSQL allows one copy of an extension per database. In a database shared with
auth-service, auth's V1 has already put `citext` in the `auth` schema. The Flyway callback
`db/migration/beforeMigrate.sql` installs `citext` into `notifications` before V1, and refuses a
database where it lives elsewhere, with a message saying so.

Locally, use `services/auth`'s Postgres container
(`docker compose -f services/auth/compose.local.yaml up -d postgres`) with a separate database on it:

```
docker exec -it auth-postgres-1 psql -U checky -d checky -c "CREATE DATABASE checky_notifications"
mvn -pl services/notification flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/checky_notifications
docker exec -it auth-postgres-1 psql -U checky -d checky_notifications \
  -c "ALTER ROLE notification_app PASSWORD 'notification-app-local-only';"
DB_URL=jdbc:postgresql://localhost:5432/checky_notifications mvn -pl services/notification spring-boot:run
```

The runtime role is `notification_app`, separate from the migration owner and unprivileged: table
owners bypass grants in Postgres, so this split is what makes least privilege real.

**Existing shared `checky` setups keep working.** If your local `checky` database already holds the
`notifications` schema, it is because `citext` was moved into `notifications` by hand
(`ALTER EXTENSION citext SET SCHEMA notifications`). The callback accepts that state, and so does
auth's matching guard, because auth's V1 has already run there.

The migration itself never commits a password - real environments provision `notification_app`'s
credential out-of-band (External Secrets Operator), same as every other credential this service
uses.
