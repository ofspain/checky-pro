# auth · T03 — Design Challenge Findings

Adversarial review of `artifacts/02-task-implementation-brief.md`. Findings are numbered but unordered by default; priorities are noted in each header's severity tag.

## Finding 1 — HIBP range API requires a `User-Agent` header (HIGH)

**Issue:** The brief tells `BreachCheckClient` to call `https://api.pwnedpasswords.com/range/{prefix}` without requiring a `User-Agent` request header. HIBP's published API documentation states that every request must include one and that a missing user agent results in an HTTP 403 response.

**Severity:** HIGH — a 403 from HIBP would be treated as an API failure under R10's fail-open rule, silently allowing every password to pass breach screening.

**Evidence:**
- HIBP API docs, "Specifying the user agent": "Each request to the API must be accompanied by a user agent request header ... A missing user agent will result in an HTTP 403 response."
- TIB business rules (lines 41–45) and config (lines 61–66) describe the URL prefix and SHA-1 prefix but never a header.

**Recommended brief amendment:** Add a requirement that `BreachCheckClient` sets a static `User-Agent: Themistra-Auth-Service/1.0` (or similar) header on every outbound request, and add a test assertion that the mock request includes it.

---

## Finding 2 — Audit outcome and caller context for `password.breach_check_failed` are undefined (HIGH)

**Issue:** R10 requires recording a `password.breach_check_failed` audit event when the range API is unreachable, but the brief does not say what `AuditOutcome` to use or how to populate the required audit context fields.

**Severity:** HIGH — without this, the named test cannot assert the correct row, and the produced audit row may violate R43's expectation that every security-relevant action records actor, target, outcome, and correlation id.

**Evidence:**
- `RecordAuditEventRequest` (lines 12–21) carries `accountUuid`, `actorUuid`, `ip`, `rawUserAgent`, and `traceId`, none of which `PasswordPolicy` receives.
- `AuditOutcome` (lines 3–5) only has `SUCCESS`/`FAILURE`; the brief never selects one.
- TIB Inputs (lines 73–74): candidate raw password is only a `String`.
- `requirements.md` R43 (line 70): every security-relevant action is recorded with actor, target, outcome, and correlation id.

**Recommended brief amendment:**
- State that the R10 audit event uses `AuditOutcome.FAILURE` (the breach check failed, even though the password is allowed).
- State that T03 records a system/account-less audit row because `PasswordPolicy` is not yet wired to any endpoint that has actor/target context; task 9 should introduce an overloaded call or context object when account information is available.

---

## Finding 3 — `package.md` §8 maps the named tests to the wrong requirement IDs (MEDIUM)

**Issue:** The Phase 3 prompt says scoped requirements are `R8`, `R9`, `R10` per `requirements.md`, but `package.md` §8 labels the same three tests as `R11`, `R12`, `R13`.

**Severity:** MEDIUM — a future implementer or reviewer tracing named tests to acceptance criteria will land on the wrong requirements.

**Evidence:**
- `requirements.md` lines 17–19: R8 = length, R9 = HIBP range, R10 = fail-open audit.
- `package.md` lines 88–90: same tests map to R11/R12/R13.

**Recommended brief amendment:** Add an Open Question / note to the brief stating that `package.md` §8 currently disagrees with `requirements.md` and that the human author should reconcile them in Phase 4.

---

## Finding 4 — Mirrored Kafka audit event type will be prefixed with `security.` (MEDIUM)

**Issue:** The brief says the audit event type is `password.breach_check_failed`, but `AuditService` publishes the mirror event with `type = "security." + request.eventType()`. Downstream consumers and the future `security-audit.v1.schema.json` will see `security.password.breach_check_failed`.

**Severity:** MEDIUM — a later contract test may assert the literal event type and fail, or the contract may be authored against the wrong value.

**Evidence:**
- `AuditService.java` line 62: `outboxPublisher.publish(... "security." + request.eventType() ...)`. The stored `AuditEvent.eventType` remains `password.breach_check_failed`.
- TIB line 24: names the event type `password.breach_check_failed` without distinguishing DB vs Kafka representations.

**Recommended brief amendment:** Add a note that the DB row stores `password.breach_check_failed`, while the outbox/Kafka mirror stores `security.password.breach_check_failed` via the existing `AuditService` path; task 33 (`security-audit.v1.schema.json`) must account for the prefixed type.

---

## Finding 5 — SHA-1 implementation location is left as an open choice with scope risk (MEDIUM)

**Issue:** The brief says SHA-1 can live in `common.Hashing` or locally in `BreachCheckClient`. Adding SHA-1 to `common.Hashing` expands a shared utility with a weak hashing primitive and touches a module outside `authn`.

**Severity:** MEDIUM — the wrong choice creates unnecessary review surface, potential reuse of SHA-1 elsewhere, and a module-boundary change the ArchUnit test was not designed to guard.

**Evidence:**
- TIB lines 57–59: "needs adding (home for it — `Hashing` vs. local to `BreachCheckClient` — is an implementation choice, not fixed by this brief)."
- `Hashing.java` (lines 12–25) only exposes `sha256` and was moved here for user-agent hashing; its javadoc does not expect weak-hash additions.

**Recommended brief amendment:** Lock the SHA-1 digest to a private method inside `BreachCheckClient` (or a dedicated `authn`-scoped utility), explicitly adding it to `common.Hashing` out of scope for T03.

---

## Finding 6 — Bounded HIBP timeout is required but not configurable (MEDIUM)

**Issue:** The brief correctly notes that R10's fail-open guarantee is meaningless without a bounded timeout, but no timeout value or config key is provided.

**Severity:** MEDIUM — every implementer will choose a different value, making the acceptance criterion non-deterministic and the production hardening invisible.

**Evidence:**
- TIB Constraints lines 137–140: "the HIBP call must be made with a bounded timeout ... No timeout value is specified ... a reasonable default must be chosen."
- TIB config block lines 61–66 has no timeout key.

**Recommended brief amendment:** Add `themistra.auth.password.breach-check.timeout-ms=3000` (or similar) to the verbatim config block, document the default, and add a test that simulates a timeout to prove fail-open works.

---

## Finding 7 — Failure of the audit write itself is not addressed (MEDIUM)

**Issue:** R10 says "allow the password change and record a `password.breach_check_failed` audit event." The brief explains what happens if HIBP is down, but not what happens if the audit write fails (e.g., DB unavailable) when `PasswordPolicy` is later called inside a task-9 service transaction.

**Severity:** MEDIUM — if the audit call propagates, the password change could be rolled back, contradicting the fail-open intent and the named test's "...AndAuditFailure" wording.

**Evidence:**
- `AuditService.record` (`AuditService.java` line 47) is `@Transactional` and can throw.
- TIB constraints lines 145–147: `PasswordPolicy` should not be `@Transactional`; it relies on `AuditService.record`'s own transaction boundary.

**Recommended brief amendment:** Decide and document whether `PasswordPolicy` must catch/log audit failures to guarantee the password is always allowed, or whether audit write failures should propagate. Add a corresponding test.

---

## Finding 8 — HIBP response parsing edge cases are unspecified (MEDIUM)

**Issue:** The brief says HIBP returns newline-delimited `SUFFIX:COUNT` pairs, but it does not define behavior for blank lines, suffix case, whitespace, count parsing, or malformed lines.

**Severity:** MEDIUM — an over-strict parser could break on a trailing newline or HIBP format change; an under-strict parser could miss a breach.

**Evidence:**
- TIB Inputs lines 75–76: "HTTP response body (newline-delimited `SUFFIX:COUNT` pairs — HIBP's published convention, not documented in the spec package) into `BreachCheckClient`."

**Recommended brief amendment:** Require the parser to:
- ignore blank/whitespace-only lines;
- compare suffixes case-insensitively (normalize both sides to uppercase); and
- treat any line without a positive integer count as non-breach.
Add tests for trailing newline, count `0`, and lowercase suffix.

---

## Finding 9 — Null/blank password handling is underspecified (LOW)

**Issue:** The brief states `PasswordPolicy` must not silently NPE on null/blank input, but it does not say what exception is thrown or whether null is treated as a length violation.

**Severity:** LOW — easily resolved, but the named test suite does not cover it unless specified.

**Evidence:**
- TIB Constraints line 152: "Null handling: `PasswordPolicy` must not silently NPE on a null/blank candidate password."

**Recommended brief amendment:** Specify that null/blank passwords are rejected with the same domain-specific policy exception used for length violations, and add a unit test for it.

---

## Finding 10 — Task 3 assumes responsibility for task 4's audit wiring without clarifying task 4's remaining scope (LOW)

**Issue:** The brief explicitly includes the R10 audit call in task 3 because the named test requires it, while `tasks.md` keeps task 4 as a separate "Breach-check audit event" task. This could leave task 4 empty or duplicate work.

**Severity:** LOW — manageable, but a sequencing hazard for project tracking.

**Evidence:**
- TIB Scope lines 23–27: "Treating task 4 as satisfied by this task's implementation rather than deferring the audit call. **Assumption — flag if wrong.**"
- `tasks.md` lines 9–10 lists task 3 and task 4 separately.

**Recommended brief amendment:** Either state that task 4 is fully satisfied by T03 and should be marked done, or state that T03 only records the minimal event using the existing `AuditService` API and task 4 remains responsible for stabilizing event-type constants, payload schema, and integration tests.

---

## Finding 11 — Existing `@Size(12,128)` on `RegisterAccountRequest.password` will conflict with task 9 wiring (LOW)

**Issue:** The brief correctly leaves the DTO unchanged, but when `PasswordPolicy` is wired into registration in task 9, the duplicate length validation could reject the same input in two places or cause mismatched error handling if config values ever change.

**Severity:** LOW — not a T03 blocker, but a hidden dependency that should be tracked.

**Evidence:**
- `RegisterAccountRequest.java` lines 18–20: `@Size(min = 12, max = 128)` on `password`.
- TIB Files NOT to Modify lines 107–108: keep `RegisterAccountRequest.java` unchanged for T03.

**Recommended brief amendment:** Add a task-9 note in the brief (or re-open as an Open Question) that the hardcoded `@Size` annotation must be removed or delegated to `PasswordPolicy` when the policy is wired to registration/change-password/password-reset.
