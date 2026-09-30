# notification · T10 · Phase 1 — Specification Extraction

## Business Rules

- **R15.** WHEN rendering a message or writing a log line, THEN the system SHALL NOT include
  secrets, access/refresh tokens, raw password-reset token values beyond the intended one-time
  link, or full API keys.

## Locked Decisions

- **L4.** No secrets or tokens in messages or logs — "Rendered bodies and log lines never contain
  access/refresh tokens, raw secrets, full API keys, or reset-token values beyond the single
  intended one-time link... R15." **Authoritative reading, resolving Phase 0's own carried-forward
  ambiguity:** the carve-out ("beyond the single intended one-time link") applies to rendered
  bodies, not log lines — a rendered `email.verify` body legitimately contains the real token
  embedded in its one intended `verificationLink` (T09's own design), but nothing should ever *log*
  that same body/subject verbatim. `package.md` §9's own checklist item drops this carve-out in its
  own shorthand phrasing; L4's own fuller text governs.

## Files involved

**Already exists, the discipline this task formalizes (not a gap to fix, a pattern to extend):**
- `EmailRequestedEvent.toString()` (T06), `RenderedMessage.toString()` (T09) — both already exclude
  sensitive content from their own `toString()`.
- `NoOpNotificationDispatcher`'s own log statement (T06) — logs `eventData.keySet()` only.

**New, this task's own deliverable (per `design.md` §6, `common/` package):**
- `common/SecretSafeLogging.java` — a static utility, `redact(String text)`, masking any
  secret-shaped substring (`token=`/`secret=`/`apikey=`/`api_key=`/`password=`, case-insensitive
  key, up to the next `&`/whitespace/end-of-string) with `***`, leaving the key name and overall
  structure intact. Not yet wired into any real call site — no code in this module currently logs
  free-form rendered content or an exception message that might embed one of these patterns (the
  only future consumer, `DeliveryOrchestrator`, is task 11's own scope) — same "seam built ahead of
  its caller" shape as `NotificationDispatcher` (T06), `PreferenceResolver` (T08), `TemplateRenderer`
  (T09).

## Dependencies

None beyond the JDK's own `java.util.regex`. No Spring context, no Docker, no DB.

## Acceptance Criteria

1. **AC1.** `SecretSafeLogging.redact(String text)` masks the *value* half of any
   `token=`/`secret=`/`apikey=`/`api_key=`/`password=`-shaped substring (case-insensitive key name)
   with `***`, preserving the key name.
2. **AC2.** Multiple distinct secret-shaped substrings in one input are all masked.
3. **AC3.** Text containing no secret-shaped substring is returned unchanged (byte-for-byte).
4. **AC4.** `redact(null)` returns `null` — a defensive, lenient contract (this is an
   observability/error-logging utility; a `NullPointerException` here would risk masking the
   *original* error a caller was trying to safely log), unlike `PreferenceResolver`/`TemplateRenderer`'s
   own stricter caller-contract precedent for their own core business-logic arguments.
5. **AC5.** A permanent, reflection-based test asserts every class under
   `com.themistra.notification` with a field literally named (case-insensitively) `token`, `secret`,
   `password`, `apiKey`, or `key` declares its own `toString()` override — locking the
   already-established per-class discipline (T06/T09) against a future regression, not merely
   documenting it. **Known, disclosed limitation:** a field-name heuristic cannot catch a
   semantically-sensitive-but-ambiguously-named field (e.g., `RenderedMessage.body`/`subject`,
   which legitimately embeds a token as rendered text, not a literally-named secret field) — T09's
   own equivalent gap was caught by Kimi's adversarial review, not by any automated scan, and this
   task's own AC5 does not close that class of gap either. Disclosed, not fixed, here.

## Tests required

No named test in `package.md` §8 maps directly to this task (its 19 named tests cover R1-R19/L11;
R15's own named test, `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs`, is listed but —
per its own wording — is more naturally satisfied by the per-class tests already written in T06/T09
than by anything new this task's own `SecretSafeLogging` utility itself needs to prove). Required
here: `redact()`'s own correctness (AC1-AC4) and the reflection-based field-scan guard (AC5).

## Open Questions

No blockers. Phase 0's own two carried-forward questions are resolved here: (1) `SecretSafeLogging`'s
own real value-add is the `redact()` utility (new, reusable infrastructure) plus the reflection-based
scan (a permanent guard on the already-established discipline) — not a duplicate of what per-class
`toString()` overrides already do; (2) the checklist-vs-L4 phrasing inconsistency is resolved in
L4's own favor, stated explicitly above, not silently picked.
