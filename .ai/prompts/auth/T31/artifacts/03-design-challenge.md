<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T31 · Phase 3 — Design Challenge

Consumes `artifacts/02-task-implementation-brief.md`. Adversarial review of the T31 rate-limiting brief before the Phase 4 freeze. Findings only — no redesign, no implementation.

---

## Finding 1 — MFA verification is omitted despite being an explicit R41 path

**Severity:** High

**Evidence:** `spec/auth-service/requirements.md:65` (R41) lists four paths: "login, `/oauth2/token`, password-reset confirmation, **or MFA verification**." `design.md` §4b-O2 also asks for thresholds for "login, `/oauth2/token`, password-reset confirm, **and MFA verify**." The TIB's OQ2 resolves MFA verification as "covered by construction" because TOTP/recovery-code verification happens inside `/login`. This is a pragmatic technical observation, but it is not what R41 or design.md O2 say. The current brief therefore implements three of the four explicitly named paths and reinterprets the fourth away without a Phase 4 decision.

**Recommended brief amendment:** Either (a) add MFA verification as a fourth independently rate-limited path with its own threshold and bucket key (e.g., account UUID after the first `/login` step succeeds, or the same login-path key if it truly must share), or (b) add an explicit Phase 4 decision (D1) that records why MFA verify is intentionally folded into `/login` despite R41's wording, and updates the acceptance criteria/tests to reflect only three paths. Do not leave the discrepancy silent.

---

## Finding 2 — Login-path key derivation contradicts the enumeration-safety requirement

**Severity:** Medium

**Evidence:** The brief states the 429 body must not leak whether the account exists, and that "the bucket key derivation happens before any existence check" (`Constraints` / `Security`). Yet the `Dependencies` list says the login-path bucket key comes from `AccountService.findLoginView(...)`, and the `Files to Create` description says the limiter class uses `AccountService` for the login key. `findLoginView` is an account lookup; by definition it performs an existence check. If the lookup is required to derive the key, the limiter cannot both derive the key before any existence check and use `findLoginView`.

**Recommended brief amendment:** Decide the login keying strategy explicitly: (a) key purely on the raw username/email from the request (no DB lookup, true pre-check enumeration safety, but buckets for non-existent emails consume memory), or (b) accept that the login limiter performs an account lookup and document the resulting enumeration trade-off. If (b), clarify whether `findLoginView`'s "account not found" outcome still produces a bucket key (e.g., a deterministic synthetic key for unknown usernames) so fabricated usernames get rate-limited too.

---

## Finding 3 — `/oauth2/token` keying by refresh-token hash is not per-account

**Severity:** Medium

**Evidence:** R41 says "per-account request rates." The brief resolves OQ3 by keying the `/oauth2/token` limiter on the SHA-256 hash of the presented refresh token, arguing this is a "legitimate account-proxy" for the refresh-token grant. A single account with multiple active sessions has multiple refresh tokens, so this gives the account 30 req/min *per session*, not 30 req/min per account. This is materially different from "per-account" and could allow a merchant with 10 sessions to make 300 req/min to `/oauth2/token`.

**Recommended brief amendment:** Add a Phase 4 decision explicitly accepting per-token keying as a deliberate narrowing of "per-account" for the refresh-token grant, or change the key to the account UUID resolved from the refresh token (which may require looking up the family/authorization first — a trade-off of its own). Update AC4/AC8 and tests accordingly.

---

## Finding 4 — Rate-limit check timing relative to credential validation is unspecified

**Severity:** Medium

**Evidence:** The brief does not state whether the limiter runs before or after credential validation for `/login` and password-reset. If the check is after credential validation, an attacker can force the service to perform password hashing/verification work indefinitely (CPU exhaustion) until the bucket exhausts — weakening the rate limiter's value as a DoS backstop. If the check is before credential validation, a legitimate user can be locked out by an attacker who doesn't know the password. Both positions are defensible, but the choice has security and UX consequences.

**Recommended brief amendment:** State explicitly that the limiter runs *before* credential validation for DoS-backstop effectiveness (R42), and that this is acceptable because the per-account bucket is not a permanent lockout (AC5). Alternatively, if the choice is after validation, document the rationale and accept the weaker DoS protection.

---

## Finding 5 — No `Retry-After` header specified for 429 responses

**Severity:** Low

**Evidence:** The brief says the 429 body is `application/problem+json` and matches the codebase's other rejection shapes, but it does not mention the `Retry-After` header. RFC 6585 (and good API hygiene) recommends including `Retry-After` with 429 responses so clients can back off deterministically. Bucket4j can provide the exact nanoseconds-until-refill, making this trivial to add.

**Recommended brief amendment:** Include `Retry-After` in the 429 response spec (either as a seconds value or an HTTP-date), add it to AC7, and add a test asserting its presence and approximate correctness.

---

## Finding 6 — Fail-open vs fail-closed for limiter internal errors is unspecified

**Severity:** Medium

**Evidence:** The brief does not state what happens if the rate-limiter itself throws (e.g., `ConcurrentHashMap` corruption, Bucket4j misconfiguration, or a key-derivation failure). A fail-open choice (allow the request) avoids accidental self-DoS but weakens R41. A fail-closed choice (return 500 or 429) is safer for security but could lock all users out if the limiter breaks. The codebase's standing rule on fail-open/fail-closed for security features (see `agents.md` and prior tasks) should be applied here.

**Recommended brief amendment:** Add a constraint: if the limiter throws, the request is allowed and an error is logged/metric-recorded (fail-open with observability), or the request is rejected with 429 (fail-closed). Whichever is chosen, document it and test it.

---

## Finding 7 — Window/bucket algorithm is unspecified

**Severity:** Low

**Evidence:** The brief uses "10 requests/minute" as a threshold but does not specify whether this is a fixed window, sliding window, or token bucket with refill. Bucket4j supports all three. This affects testability (when exactly does a throttled client become allowed again?) and user-visible behavior (burst tolerance).

**Recommended brief amendment:** Specify the algorithm explicitly, e.g., "token bucket with capacity = threshold and refill rate = threshold per 60 seconds" or "sliding 60-second window." Add the refill/window semantics to the acceptance criteria so tests can assert AC5 precisely.

---

## Finding 8 — Memory growth of per-key buckets is acknowledged but not decided

**Severity:** Low

**Evidence:** The brief's `Constraints` section notes that an unbounded map of buckets could grow indefinitely and suggests Phase 5 consider a bounded/expiring cache. This is a real operational risk: under a distributed attack with many distinct emails or refresh-token hashes, the JVM could OOM. The brief defers the decision rather than making it.

**Recommended brief amendment:** Add a Phase 4 decision either (a) accepting unbounded growth as a known backstop limitation (with a note that ingress is the primary defense, R42), or (b) requiring a bounded cache (e.g., Caffeine with `maximumSize` and/or `expireAfterAccess`) for the per-key bucket registry.

---

## Finding 9 — `client_credentials` and `authorization_code` scoping is binary but other grants are not mentioned

**Severity:** Low

**Evidence:** AC8 says `client_credentials` and `authorization_code` grant requests to `/oauth2/token` are unaffected. The brief does not mention the `password` grant (if SAS is configured to allow it) or any custom grant. If a `password` grant is enabled, it is logically closer to `/login` than to `refresh_token` and probably should be rate-limited too.

**Recommended brief amendment:** Explicitly state which grant types are covered, unaffected, or out-of-scope. If `password` grant is disabled in this deployment, document that assumption.

---

## Non-Issues Confirmed

- **Library choice:** Bucket4j 8.10.1 JDK17 variant is a reasonable choice for a Java 21 service.
- **No persistence:** in-process ephemeral buckets align with `design.md` O2 and R42's backstop framing.
- **429 body format:** `application/problem+json` is consistent with the rest of the service.
- **Complementarity with lockout:** the brief correctly scopes out `lockout_state` changes.
- **ProblemTypes addition:** a new constant for 429 is appropriate.
- **Module boundaries:** relying on public `AccountService` and `Hashing` (common) avoids repository leakage.

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (Freeze Task Brief / Human Approval) on approval.
