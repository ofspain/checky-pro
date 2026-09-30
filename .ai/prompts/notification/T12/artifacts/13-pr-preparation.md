# notification · T12 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T12: email channel (EmailChannel, Amazon SES v2)`

## Commit message

```
notification-service T12: email channel (EmailChannel, Amazon SES v2)

Add EmailChannel, replacing NoOpEmailChannel (pre-authorized since T11)
as the real EMAIL NotificationChannel: sends via Amazon SES v2 in real
profiles and via a capturing fake in tests, so no real email is ever
sent in CI (agents.md). O2/Q2 (SES vs SendGrid vs SMTP) was already
resolved at T03 - this task only wires the already-chosen vendor.

EmailChannel delegates to an EmailTransport seam (SesEmailTransport vs
FakeEmailTransport), selected exclusively by
themistra.notification.email.transport (ses|fake) via
@ConditionalOnProperty - never both active at once. The property's own
default flips from ses to fake, so local/test never reaches AWS by
accident; real environments must set EMAIL_TRANSPORT=ses explicitly.
An EmailTransportStartupValidation component fails startup with a clear
message for any other value, rather than a generic Spring DI error.

SesEmailTransport never lets a raw AWS SDK exception escape unmodified:
request construction and the API call both run inside one try block: a
caught AwsServiceException surfaces only its own trusted errorCode/
errorMessage; any other RuntimeException gets a generic, class-name-only
message. Neither is ever attached as EmailDeliveryException's own cause,
since SLF4J/Logback prints a Throwable's full cause chain by default -
attaching it would defeat the sanitization. EmailChannel validates
recipient/subject/body before any transport call and never catches what
the transport throws - DeliveryOrchestrator's own outcome-recording
(T11, zero lines changed by this task) stays the single source of
truth. Its own log line, and EmailMessage's own toString(), name only
accountUuid/messageId - never the recipient's own email address, which
is PII (agents.md's own observability rule, not only secrets/tokens).

FakeEmailTransport is a thread-safe (CopyOnWriteArrayList), test-
observable capturing fake (sentMessages/clear/findMostRecentByRecipient).

All Amazon SES v2 SDK API shapes were verified directly against the
actual sesv2/aws-core jars (via javap) before writing any code, not
assumed from memory.

Three adversarial review rounds (Kimi Phase 3: 10 findings; Phase 8: 10
findings, including a real, verified agents.md PII-logging violation
this task's own self-review had already partly caught; Phase 11: 9
gaps, 2 of which turned out to already be covered by a pre-existing T11
test once cross-referenced against this task's own new code) were each
independently verified against actual source before disposition.

273 tests total (236 T01-T11 unaffected + 37 new: 31 at Phase 10 closing
Kimi's "no committed tests" finding, 6 more in the Phase 11 addendum).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/channel/EmailChannel.java`
- `services/notification/src/main/java/com/themistra/notification/channel/EmailTransport.java`
- `services/notification/src/main/java/com/themistra/notification/channel/EmailMessage.java`
- `services/notification/src/main/java/com/themistra/notification/channel/EmailDeliveryException.java`
- `services/notification/src/main/java/com/themistra/notification/channel/SesEmailTransport.java`
- `services/notification/src/main/java/com/themistra/notification/channel/FakeEmailTransport.java`
- `services/notification/src/main/java/com/themistra/notification/common/config/SesClientConfig.java`
- `services/notification/src/main/java/com/themistra/notification/common/config/EmailTransportStartupValidation.java`
- `services/notification/src/test/java/com/themistra/notification/channel/EmailChannelTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/EmailMessageTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/SesEmailTransportTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/FakeEmailTransportTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/EmailTransportWiringTest.java`
- `services/notification/src/test/java/com/themistra/notification/common/config/EmailTransportStartupValidationTest.java`

**Modified**
- `services/notification/src/main/resources/application.properties` (`email.transport` default:
  `ses` → `fake`)
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (authorized file-inventory list, renamed method)
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryOrchestratorIntegrationTest.java`
  (2 new tests inspecting `FakeEmailTransport`/`EmailProperties` directly)

**Deleted**
- `services/notification/src/main/java/com/themistra/notification/channel/NoOpEmailChannel.java`
  (pre-authorized since T11's own Javadoc)
- `services/notification/src/test/java/com/themistra/notification/channel/NoOpEmailChannelTest.java`
  (necessary consequence — its own subject class no longer exists)

**Process artifacts**
- `.ai/prompts/notification/T12/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

The third task in this pipeline to convert a temporary `NoOp*` placeholder into a real,
production-ready implementation (after T06→T11's `DeliveryOrchestrator` and, before it,
`NoOpNotificationDispatcher`). `EmailChannel` is now the real place a rendered `verify_email`/
`password_reset` message becomes an actual email — closing the loop `TemplateRenderer` (T09) and
`DeliveryOrchestrator` (T11) opened. `DeliveryOrchestrator` itself required **zero code changes**
for this task, the strongest possible proof that T11's own "channels behind one interface" boundary
(L5) was honored, not merely asserted.

## Testing performed

- `mvn -pl services/notification clean verify` — 273 tests, 0 failures, 0 errors, `BUILD SUCCESS`.
- All AWS SES v2 SDK API shapes (`SendEmailRequest`, `Destination`, `EmailContent`, `Message`,
  `Body`, `Content`, `AwsServiceException.awsErrorDetails()`) verified directly against the actual
  `sesv2-2.50.2`/`aws-core-2.25.16` jars via `javap` before any code was written.
- An adversarial exception-sanitization test deliberately embeds a token-shaped string in an AWS
  exception's own *untrusted* top-level message and confirms it never surfaces in the sanitized
  exception this codebase re-throws — only the *trusted*, AWS-supplied `errorCode`/`errorMessage`
  fields do.
- A real Logback `ListAppender` inspects the actual runtime-formatted log event (not just source
  text) to confirm no PII reaches a log line at runtime.
- Two independent 16-thread concurrency proofs for `FakeEmailTransport` (distinct recipients; same
  recipient) confirm the capturing fake is genuinely thread-safe.
- An `ApplicationContextRunner`-based wiring test proves exactly one `EmailTransport` bean is ever
  active per config value — including the case where the config value is invalid — with no Docker
  and no real AWS credentials needed.
- `git diff --stat` across this task's entire commit range confirms zero files under
  `services/notification/src/main/java/com/themistra/notification/delivery/` were touched.
- `git status -s services/auth services/crypto services/payment` — empty throughout every one of
  this task's own commits; no sibling service touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 12 ("Email channel (O2/Q2)").
- **Requirements:** R1, R2, R15.
- **LOCKED decisions:** L4, L5, L10, L11.
- **Resolved ambiguity:** O2/Q2 (SES vs. SendGrid vs. SMTP relay) was already resolved in favor of
  Amazon SES at T03 (the `sesv2` dependency and `EmailProperties` were already scaffolded with an
  explicit comment saying so) — this task implements against that already-made choice, it does not
  re-litigate it.

## Known, deliberate gaps (not this task's scope)

- **A real SES call now runs synchronously inside `DeliveryOrchestrator`'s own open DB transaction**
  (T11) — increases connection-pool pressure and lock duration under load. Disclosed at Phase 7/8;
  a real fix would mean revisiting T11's own frozen transaction boundary.
- **A real email can be sent more than once for the same event** if that same transaction aborts
  after a successful SES call but before commit (e.g. a forced Kafka rebalance) — the idempotency
  record rolls back too, so redelivery re-sends. Low-harm for `verify_email`/`password_reset` (a
  duplicate re-delivers the same still-valid link). Same root cause as above; not fixed here.
- **Real Amazon SES has never actually been called** — by design (AC3, `agents.md`'s own mandate).
  `SesEmailTransport`'s correctness against the live SES service itself (vs. its documented API
  contract) is only confirmed the first time a real environment runs with `EMAIL_TRANSPORT=ses`.
- **In-app channel** (`InAppChannel`, task 13) and **retry/dead-letter logic** (task 14) are
  untouched — this task's own scope is `EMAIL` only.

## Reviewer notes

- **Kimi's Phase 8 Finding #2 was a genuine, verified `agents.md` violation**: `EmailChannel`'s own
  success log line included the recipient's email address, which is PII —
  `agents.md`'s own observability rule ("Never log tokens, secrets, reset-token values, full API
  keys, or PII") forbids this, even though the frozen brief's own AC5 only literally named
  secrets/tokens/API keys. Fixed; while fixing it, an identical, unflagged latent leak in
  `EmailMessage.toString()` (which also printed the raw recipient address) was caught and closed in
  the same pass — worth a reviewer's attention as a case where fixing the *named* instance of a
  problem surfaced an *unnamed* twin.
- **Kimi's Phase 11 Gaps #3 and #4 were already covered**, not missing: a pre-existing T11 test
  (`DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames`)
  already proves, against the real component-scanned application context, that exactly one `"EMAIL"`
  bean exists — since `NoOpEmailChannel` no longer exists, that bean can only be `EmailChannel`
  itself. The gap was in cross-referencing an existing test against this task's own new code, not in
  actual coverage — worth noting as the reverse case of Finding #2 above (a genuinely-already-solved
  problem, not a real gap).
- **Self-review (Phase 7) and independent review (Phase 8) converged on the same finding twice**
  (request construction outside `SesEmailTransport`'s own `try/catch`; missing `body` validation) —
  both fixed identically regardless of which review raised them first, and both also reversed the
  Phase 4 frozen brief's own explicit deferral of a startup validator, once two independent reviews
  asked for the same thing.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T12.**
