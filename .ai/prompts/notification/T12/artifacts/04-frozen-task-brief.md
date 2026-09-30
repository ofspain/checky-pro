STATUS: FROZEN

# notification · T12 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 10 findings assessed against the Phase 2 brief and this codebase's own established conventions
before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | Real-vs-fake transport selection mechanism unspecified | High | **ACCEPTED (property-driven, per Kimi's own option 2)** | A single, always-present `EmailChannel` bean (`channel() -> "EMAIL"`, never duplicated) delegates to an `EmailTransport` interface, selected by `themistra.notification.email.transport` (`ses` \| `fake`) via `@ConditionalOnProperty` at the transport-bean level. This is the mechanism T03's own `EmailProperties.transport` Javadoc explicitly anticipated ("the task that implements `EmailChannel` decides the allowed values, including whatever test-only value its own capturing fake transport needs"). Resolves Findings #1/#3/#9 with one seam, exactly as Kimi's own Finding #3 noted. |
| 2 | Fake transport observability unspecified | High | **ACCEPTED** | `FakeEmailTransport` (`@ConditionalOnProperty(..., havingValue = "fake")`) backed by a thread-safe collection (`CopyOnWriteArrayList<EmailMessage>`); exposes `sentMessages()`, `clear()`, `findByRecipient(String)`. Tests call `clear()` in `@BeforeEach`. Thread-safety is required (Kafka listener threads call `EmailChannel` concurrently, mirroring T06's own `concurrency: 2` precedent). |
| 3 | Real-mode unit-testing without AWS unspecified | Medium | **ACCEPTED** | `EmailTransport` (interface: `String send(EmailMessage message)`) is the seam. `EmailChannel`'s own unit tests mock `EmailTransport` and assert the `EmailMessage` it builds; `SesEmailTransport`'s own unit tests mock `SesV2Client` directly (constructor-injected, no real AWS credential/network needed for either). |
| 4 | Exception sanitization unspecified | Medium | **ACCEPTED (the stricter reading)** | AC5 applies to any exception escaping `EmailChannel`, not only ones it originates — `SesEmailTransport` catches the SDK's own exception (`SdkException`/`SesV2Exception`), extracts only its own `errorCode()`/AWS-supplied `errorMessage()` (never the raw request payload, which could contain a `token=`-shaped link from a rendered `verify_email`/`password_reset` body), and re-throws a new `EmailDeliveryException` carrying only that safe subset. This is stricter than relying solely on `DeliveryOrchestrator`'s own downstream `SecretSafeLogging.redact()` (T11) — belt-and-braces, not a replacement for it. |
| 5 | Required-field validation unspecified | Medium | **ACCEPTED (partial)** | `EmailChannel.send` throws `IllegalArgumentException` if `recipient` is null/blank or `message.subject()` is null (an email cannot be sent without one, even though the type permits it) — caught by `DeliveryOrchestrator`'s own existing `try/catch`, recorded `FAILED`. `message.body()` is documented as never-null by `TemplateRenderer`'s own established contract (`Template.body` is `NOT NULL`; `substitute()` always returns a `String`), not re-validated. Adding `@Email` validation to `EmailProperties.from()` is **REJECTED as out of scope** — it is a T03 file, not in this task's own Files-to-Modify list, and `@NotBlank` already prevents the one failure mode (empty string) that would otherwise crash SES calls silently; a malformed-but-non-blank address is SES's own concern at send time, converted to a `FAILED` outcome by the existing orchestrator path. |
| 6 | HTML vs. plain-text unspecified | Medium | **ACCEPTED** | Plain text only at launch, matching the seeded templates' own actual content (no HTML markup anywhere in `V3__seed_launch_templates.sql`). Documented as a disclosed launch limitation, not a gap — HTML is a future task's own scope if ever needed. |
| 7 | Whether to log the SES message ID unspecified | Low | **ACCEPTED** | `EmailTransport.send` returns the provider's own message id (`String`); `EmailChannel` logs it at `INFO` on success (`accountUuid`, `recipient`, `messageId` only — never subject/body/token), unlike `NoOpEmailChannel`'s own deliberately-downgraded `DEBUG` (T11 Phase 8 Finding #5) — a real send succeeding is a genuinely significant operational event, not placeholder noise. |
| 8 | Fake activation in local dev vs. test unspecified | Low | **ACCEPTED** | `application.properties`'s own existing `themistra.notification.email.transport=${EMAIL_TRANSPORT:ses}` line changes its default value to `fake` — so `local` (and any environment that doesn't explicitly override `EMAIL_TRANSPORT`) safely never reaches AWS by default. `dev`/`staging`/`prod` deployment config (external to this repo, per this codebase's own established single-flat-properties-file convention — no `application-<profile>.properties` files exist anywhere in this module) is responsible for explicitly setting `EMAIL_TRANSPORT=ses`, mirroring how `AUTH_EMAIL_LINK_BASE_URL` is handled today. This is a narrow, justified `application.properties` value change (not a T03 code change — `EmailProperties.java` itself needs no edit), matching T11's own precedent of a narrow, disclosed edit to a prior task's file (`AuthEventConsumer.java`) when the current task genuinely needs it. |
| 9 | Coexistence of real/fake `NotificationChannel` beans | Low | **ACCEPTED (already resolved by #1)** | Only one `NotificationChannel` bean ever returns `"EMAIL"` — `EmailChannel` itself, always present; the real-vs-fake choice lives one layer down, at `EmailTransport`, which is never injected as a `List<>` by `DeliveryOrchestrator` and so cannot collide. |
| 10 | Config property migration/defaults unspecified | Low | **ACCEPTED (already resolved by #1/#8)** | Allowed values for `themistra.notification.email.transport` are exactly `ses` and `fake`; default becomes `fake` (Finding #8). No new `application-*.properties` files are introduced — out of step with this codebase's own established single-file convention. |

A note on scope discipline: none of these 10 dispositions add a speculative improvement beyond what
Kimi's own review asked for. One adjacent idea considered and explicitly **not** adopted: a
`LinkPropertiesStartupValidation`-style startup guard that fails boot if `transport` is still
`fake` outside `local`/`test`. No finding asked for this, and the task statement does not mention
startup/readiness behavior — deferred as a future task's own scope, not built here.

## Task

Unchanged from Phase 2, with all 10 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- `channel/EmailTransport.java` — new interface, the real/fake seam (`String send(EmailMessage)`).
- `channel/EmailMessage.java` — new record (`to`, `from`, `subject`, `body`), mirroring
  `TemplateRenderer.RenderedMessage`'s own small-message-type precedent.
- `channel/SesEmailTransport.java` — real implementation, constructor-injects `SesV2Client` +
  `EmailProperties`; sends plain-text only (Finding #6); catches the SDK's own exception and
  re-throws a sanitized `EmailDeliveryException` (Finding #4).
- `channel/FakeEmailTransport.java` — capturing fake (Finding #2).
- `channel/EmailDeliveryException.java` — new, minimal unchecked exception type.
- `common/config/SesClientConfig.java` — one `@Bean SesV2Client`, itself
  `@ConditionalOnProperty(..., havingValue = "ses")` so a `fake`-transport run never attempts to
  construct a real client at all.
- `application.properties` — `themistra.notification.email.transport` default value changes from
  `ses` to `fake` (Finding #8/#10). No other line changes.

**Out:** Unchanged from Phase 2.

## Business Rules

Unchanged from Phase 1's own extraction (R1, R2, R15).

## Locked Decisions

Unchanged from Phase 1's own extraction: L4, L5, L10, L11.

## Dependencies

Unchanged from Phase 2, plus: `software.amazon.awssdk:sesv2`'s own `SesV2Client`, `SendEmailRequest`,
`Destination`, `EmailContent`, `Message`, `Body`, `Content` types (real transport only); no new
Maven dependency (already present).

## Acceptance Criteria

Unchanged from Phase 1/2's own AC1-6, with AC2/AC3/AC4/AC5 now explicit per the dispositions above,
plus:
7. **AC7.** `EmailChannel.send` throws `IllegalArgumentException` for a null/blank `recipient` or a
   null `message.subject()`, before any transport call is attempted.
8. **AC8.** `SesEmailTransport` never lets a raw `SdkException`/`SesV2Exception` escape unmodified —
   it always re-throws `EmailDeliveryException` carrying only the AWS-supplied error code/message,
   never the original request payload.
9. **AC9.** Exactly one `NotificationChannel` bean returns `"EMAIL"` in any given run — verified by
   the existing `DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames`
   (T11), which this task must keep passing, not merely by inspection.
10. **AC10.** `FakeEmailTransport`'s own capture list is thread-safe.

## Files to Create / Modify / Delete

Unchanged from Phase 2, now itemized:

**Create:** `channel/EmailChannel.java`, `channel/EmailTransport.java`, `channel/EmailMessage.java`,
`channel/SesEmailTransport.java`, `channel/FakeEmailTransport.java`,
`channel/EmailDeliveryException.java`, `common/config/SesClientConfig.java`.

**Modify:** `application.properties` (one line's default value), `T01SkeletonRegressionTest.java`.

**Delete:** `channel/NoOpEmailChannel.java`, `channel/NoOpEmailChannelTest.java`.

## Required Tests

Unchanged from Phase 2, plus: `EmailChannel`'s own validation tests (Finding #5/AC7),
`SesEmailTransport`'s own exception-sanitization test (Finding #4/AC8 — a real
`SesV2Exception`/`SdkException` constructed with a token-bearing message must never surface that
content in the re-thrown `EmailDeliveryException`), `FakeEmailTransport`'s own capture/clear/
`findByRecipient` tests (Finding #2), and a real-context proof that exactly one `"EMAIL"` bean
exists under each of `transport=ses` and `transport=fake` (AC9 — reusing/confirming T11's own
existing bean-count test still passes under this task's own default, and does not regress under
`transport=ses` either).

## Constraints

Unchanged from Phase 2, plus: `EmailTransport` implementations must be selected exclusively via
`@ConditionalOnProperty` (never both active in the same run, Finding #9); `SesClientConfig`'s own
`SesV2Client` bean must not be constructed at all when `transport=fake` (avoids any accidental
credential/network resolution attempt in test/local).

## Open Questions

No blockers. All 10 Phase 3 findings resolved above; one adjacent idea (a startup validator for
`transport`) was considered and explicitly deferred as out of this task's own scope, not left
ambiguous.
