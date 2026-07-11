# Notification Service

Java 21 · Spring Boot. Idempotent Kafka consumer.

Owns: templates, channel preferences, delivery log (dispute-grade: "was the merchant notified?"),
`notifications` Postgres schema.

Channels at launch: email + in-app (SSE/websocket). Next: HMAC-signed merchant webhooks, mobile push.
Consumes: `invoice.created`, `payment.*`, `receipt.issued`, `user.registered`.
