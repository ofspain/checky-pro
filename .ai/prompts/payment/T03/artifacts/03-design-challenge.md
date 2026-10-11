# payment · T03 · Phase 3 — Design Challenge Findings

Artifact: `.ai/prompts/payment/T03/artifacts/03-design-challenge.md`  
Consumes: `.ai/prompts/payment/T03/artifacts/02-task-implementation-brief.md`  
Authoritative standing rules: `spec/payment-service/agents.md`, `spec/payment-service/design.md` §4c  
Scoped to T03 only. Findings only — no redesign, no implementation.

---

### 1. 403 shape cannot be exercised with the filter chain described in T03

- **Issue:** AC4 and `ResourceServerConfigTest` require an RFC 9457 `application/problem+json` body on both 401 and 403. The filter chain in T03 only permits public actuator paths and requires authentication for `anyRequest()`. There is no authority-based matcher, so no request can produce a 403 until a scoped endpoint (e.g., an internal endpoint requiring `SCOPE_internal.crypto:write`) is added in a later task.
- **Severity:** Medium — the requirement is testable in principle, but not with the code T03 is allowed to write.
- **Evidence:**
  - `02-task-implementation-brief.md:105-106` (AC3/AC4)
  - `02-task-implementation-brief.md:114-115` (`ResourceServerConfigTest`)
  - `services/notification/src/main/java/com/themistra/notification/common/ResourceServerConfig.java:45-48` (only `permitAll` + `authenticated`)
  - `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java:52-55` (has `/internal/v1/**` scope matcher, so 403 is reachable)
- **Recommended brief amendment:** Either (a) add a test-only scoped authority matcher for the 403 case, (b) test the `AccessDeniedHandler` bean directly with mock request/response objects, or (c) defer the 403-shape assertion to the task that introduces the first authority-restricted endpoint, while keeping the 401-shape test now.

---

### 2. Required vs. optional configuration fields are not enumerated

- **Issue:** AC1 and AC5 say validated properties must fail startup on missing/invalid values in non-local profiles, but several `design.md` §4c keys already have defaults (`default-ttl-minutes=60`, `amount-tolerance-base-units=0`, cron expressions, Crypto paths/timeouts). Others use environment placeholders with empty or localhost defaults (`base-url=${CRYPTO_SERVICE_BASE_URL:http://crypto-service}`, `bucket=${RECEIPT_BUCKET:}`, `kms-public-keys-url=${KMS_PUBLIC_KEYS_URL:}`). The brief doesn't state which fields are required and which are optional-with-default.
- **Severity:** Medium — tests cannot assert the right failures without knowing which fields must be non-blank/positive.
- **Evidence:**
  - `02-task-implementation-brief.md:100-101` (AC1)
  - `02-task-implementation-brief.md:107-109` (AC5)
  - `spec/payment-service/design.md` §4c property block
- **Recommended brief amendment:** List, per record, which fields are required (must be non-blank/positive and fail startup when missing/invalid in non-local profiles) and which have safe defaults. For example, mark `receipt.bucket`, `receipt.kms-public-keys-url`, and `crypto.base-url` as required; keep cron/timeout/TTL/tolerance as optional-with-default.

---

### 3. No `spring.threads.virtual.enabled=true`

- **Issue:** `services/crypto` enables virtual threads in `application.properties` because the watcher layer benefits from them. Payment service will run Kafka consumers, scheduled outbox relay, and scheduled expiry/anchor jobs — all I/O-bound workloads that similarly benefit. T03 is the natural place to set this, but the brief omits it.
- **Severity:** Low — not a functional defect, but a platform-convention gap.
- **Evidence:**
  - `services/crypto/src/main/resources/application.properties:6`
  - `02-task-implementation-brief.md:17-22` (application.properties scope)
- **Recommended brief amendment:** Add `spring.threads.virtual.enabled=true` to `application.properties` and a corresponding assertion in the config test (or explicitly document why payment-service does not need it, if that's the decision).

---

### 4. `management.endpoint.health.show-details=never` is not required

- **Issue:** AC6 requires actuator exposure and health probes, but does not require `management.endpoint.health.show-details=never`. Both sibling services set it to prevent health endpoints from leaking internal details. `agents.md` Security rule says responses contain no internal detail.
- **Severity:** Low — defense in depth.
- **Evidence:**
  - `services/crypto/src/main/resources/application.properties:55`
  - `services/notification/src/main/resources/application.properties:60`
  - `02-task-implementation-brief.md:110-111` (AC6)
  - `spec/payment-service/agents.md` Security rule (RFC 9457, no internal detail)
- **Recommended brief amendment:** Add `management.endpoint.health.show-details=never` to AC6 and to the required `application.properties` content.

---

### 5. Kafka consumer group-id is not configured

- **Issue:** The brief says include Kafka bootstrap servers but does not require a consumer group-id. Payment service will have Kafka consumers (outbox relay in task 4, `chain.tx.*` consumers in task 11). Without an explicit group-id, Spring Kafka falls back to a generated id, which changes across restarts and defeats consumer-group semantics.
- **Severity:** Low — functional now, but a convention gap that will be harder to catch later.
- **Evidence:**
  - `services/notification/src/main/resources/application.properties:42-43`
  - `02-task-implementation-brief.md:17-22` (Kafka bootstrap servers)
- **Recommended brief amendment:** Add `spring.kafka.consumer.group-id=payment-service` to `application.properties` now, even though the first listener is in a later task.

---

### 6. `@WebMvcTest` for `ResourceServerConfigTest` assumes a dummy controller

- **Issue:** The Required Tests specify `@WebMvcTest` to verify public/authenticated paths. With no controller in the production source yet, the test needs an inner `@RestController` or `@TestConfiguration` controller to have a path to protect. The brief doesn't mention this.
- **Severity:** Low — an implementer will likely figure it out, but it is an unstated assumption.
- **Evidence:**
  - `02-task-implementation-brief.md:114-115`
  - `02-task-implementation-brief.md:28-29` (controllers are out of scope)
- **Recommended brief amendment:** Note in Required Tests that the `@WebMvcTest` should include a minimal test-only controller (e.g., `@RestController` inside the test class) so there is a non-public path to assert against.

---

### 7. No source-text guards for secrets or YAML prohibition

- **Issue:** L13 says no secret/credential committed, and `agents.md` says flat `application.properties` only — never YAML. The brief creates `application.properties` but does not require a regression test scanning it for credential patterns or scanning the resources tree for `.yml`/`.yaml` files. T01/T02 established this style of negative file-scan guard.
- **Severity:** Low — the human reviewer can catch it, but CI cannot without a test.
- **Evidence:**
  - `02-task-implementation-brief.md:43-45` (L13)
  - `spec/payment-service/agents.md` Configuration rule (flat `application.properties` only)
  - `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java` (negative file-scan precedent)
- **Recommended brief amendment:** Add to Required Tests a source-text guard asserting `application.properties` contains no credential placeholder beyond the documented datasource-password carve-out, and that no `.yaml`/`.yml` files exist under `src/main/resources`.

---

### 8. Datasource connection pool and search_path settings are not explicit

- **Issue:** The brief says "datasource (as payment_app)" but does not list `spring.datasource.hikari.maximum-pool-size`, `connection-timeout`, or the `connection-init-sql=SET search_path TO payments, public` that sibling services use. Omitting the search-path init SQL would cause JPA queries to fail once entities are added.
- **Severity:** Low — harmless now with zero entities, but the brief is setting the foundation for later tasks.
- **Evidence:**
  - `services/crypto/src/main/resources/application.properties:13-18`
  - `services/notification/src/main/resources/application.properties:14-19`
  - `02-task-implementation-brief.md:17-22`
- **Recommended brief amendment:** Add the Hikari pool settings and `connection-init-sql=SET search_path TO payments, public` to the `application.properties` scope and to AC5/AC6 checks.
