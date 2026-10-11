# payment · T01 · Phase 0 — Repository Understanding

## 1. Architecture summary (planned, per spec — nothing built yet)

`services/payment` does not exist as code; only `services/payment/README.md` is present (confirmed
`find services/payment -type f` → one file). Per `spec/payment-service/package.md`/`design.md`:

- **Modules (package-by-feature under `com.themistra.payment`, L12):** `invoice`, `payment`,
  `verification`, `consumer`, `receipt` (+ `receipt/ledger`), `attestation`, `export`, `events`,
  `common` (design.md §6).
- **Persistence:** PostgreSQL, owns the `payments` schema only (no cross-schema queries). Flyway
  DDL-only migrations; `V1__payments_baseline.sql` is the whole baseline (not this task — task 2).
  JPA for simple find/save. Money is `NUMERIC`/`BigDecimal` only, never `float`/`double` (L6,
  ArchUnit-enforced later).
- **Events/outbox:** Kafka (Spring Kafka). Every publish (`invoice.created`, `payment.seen`,
  `payment.finalized`, `receipt.issued`) goes through a transactional outbox + relay (L8) — not
  built this task (task 4).
- **Security:** resource-server only, never an OIDC issuer — validates JWTs against the Auth
  Service JWKS (L10). This service also makes outbound, service-to-service-JWT-authenticated calls
  to Crypto Service's `/attest` and `/watches` (L3, design.md §4c) — it is never itself a client
  secret holder for that; it presents its own service-to-service token.
- **This service never touches a blockchain or KMS** (agents.md, L3) — no web3j/Tron SDK, no AWS
  KMS signing client anywhere. It does use the AWS S3 SDK, for the receipt WORM store only (L9) —
  this task's own explicit addition.

## 2. Existing code this task touches

Nothing in `services/payment` exists yet — this task is purely additive:
- **New:** `services/payment/pom.xml`, and the root `pom.xml`'s `<modules>` list gains
  `<module>services/payment</module>`.
- **Root `pom.xml` current state (read directly):** parent is `spring-boot-starter-parent:3.5.4`;
  `<modules>` currently lists exactly `services/auth`, `services/crypto`, `services/notification` —
  no `services/payment` yet, confirming this is genuinely the first touch.
- **Nothing else in this task** — no Java classes, no `application.properties`, no migrations.
  Those are tasks 2–4 (schema, config, outbox).

## 3. Established patterns to follow (read directly from the three already-built sibling services)

- **`services/auth/pom.xml`** is the one named mirror target, but it is an OAuth2 **issuer**
  (`spring-boot-starter-oauth2-authorization-server` + a local KMS envelope-encryption exception,
  ADR-0003/D-025) — neither applies to payment-service, which is resource-server-only and
  explicitly KMS-free (L3).
- **`services/crypto/pom.xml`** and **`services/notification/pom.xml`** are the more accurate
  templates: both are resource-server-only services with the identical comment "this service
  validates JWTs... but never issues tokens." Both mirror auth's JPA/Flyway/Kafka/actuator/
  Prometheus/testcontainers/ArchUnit/Awaitility block verbatim, redeclare the AWS SDK BOM
  (`software.amazon.awssdk:bom:2.50.2`) at module level for version management (needed here too,
  for the S3 SDK), and pin `testcontainers.version` to `1.21.4` for the same Docker-API-handshake
  reason documented in both their own pom comments and the `docker-testcontainers-handshake-issue`
  memory note — not something specific to either service, so payment-service needs the identical
  pin.
- **Dependencies task 1's own text does NOT list, and which this task therefore does NOT add:**
  ShedLock (`net.javacrumbs.shedlock:*`) and `jackson-dataformat-yaml`. Both auth, crypto, and
  notification DO carry these, but task 1's own enumerated parenthetical list ("web, validation,
  resource-server, data-jpa, flyway, postgres, spring-kafka, actuator, prometheus, testcontainers,
  archunit, awaitility") is exhaustive and deliberately narrower — ShedLock is needed starting at
  task 8 (invoice expiry sweep) and jackson-dataformat-yaml at task 24 (contracts/OpenAPI contract
  test). Mirrors a named lesson from `crypto-service`'s own T01 ("don't blindly mirror auth's
  annotations onto crypto's bare Application class... add each annotation in the task that actually
  introduces the thing it enables") — the same discipline applies to POM dependencies here, and
  task 1's own literal text is the authoritative scope boundary, not a reason to guess a fuller list.
- **finalName / build plugins:** every sibling sets `<finalName>${artifactId}</finalName>` and adds
  the bare `spring-boot-maven-plugin` (needed for `mvn verify`'s `repackage` goal to succeed at all —
  a named lesson from `crypto-service` T01: a pom with the plugin but zero `@SpringBootApplication`
  classes still fails `repackage`, so a bare `Application` class is normally added in the SAME task
  as the pom; this task's own text names only `pom.xml`/module registration, so the bare
  `PaymentServiceApplication` class is implicitly required too, to avoid that exact, already-seen
  failure — flagging under Known Gaps below since task 1's own text doesn't say this explicitly).
  The Flyway Maven plugin (local-dev-only, bound to no `<executions>`) is added per-service with
  only the `<schemas>` value differing (`payments` here) — this task's own text does list
  "flyway," so this plugin registration belongs in this task, with the DDL migration itself
  deferred to task 2.
- **No failsafe-plugin precedent to follow uniformly:** only `crypto-service`'s pom carries
  `maven-failsafe-plugin` (added once, during a specific merge-recovery incident, not as a stated
  platform convention) — `auth` and `notification` have no such plugin despite also having
  Testcontainers-backed integration tests, meaning Surefire alone picks up their `*IntegrationTest`-
  suffixed classes under the normal `test` phase. This task does not need a failsafe-plugin
  decision; it is deferred to whichever later task actually introduces the first integration test.

## 4. Testing conventions

- Unit: plain JUnit, no Spring context, fixed `Clock` (agents.md, design.md §4a). Not exercised by
  this task — no test classes exist yet, and this task adds no behavior to test.
- ArchUnit + contract tests, then Testcontainers (Postgres + Kafka) integration tests — same ladder
  as every sibling service. The `archunit-junit5`/`awaitility` test dependencies this task's own
  text does list exist to support later tasks (module-boundary enforcement at task 25, async/
  scheduled-job assertions from task 8 onward) — adding them now, at pom-creation time, mirrors
  every sibling service's own identical precedent of including test-scope deps upfront even though
  the first test that uses them lands later.
- **Known, pre-existing repo-wide bug, not this task's concern but worth citing so it isn't
  re-discovered as new:** `surefire-archtest-field-bug` — any class with an `@ArchTest` field
  reports 0 tests under this repo's Surefire 3.5.3. auth/crypto's own `ArchitectureTests` are
  silently non-enforced as a result. When this service reaches its own ArchUnit task (25), the
  same bypass technique (a temporary, never-committed standalone `ArchCheckRunner` using
  `ClassFileImporter` + direct `ArchRule.check(...)`) will likely be needed, same as for auth/crypto.

## 5. Known gaps / unknowns

- Task 1's own text names only "Add `services/payment` to the root `<modules>`. Create `pom.xml`...
  Add the S3 SDK dependency..." — it does not explicitly say to add a bare
  `@SpringBootApplication` class. Per the named crypto-service T01 lesson above, I do not know
  whether to silently add one (to avoid a known, previously-hit `repackage` failure) or to treat
  its absence as this task's own explicit, narrow scope and let a build failure surface in a later
  phase. **Flagging rather than guessing — Phase 2 (implementation brief) should decide this
  explicitly, citing the crypto-service precedent, rather than have it fall out of silent habit.**
- I do not know whether `services/payment` needs its own `dependencyManagement` override beyond the
  AWS SDK BOM (crypto-service separately pins `jackson-annotations` to 2.21 for a web3j-specific
  conflict that does not apply here, since payment-service never adds web3j/Tron). Absent a
  payment-specific conflict, I assume none is needed — flagging as an assumption, not a verified fact.
- No `libs/java/outbox` module exists yet in the root `<modules>` list (not checked beyond grep of
  the modules block) — every sibling service's outbox is hand-rolled per-service, not a shared
  library dependency, so this is consistent with, not a gap against, this task's own scope.
