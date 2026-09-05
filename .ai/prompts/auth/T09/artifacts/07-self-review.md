# auth · T09 — Phase 7: Self-Review

Reviewing the Phase 6 diff (`AccountService.java`, `RegisterAccountRequest.java`) against the
frozen brief and `agents.md`. No rewriting — findings only, fixes are Phase 9's job.

---

### Finding 1 — HIBP network call now runs inside a `@Transactional` method on a public, unrated-limited endpoint

**Severity:** Medium

**Issue:** `PasswordPolicy.validate` calls `BreachCheckClient.isBreached(...)` — an outbound HTTPS
request to the HIBP range API, timeout `themistra.auth.password.breach-check.timeout-ms=3000` —
from inside `AccountService.register`, which is `@Transactional`. This was explicitly forecast at
T08 Phase 12 as an accepted trade-off ("will recur when task 9 wires the same call into
register/resetPassword"), but `register` specifically is a new exposure beyond what T08 accepted:
it is `POST /accounts`, unauthenticated, and — unlike `resetPassword` — not one of the endpoints
`requirements.md` R41 names for per-account rate limiting (`login`, `/oauth2/token`,
`password-reset confirmation`, `MFA verification`; registration is absent from that list). A DB
connection is now held open for up to 3 seconds per registration attempt whenever the breach-check
API is slow, on an endpoint with no requirement-mandated backstop against high request volume.

**Evidence:**
- `AccountService.java:87` (`register`) and `AccountService.java:203` (`resetPassword`) — both
  newly call `passwordPolicy.validate(...)` inside their existing `@Transactional` method bodies.
- `PasswordPolicy.java:68-74` (`validateNotBreached`) — the network call this triggers.
- `services/auth/src/main/resources/application.properties:65` — `breach-check.timeout-ms=3000`.
- `requirements.md:65` (R41) — registration is not in the rate-limited endpoint list;
  `password-reset confirmation` is, partially mitigating `resetPassword`'s exposure but not
  `register`'s.

**Recommendation:** Not a defect in this task's scope — `PasswordPolicy` itself and its
transaction/HTTP interaction were out of scope for T09 (frozen brief: `PasswordPolicy.java` is in
Files NOT to Modify), and the trade-off was already accepted in principle at T08. Flag for the
task/spec author as a candidate for a follow-up: either move the breach-check call outside the
transactional boundary (e.g., validate before opening the transaction, which would require
restructuring `@Transactional` placement across the module) or add `POST /accounts` to R41's
rate-limited set. No code change recommended here — noting for the record per this phase's
guardrail ("if something looks wrong, log it, don't deviate silently").

---

### Finding 2 — Duplicate-email registration attempts now incur breach-check latency they previously avoided

**Severity:** Low

**Issue:** Before this task, `register` checked `existsByEmail` first and returned
`DuplicateEmailException` near-instantly for a known-taken email, without ever calling
`passwordEncoder.encode` or any network service. After this task's reorder (required by Finding
2/AC4 of the frozen brief, to close the enumeration-safety gap), every registration attempt —
duplicate or new — now runs `passwordEncoder.encode` and `passwordPolicy.validate` (including the
HIBP round-trip) before the duplicate check is ever reached. This is the explicitly accepted,
human-approved trade-off from Phase 4 (an extra BCrypt encode on the duplicate path), but the
Phase 4 discussion focused on the BCrypt cost specifically and didn't separately call out the HIBP
network latency now also being paid on every duplicate-email attempt (previously zero network
calls on that path, now up to the full breach-check timeout).

**Evidence:**
- `AccountService.java:86-91` — `Account.register(...)` (includes `passwordEncoder.encode`) and
  `passwordPolicy.validate(...)` (includes the HIBP call) both now precede
  `accountRepository.existsByEmail(email)`.

**Recommendation:** No action needed — this is the correct, intended behavior per the frozen
brief's Finding 2 resolution (L5 enumeration safety requires the policy check to run before the
existence branch, unconditionally). Recorded here only so the latency-profile change is visible to
a reviewer, not because it should be reverted.

---

## Areas reviewed with no findings

- **Correctness / boundary conditions:** both new call sites pass the exact raw password and a
  real, non-null `accountUuid`/`actorUuid` pair; `PasswordPolicyTest`'s existing boundary coverage
  (12/128-char edges) is unaffected since `PasswordPolicy` itself is unchanged.
- **Null-safety:** `request.password()` / `newPassword` are guaranteed non-blank by `@NotBlank` +
  `@Valid` at the controller boundary before either service method's body runs;
  `account.getAccountUuid()` (register) and the already-unwrapped `accountUuid` local
  (resetPassword) are both guaranteed non-null at the point `validate` is called, satisfying
  `PasswordPolicy.validate`'s `Objects.requireNonNull` guards.
- **Thread-safety:** `PasswordPolicy` is a stateless singleton bean; no new mutable state
  introduced.
- **Module boundaries:** no new imports, no cross-module entity access; `PasswordPolicy` was
  already an `account`-package collaborator of `AccountService` since T08.
- **Idempotency:** unaffected — `register`'s real duplicate guard remains the DB unique
  constraint; `resetPassword`'s token consumption remains `markConsumed`'s atomic conditional
  update, unchanged.
- **Money types:** not applicable to this task.
- **Enumeration-safety (beyond Finding 1/2 above):** `resetPassword`'s accepted residual
  token-validity signal (frozen brief Finding 3) is implemented exactly as decided — no
  implementation deviation from what was approved at Phase 4.
- **Secret-handling:** no new logging of passwords, tokens, or hashes; `PasswordPolicyViolationException`'s
  messages describe the password's own content (length, breach status) — no different in kind from
  what `changePassword` already discloses to an authenticated caller since T08, and disclosing
  *why* a submitted password was rejected to the same caller who typed it is not an
  enumeration-safety or secret-handling concern.
- **Readability/complexity:** the two new Javadoc paragraphs explain non-obvious ordering
  decisions (why validate precedes `existsByEmail`; why the resetPassword ordering was chosen and
  what residual risk was accepted) — consistent in density with this file's existing Javadoc style
  for `resetPassword` and `activateFromVerificationToken`.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 9.
- Requirements: R8, R9, R10 (implemented, both call sites); R41 (referenced in Finding 1, not
  modified — out of scope).
- LOCKED decisions: L2 (unchanged), L5 (upheld — see Finding 2's confirmation, not a defect).
