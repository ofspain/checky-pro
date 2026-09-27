# Notification Service

Java 21 · Spring Boot. Idempotent Kafka consumer.

Owns: templates, channel preferences, delivery log (dispute-grade: "was the merchant notified?"),
`notifications` Postgres schema.

Channels at launch: email + in-app (SSE/websocket). Next: HMAC-signed merchant webhooks, mobile push.
Consumes: `invoice.created`, `payment.*`, `receipt.issued`, `user.registered`.

## Local development database

Uses `services/auth`'s Postgres container (`docker compose -f services/auth/compose.local.yaml up
-d postgres`), a new `notifications` schema, and a separate, unprivileged `notification_app`
runtime role (never the migration owner - table owners bypass grants in Postgres, so this split is
what makes the service's own least-privilege access real).

`V1__notifications_baseline.sql` is a byte-for-byte VERBATIM transcription of `design.md` §4c and
narrows `search_path` to `notifications` before creating its tables, one of which uses `CITEXT`.
That line replaces the search path rather than appending to it, so `citext` must already be visible
*inside* the `notifications` schema before this migration runs - a plain `public`-schema install
(the default, and what `services/auth`'s own migration already does) is not enough. PostgreSQL only
allows one installation of a given extension per database, so which one-time command is correct
depends on whether `citext` is already installed elsewhere on this Postgres instance:

```
# Fresh database, citext not installed anywhere yet:
docker exec -it auth-postgres-1 psql -U checky -d checky \
  -c "CREATE SCHEMA IF NOT EXISTS notifications; CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications;"

# citext already installed in public (e.g. by services/auth's own migration having run first -
# the realistic case on the shared local stack); relocates it, it does not reinstall it:
docker exec -it auth-postgres-1 psql -U checky -d checky \
  -c "ALTER EXTENSION citext SET SCHEMA notifications;"
```

Moving an already-installed `citext` does not break existing columns that use it (type binding is
by OID, not by schema-qualified name) - verified directly against `services/auth`'s own
`accounts.email` column after running the `ALTER EXTENSION` above on the shared local stack.

```
mvn -pl services/notification flyway:migrate
docker exec -it auth-postgres-1 psql -U checky -d checky \
  -c "ALTER ROLE notification_app PASSWORD 'notification-app-local-only';"
```

The migration itself never commits a password - real environments provision `notification_app`'s
credential out-of-band (External Secrets Operator), same as every other credential this service
uses.
