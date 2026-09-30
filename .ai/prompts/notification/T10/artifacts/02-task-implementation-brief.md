# notification · T10 · Phase 2 — Task Implementation Brief

## Task

Add `SecretSafeLogging` — a static redaction utility masking secret-shaped substrings in
free-form text — plus a permanent, reflection-free static-scan test locking the already-established
per-class `toString()` discipline (T06/T09) against a future regression.

## Purpose

Formalizes the token/secret-safety property (L4/R15) this codebase has enforced per-class since
T06 into reusable infrastructure for future logging code (`DeliveryOrchestrator`, task 11) that
can't rely on a fixed DTO's own `toString()` override, plus a permanent guard that a future class
adding an obviously-named sensitive field doesn't silently skip the same discipline.

## Scope

**In:**
- `common/SecretSafeLogging.java` — `public static String redact(String text)`: matches
  `(?i)(token|secret|api_?key|password)=([^&\s]*)` and replaces each match with `<key>=***`
  (preserving the matched key's own original casing, discarding the value); `null` input returns
  `null` (AC4 — a lenient, defensive contract, not the stricter caller-contract precedent
  `PreferenceResolver`/`TemplateRenderer` use for their own core business arguments, since this is
  an observability utility a caller might invoke while already handling an error). Not wired into
  any real call site — no code in this module currently logs free-form rendered content (the only
  future consumer, `DeliveryOrchestrator`, doesn't exist yet).

**Out:**
- Wiring `redact()` into any existing class's own logging (`NoOpNotificationDispatcher`,
  `AuthEventConsumer`) — none of them log free-form content today; each already has its own
  narrower, already-sufficient discipline (logs specific safe fields, never a whole object/string
  that might embed a secret).
- A comprehensive runtime log-capture test rendering every seeded template and asserting nothing
  leaks — no real call site exists yet to exercise that way; the static-scan test (AC5) is this
  task's own chosen, narrower, permanent guard instead.
- Fixing the field-name heuristic's own known limitation (can't catch a semantically-sensitive but
  ambiguously-named field like `RenderedMessage.body`) — disclosed at Phase 1, not this task's own
  job to close.

## Business Rules

R15 (stated in full in Phase 1's own extraction).

## Locked Decisions

- **L4.** No secrets/tokens in messages or logs — this task's own primary deliverable is the
  reusable tooling; the underlying property is already enforced per-class since T06/T09.

## Dependencies

None beyond `java.util.regex` (JDK). No Spring context, no Docker, no DB.

## Acceptance Criteria

1. **AC1.** `redact` masks the value half of any `token=`/`secret=`/`apikey=`/`api_key=`/
   `password=`-shaped substring (case-insensitive key), preserving the key name, replacing the
   value with `***`.
2. **AC2.** Multiple distinct secret-shaped substrings in one input are all masked.
3. **AC3.** Text with no secret-shaped substring returns unchanged (byte-for-byte).
4. **AC4.** `redact(null)` returns `null`.
5. **AC5.** A permanent test scans every `.java` file under
   `src/main/java/com/themistra/notification` for a field declaration named (case-insensitively)
   `token`/`secret`/`password`/`apiKey`/`key`, and asserts every file where such a field is found
   also declares its own `toString()` method — mirrors this module's own established static-scan
   test style (reads source as text, no real reflection/classloading), consistent with
   `T01SkeletonRegressionTest`/`AuthEventConsumerTest`'s own precedent for this class of guard.

## Required Tests

`redact()`'s own correctness (AC1-AC4: single match, multiple matches, case-insensitivity, no
match, `null` input) and the reflection-free static-scan test (AC5, verified against the current
codebase's own one real match — `EmailRequestedEvent.token` — plus a synthetic
positive/negative-fixture pair proving the scan itself would catch a violation and correctly
ignore a safe class).

## Constraints

- **No new dependency** — plain `java.util.regex`, matching the rest of this module's own
  established style (no reflection library, no ArchUnit for this specific guard).
- **`redact()` is lenient on `null`**, unlike this module's other recent business-logic methods —
  explicitly disclosed as a deliberate, narrower exception to the caller-contract precedent
  (Constraints section of T08/T09's own frozen briefs), not an oversight.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/common/SecretSafeLogging.java`

## Files to Modify

None expected. (Historical note: T04/T05/T06/T08/T09 each discovered a required, undisclosed-in-brief
`T01SkeletonRegressionTest.java` update once their own new production files existed — expected to
recur here too, disclosed at Phase 6/9 exactly as those five tasks did.)

## Files NOT to Modify

- `services/notification`'s own T01-T09 files (`pom.xml`, prior migrations,
  `{AuthEventConsumer,NotificationDispatcher,NoOpNotificationDispatcher}.java`,
  `{ChannelPreference*,PreferenceResolver}.java`, `{Template*,TemplateRenderer}.java`, config
  records, `ResourceServerConfig`, `PublicEndpoints`).
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — no cross-service dependency exists for
  this task.

## Open Questions

No blockers. Both of Phase 0/1's own carried-forward questions are resolved as concrete design
decisions in Phase 1's own extraction, not re-opened here.
