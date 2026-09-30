# notification · T12 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` is a package-by-feature Spring Boot 3.5.4 / Java 21 service, consume-only at
launch (L2): it reacts to Kafka events (`auth.email.requested`, `auth.user.lifecycle`; payment
topics unreachable until T07/`payment-service` exists) and never makes a synchronous cross-service
call on the delivery path. Persistence is a single `notifications` Postgres schema (Flyway,
DDL-only, JPA for simple find/save). It is an OAuth2 resource server for its own in-app read/stream
API (not touched by this task). Six modules exist under
`src/main/java/com/themistra/notification/`: `consumer`, `preference`, `template`, `delivery`,
`channel`, `common` (`inapp` — the SSE/read API — is future scope, not yet built).

The delivery path, fully wired since T11: `AuthEventConsumer` (T06) → `IdempotencyGuard` (T04) →
`ContactProjectionUpdater` (T05) → `DeliveryOrchestrator` (T11), which resolves preferences
(`PreferenceResolver`, T08) → renders (`TemplateRenderer`, T09) → dispatches to a resolved
`NotificationChannel` bean (looked up by its own `channel()` name, `"EMAIL"`/`"IN_APP"`) → appends a
`delivery_log` row (`DeliveryLog`/`DeliveryLogRepository`) with a redacted `errorDetail`
(`SecretSafeLogging`, T10). `dispatch` is `@Transactional` and never throws.

## 2. Existing code this task touches

**Already exists, directly reusable:**
- `channel/NotificationChannel.java` (T11) — the interface: `String channel()`,
  `void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message)`.
- `channel/NoOpEmailChannel.java` (T11) — the temporary placeholder this task replaces. Its own
  Javadoc explicitly pre-authorizes this: *"replaced (this file deleted, the real implementation
  added in its place) by task 12's own `EmailChannel`"*. Currently just logs at `DEBUG` (guarded by
  `isDebugEnabled()`) and returns — never actually sends anything.
- `template/TemplateRenderer.RenderedMessage` (T09) — `record(String subject, String body, int
  version)`, already the exact type `NotificationChannel.send`'s third parameter carries. Its own
  `toString()` deliberately excludes `subject`/`body` content (safe to log the whole object).
- `common/config/EmailProperties.java` (T03) — `@ConfigurationProperties(prefix =
  "themistra.notification.email")`, fields `from` (`@NotBlank`) and `transport` (`@NotBlank`,
  deliberately an unconstrained `String`, not an enum — T03's own Javadoc: *"the task that
  implements `EmailChannel` decides the allowed values, including whatever test-only value its own
  capturing fake transport needs"*). Bound from `application.properties`:
  `themistra.notification.email.from=no-reply@checky.pro`,
  `themistra.notification.email.transport=${EMAIL_TRANSPORT:ses}`.
- **`pom.xml` already declares `software.amazon.awssdk:sesv2`**, with an explicit comment: *"Email
  transport (O2/Q2): Amazon SES via the modern v2 client"*. **O2/Q2 (SES vs SendGrid vs SMTP) was
  already resolved in favor of SES at T03** — this task does not need to re-litigate the vendor
  choice, only implement `EmailChannel` against it. No `SesV2Client` bean or any AWS SDK usage
  exists anywhere in `main/` yet — building that wiring is this task's own job.
- `common/config/LinkProperties.java` (T03/T09) — `baseUrl`, already the source
  `TemplateRenderer.computeLinkPlaceholders` uses for every link placeholder in a rendered body;
  not touched by this task, only consumed indirectly via the already-rendered `RenderedMessage`.

**Already exists, requires the disclosed extension this task performs:**
- `T01SkeletonRegressionTest.java` — will need its authorized file-inventory list updated once
  `EmailChannel` is added and `NoOpEmailChannel` deleted, per T04/T05/T06/T08/T09/T10/T11's own
  unbroken precedent.

**New, this task's own deliverable (per `design.md` §6):**
- `channel/EmailChannel.java` — replaces `NoOpEmailChannel`, `implements NotificationChannel`.
- Whatever AWS SDK client bean/configuration wiring `EmailChannel` needs (e.g. a `SesV2Client`
  `@Bean`, region/credential resolution — `design.md` §4c's own config block lists no explicit AWS
  region/credential property, and `agents.md` forbids "AWS SDK secret-retrieval in application
  code," suggesting the SDK's own default credential/region provider chain, i.e. IRSA in EKS, is the
  intended mechanism — to be confirmed at Phase 1/3, not assumed here).
- A **capturing fake transport** for tests (`package.md` §8 header / `agents.md`: *"a capturing fake
  transport — no real email is sent in CI"*) — no such fake exists anywhere in the tree today; this
  task builds it. `EmailProperties.transport`'s own deliberately-unconstrained string type
  (T03's own disclosed intent) is almost certainly the selector between the real SES-backed bean and
  the fake, but the exact mechanism (a distinct `transport` value read by a `@Bean` method à la
  `@ConditionalOnProperty`, vs. a `@Primary` test-only Spring bean override, vs. a hand-constructed
  test double with no Spring involvement at all) is undecided — Phase 1/3's own job.

## 3. Established patterns to follow

- **"Temporary NoOp → real implementation, same interface" pattern** (T06→T11 precedent, now
  repeating for T11→T12): delete the placeholder, add the real class implementing the same
  interface, update `T01SkeletonRegressionTest`'s own authorized list, delete the placeholder's own
  test if its subject class is gone.
- **Validated `@ConfigurationProperties` records** (`EmailProperties`, `LinkProperties`,
  `RetryProperties`, `InappProperties`) — `@NotBlank`/similar constraints fail startup in non-local
  profiles (L10); `LinkPropertiesStartupValidation` is the established pattern for a property that
  is deliberately blank in `local` but required elsewhere, if `EmailChannel` needs an analogous
  conditional-requiredness check for any new property.
- **Redaction discipline (L4/R15)**: every `errorDetail` `DeliveryOrchestrator` persists is already
  passed through `SecretSafeLogging.redact()` before persistence — if `EmailChannel.send` throws, its
  exception's own message is what eventually reaches that redaction, so `EmailChannel` itself does
  not need to duplicate redaction logic, only avoid constructing an exception message that embeds
  raw secrets in the first place (mirrors T09's own `RenderedMessage.toString()` precedent: safety
  lives in what gets constructed, not only in a downstream filter).
- **`Clock`-injection for testability** (T04 precedent, reused at T05/T11) — if `EmailChannel` needs
  a timestamp for anything (e.g. a `Message-ID` header, a sent-at log field), inject `Clock`, never
  call `Instant.now()`/`System.currentTimeMillis()` directly.
- **Fixed-`Clock` unit tests, Testcontainers integration tests** (every prior task) — `EmailChannel`
  itself has no persistence of its own, so a real Spring-context integration test's own value here is
  likely narrower than prior tasks' (proving the real bean is what gets component-scanned/wired,
  mirroring `IdempotencyGuardIntegrationTest.theRealDeliveryOrchestratorIsTheResolvedSpringBean`'s
  own precedent) — Phase 5/6's own call.
- **Never modify a sibling module's files** — `EmailChannel` lives in `channel/`; `DeliveryOrchestrator`
  (`delivery/`) is its only caller and is NOT touched by this task (it already resolves channels
  generically via `channelsByName.get(channel)` — no change needed there for a new channel
  implementation to be picked up, since Spring's own `List<NotificationChannel>` injection collects
  every `@Component` implementing the interface automatically).

## 4. Testing conventions

- Unit: plain JUnit + Mockito, no Spring context, no Docker (e.g. `DeliveryOrchestratorTest`,
  `NoOpEmailChannelTest`).
- Integration: `@Testcontainers` (Postgres [+ Kafka, only where a listener is involved]) +
  `@SpringBootTest`, real collaborators, no mocks (e.g. `DeliveryOrchestratorIntegrationTest`).
  `EmailChannel` has no persistence of its own, so whether it needs its own Testcontainers
  integration test (vs. only a real-context bean-resolution proof) is a Phase 5/6 design call, not
  assumed here.
- ArchUnit: package-by-feature boundaries enforced (`shouldPreventCrossModuleEntityImports`, L11) —
  not expected to need a new rule for this task (no new entity).
- Contract tests: validate consumed payloads against `contracts/events/*` — not touched by this task
  (`EmailChannel` consumes only an already-rendered `RenderedMessage`, not a raw event payload).
- `T01SkeletonRegressionTest` — the file-inventory static guard every prior task has updated;
  expected here too.

## 5. Known gaps / unknowns

- **The capturing-fake-transport mechanism is undecided.** `agents.md`/`package.md` both mandate one
  exists ("no real email is sent in CI") but no code, test helper, or design-doc paragraph specifies
  its shape. This is the single largest open design question for this task — flagged for Phase 1/3,
  not resolved here.
- **AWS region/credential resolution for the SES v2 client is not specified anywhere in the spec
  package.** `design.md`'s own config block (§4c) lists no `aws.region` or credential property, and
  `agents.md` explicitly forbids "AWS SDK secret-retrieval in application code," which points toward
  the SDK's own default provider chain (environment/IRSA in EKS) — I do not know whether this is the
  intended mechanism or whether a Phase 1/3 decision should propose an explicit
  `@ConfigurationProperties` region field instead. Flagging as "I do not know," not assuming.
- **R1/R2's own named tests (`shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
  `shouldSendPasswordResetEmailOnAuthEmailRequestedReset`) already exist and pass**, in
  `AuthEventConsumerTest` (T06) — but they prove only routing/dispatch-call correctness, explicitly
  *"not an actual send"* (T06's own frozen brief scope note). This task makes the actual send real
  for the first time; whether its own new tests should reuse those exact two method names again (in
  a different class, testing a different concern) or use new names is a Phase 1 interpretation call,
  not decided here.
- **No readiness-gate/health-check code exists for the email transport** despite `package.md:114`
  ("Readiness gates on DB + Kafka + the outbound email transport reachability... per O2") — the task
  statement for T12 does not mention health/readiness at all, so I read this as a different, later
  task's own scope (not found assigned to any task number in `tasks.md` at this reading), not
  something T12 must build. Flagging for Phase 1 to confirm against `tasks.md` in full, not assumed
  in scope here.
- **`DeliveryOrchestrator` itself needs no code change** for this task to take effect — confirmed by
  reading its own constructor (`List<NotificationChannel> channels`, collected via
  `Collectors.toMap(NotificationChannel::channel, ...)`), which already resolves any bean whose
  `channel()` returns `"EMAIL"` generically. A new `EmailChannel` bean with `channel() -> "EMAIL"`
  is picked up automatically once `NoOpEmailChannel` (which returns the same string) is deleted —
  no duplicate-key collision risk as long as exactly one `"EMAIL"`-returning bean exists at a time.
