# notification · T15 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/notification` is a Spring Boot 3 / Java 21 Kafka-consuming, package-by-feature monolith
(`com.themistra.notification`), non-custodial, consume-only (L2). It consumes exactly two real Kafka
topics today, both from `services/auth`: `auth.email.requested` and `auth.user.lifecycle`, both
handled by `AuthEventConsumer` (T06). Each listener dedupes via `IdempotencyGuard` (T04, L1), then
hands off to `DeliveryOrchestrator` (T11, extended T12-T14) via the `NotificationDispatcher` seam.
Persistence is Postgres (schema `notifications`), migrated via Flyway at build time only. This
service authors no new published-event contract of its own at launch (consume-only, L2) — it only
ever *deserializes against* already-authored schemas under the monorepo-root `contracts/events/`
directory, never generates or publishes one.

## 2. Existing code this task touches

**Already exists — the substance of R19 appears to already be covered, not newly needed:**
- `consumer/dto/EmailRequestedEvent.java` / `consumer/dto/UserLifecycleEvent.java` (T06) — the two
  hand-written deserialization records this service's own two real `@KafkaListener` methods
  (`AuthEventConsumer.java:72,104`) actually bind against. Both records' own Javadoc already states
  "hand-written, not generated... no code-generation tooling exists anywhere in this repo for
  `contracts/events/*`" — a disclosed, already-accepted T06-era deviation from `agents.md`'s own
  codegen rule, not something this task reopens.
- `consumer/dto/EmailRequestedEventContractTest.java` / `consumer/dto/UserLifecycleEventContractTest.java`
  (T06, extended at T06's own Kimi Phase 8 review) — **already exist and already do almost exactly
  what R19 and this task's own statement describe**: each serializes/deserializes the real record
  against the real schema file at `contracts/events/auth/{email-requested,user-lifecycle}.v1.schema.json`,
  asserts every `required` field is present, asserts no undeclared field is serialized
  (`additionalProperties: false` in spirit), asserts every known enum/purpose value round-trips, and
  (`UserLifecycleEventContractTest`) asserts format constraints (UUID, email shape, ISO-8601 instant).
  Both their own Javadocs explicitly say "Mirrors `services/auth`'s own
  `{UserLifecycleEventPayloadContractTest,EmailRequestedEventPayloadContractTest}` exactly" — the
  exact mirroring this task's own statement asks for was already done, at T06, not deferred to this
  task.
- `services/auth/src/test/java/com/themistra/auth/account/event/UserLifecycleEventPayloadContractTest.java`
  / `EmailRequestedEventPayloadContractTest.java` — the producer-side pattern both of the above
  already mirror; read directly to confirm no drift exists between the two services' own respective
  copies of the same schema-conformance logic.

**Does not exist, and does not appear reachable within this task's own real scope:**
- `contracts/events/payments/` — **does not exist at all** (confirmed by direct directory listing;
  only `contracts/events/auth/*` and `contracts/events/chain/*` exist, the latter belonging to
  `services/crypto`, not this service). The task statement's own literal
  `contracts/events/{auth,payments}/*` wording names a path this repository has never created.
  `services/payment` itself does not exist (no `pom.xml`, not a root Maven module) — this exact gap
  is already memory-recorded as the reason T07 (payment event consumer) was skipped, and nothing
  about this task changes that blocker.
- The literal named test `shouldConformToConsumedEventSchemas` (`package.md` §8) — does not exist
  anywhere in this codebase today under that exact name (confirmed by grep). The two existing
  contract tests' own method names (`serializedEventMatchesTheDocumentedSchema`, etc.) satisfy the
  same substance under different names.

## 3. Established patterns to follow

- **Contract-test shape** (T06, mirrored from `services/auth`): plain Jackson (`ObjectMapper`
  `.findAndRegisterModules()`, dates as ISO-8601 strings not timestamps), no JSON-Schema-validation
  library (`target-design.md` §17.5's own stated reason: not worth adding a dependency for this few
  contract files) — read the schema file's own `required`/`properties`/`enum` nodes directly and
  assert against them structurally.
- **Hand-written DTOs, not generated** — disclosed, accepted deviation from `agents.md`'s own
  codegen rule, since no codegen tooling exists anywhere in this repo for `contracts/events/*`;
  `services/auth`'s own producer-side payload records are hand-written for the identical reason.
- **Secret-safety in a DTO's own `toString()`** — `EmailRequestedEvent.toString()` already excludes
  `token` (L4); any new or modified contract-test-adjacent class must preserve this convention, never
  print a raw secret/token even accidentally via a default `toString()`.
- **Schema path resolution** — `Path.of("../../contracts/events/auth/...")`, relying on Surefire's
  own long-standing convention of running tests with the module directory as the working directory.

## 4. Testing conventions

- Plain JUnit, no Spring context, no Docker for contract tests specifically (schema conformance is
  a pure serialization concern, no DB/Kafka/HTTP involvement needed) — matches both existing
  contract test classes' own shape exactly.
- Fixed `Instant` literals (e.g. `Instant.parse("2026-07-13T00:00:00Z")`), not `Instant.now()`, for
  deterministic serialized output.
- No ArchUnit test exists yet in this service (T16's own future scope) — irrelevant to this task;
  the `shouldPreventCrossModuleEntityImports` named test listed immediately after
  `shouldConformToConsumedEventSchemas` in `package.md` §8 belongs to task 16, not this one (T15's
  own header correctly cites only R19, not L11).

## 5. Known gaps / unknowns

- **I do not know** whether this task's own real, legitimate scope is (a) confirming/lightly
  extending the already-existing `EmailRequestedEventContractTest`/`UserLifecycleEventContractTest`
  coverage and renaming or adding one test to literally match the `shouldConformToConsumedEventSchemas`
  name `package.md` §8 expects, or (b) something more substantial Phase 1/2 will need to extract from
  `requirements.md`/`design.md` that I have not yet identified by reading the code alone. This is
  squarely Phase 1's own job to resolve, not guessed at here.
- **I do not know** whether the `security-audit.v1.schema.json` contract (which exists under
  `contracts/events/auth/` but is consumed by neither of this service's own two real
  `@KafkaListener` methods) is meant to be in scope for this task at all — nothing in this service's
  own code reads that topic/schema today, so it looks out of reachable scope, but this is worth
  confirming explicitly at Phase 1 rather than assumed.
- **This task cannot build any real coverage for `contracts/events/payments/*`** — the directory and
  every schema under it simply do not exist, and `services/payment` itself does not exist. Any
  "payments" half of R19 is blocked on the same precondition T07 is blocked on
  (`spec/payment-service/package.md` reaching `READY FOR IMPL`), not something this task can resolve
  by writing tests against nothing.
