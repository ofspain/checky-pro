<!-- MODEL: Claude — Phase 9 (Review Resolution, human-gated). -->

# notification · T12 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T12 — Email channel (O2/Q2) |
| **Consumes** | `artifacts/08-independent-review.md` (Kimi) |
| **Produces** | `artifacts/09-review-resolution.md` |

Disposition of all 10 Phase 8 findings (which substantially overlap the Phase 7 self-review's own 5
findings — cross-referenced below). All code fixes were applied directly, compiled
(`mvn -pl services/notification test-compile`), and verified against the full suite
(`mvn -pl services/notification clean verify` → 236 tests, 0 failures, 0 errors — same count as
before this batch; no new tests were added at this phase, only production-code fixes).

---

## Finding 1 · No `EmailChannel`/`SesEmailTransport`/etc. tests exist

**Disposition: DEFERRED to Phase 10.** Matches this pipeline's own unbroken precedent (T09/T10/T11
each deferred their own equivalent finding identically) — Phase 6/7/8/9 focus on implementation and
review; the committed test suite is Phase 10's own scope.

---

## Finding 2 · `EmailChannel` logs the recipient email address, which is PII

**Disposition: ACCEPTED, FIXED.** Verified directly against `agents.md:53-54`
("Never log tokens, secrets, reset-token values, full API keys, or PII") — a real, unambiguous
violation. Also checked whether this was a pre-existing pattern elsewhere in the codebase before
fixing: `NoOpInAppChannel`'s own similar-looking `recipient={}` log line is **not** the same
violation — for `IN_APP`, `recipient` is the account UUID's own string form (T11's established
convention), not an email address, so it carries no PII. Only `EmailChannel`'s own `recipient` (a
real email address) was affected.

**Fixed**: `EmailChannel.send`'s own log line now names only `accountUuid`/`messageId` — never
`recipient`. Additionally (beyond what this finding literally asked for): `EmailMessage.toString()`
had the identical latent problem (`to=` printed the real email address) — fixed in the same pass by
dropping `to` from `toString()` entirely and adding `accountUuid` (safe, not PII) in its place.

---

## Finding 3 · `SesEmailTransport` request construction is outside the `try/catch`

**Disposition: ACCEPTED, FIXED** (identical to self-review Finding 1). Took Kimi's own preferred
"Option 1" (move construction inside `try`) over Option 2 (validate `body` only) — broader
protection against any future builder failure mode, not only a null body. The catch clause itself
was also broadened from `catch (SdkException e)` to `catch (RuntimeException e)` so a non-SDK
failure during construction (e.g. a hypothetical NPE) is sanitized identically, not just an
`SdkException`.

---

## Finding 4 · `EmailChannel` does not validate `message.body()`

**Disposition: ACCEPTED, FIXED** (identical to self-review Finding 4, and to Kimi's own Finding 4
independently). `EmailChannel.validate` now throws `IllegalArgumentException` for a null/blank
`body`, matching the existing `subject` check exactly.

---

## Finding 5 · `FakeEmailTransport.findByRecipient` semantics are unclear and inefficient

**Disposition: ACCEPTED (naming), REJECTED (early-exit optimization).** Renamed to
`findMostRecentByRecipient` with an explicit Javadoc stating the intended "most recent, not first"
semantics (already the actual behavior — confirmed by reading the loop directly before touching
it). The early-exit/reverse-iteration optimization was **not** applied: this list only ever holds a
handful of test-dispatched messages, so the O(n) full-scan cost is negligible, and an early-exit
implementation would be marginally more complex for zero real-world benefit here — not worth the
added complexity for a test-only capturing list.

---

## Finding 6 · `FakeEmailTransport` does not capture `accountUuid`

**Disposition: ACCEPTED, FIXED.** `EmailMessage` gained an `accountUuid` field (threaded from
`EmailChannel.send`'s own already-available parameter — no new dependency needed). This also
directly enabled Finding #2's own fix (logging `accountUuid` instead of `recipient`) and closed the
identical latent PII leak in `EmailMessage.toString()` noted under Finding #2 above.

---

## Finding 7 · `SesEmailTransport` does not log at all

**Disposition: ACCEPTED, FIXED.** Added `DEBUG`-level logs before the SES call attempt and on
success (`accountUuid`/`messageId` only — never recipient/subject/body, consistent with Finding #2's
own PII rule applied to this new call site too, not only the one Kimi explicitly flagged).

---

## Finding 8 · Invalid `transport` values produce a generic Spring DI error at startup

**Disposition: ACCEPTED, FIXED** (identical to self-review Finding 5, independently confirmed by
Kimi). This reverses the Phase 4 frozen brief's own explicit deferral of this exact idea ("no
finding asked for this... deferred as a future task's own scope") — at Phase 4, no review had yet
asked for it; now that **both** the self-review and this independent review converged on the same
recommendation, implementing it is honoring two independent findings, not scope creep. Added
`common/config/EmailTransportStartupValidation.java`, mirroring
`LinkPropertiesStartupValidation`'s own established pattern exactly, but unconditional (no
`@Profile` guard) since no environment ever legitimately permits a third value.

---

## Finding 9 · The external network call holds a DB connection/transaction open

**Disposition: ACCEPTED-AS-DOCUMENTED, no code change** (identical to self-review Finding 3, and to
Kimi's own recommendation). A real fix would mean revisiting T11's own already-frozen transaction
boundary — out of this task's own scope.

---

## Finding 10 · Duplicate send risk if the transaction aborts after a successful SES call

**Disposition: ACCEPTED-AS-DOCUMENTED, no code change** (identical to self-review Finding 2, and to
Kimi's own recommendation). Low-harm for `verify_email`/`password_reset` (a duplicate re-delivers the
same still-valid link). A real fix would mean an outbox or redesigning T11's own transaction
boundary — out of this task's own scope.

---

## Summary of code changes this phase

- `channel/EmailMessage.java` — added `accountUuid` field; `toString()` now excludes `to` as well as
  `subject`/`body` (a PII leak this task's own review didn't explicitly name but shares the exact
  same root cause as Finding #2).
- `channel/EmailChannel.java` — `send` passes `accountUuid` into `EmailMessage`; success log drops
  `recipient`; `validate` now also checks `body`.
- `channel/SesEmailTransport.java` — request construction moved inside `try`; catch broadened to
  `RuntimeException`; added `DEBUG` logs (PII-free) before/after the SES call.
- `channel/FakeEmailTransport.java` — `findByRecipient` renamed to `findMostRecentByRecipient` with
  explicit semantics documented.
- `common/config/EmailTransportStartupValidation.java` (**new**) — fails startup with a clear
  message if `transport` is anything other than `ses`/`fake`.
- `T01SkeletonRegressionTest.java` — authorized file list updated (39 files).

No test files were added or modified this phase (Finding #1 defers all new tests to Phase 10).
Full suite: 236 tests, 0 failures, 0 errors — unchanged count, confirming these are pure
production-code fixes with no test-visible regressions.
