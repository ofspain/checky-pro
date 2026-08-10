# auth · T24 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief and `agents.md`. Findings only.

---

## 1. `exchange`'s audit-target resolution misattributes revoked/expired rejections when multiple candidates share a prefix

- **Issue.** In the `!eligible` branch, `auditAccountUuid` is resolved from `candidates.getFirst().getAccountId()` (`ApiKeyService.java:163`) unconditionally — even when `matched` (line 150-156) is a specific, different, non-null candidate whose hash *did* match but whose row is revoked or expired. If more than one row shares a prefix (architecturally possible — `prefix` has no DB-level `UNIQUE` constraint, and T23's own test suite explicitly covers this multi-match case), a revoked-key-usage attempt against account B could be audited against account A instead, purely because A's row happened to come first in `findByPrefix`'s result order. In the overwhelmingly common real-world case (0 or 1 candidate per prefix) this has zero effect, but it's a latent correctness defect the moment a genuine collision occurs — exactly the scenario this codebase has repeatedly designed around defensively elsewhere (T23's `List`-not-`Optional` return type exists *because* this case is possible).
- **Severity.** Medium — security-audit-trail accuracy, not an authorization bypass (the exchange success/failure decision itself is unaffected; only which account's audit trail records the rejection is wrong).
- **Evidence.** `ApiKeyService.java:150-167`.
- **Recommendation.** When `matched != null` (a specific row's hash matched but it's revoked/expired), audit that row's account (`matched.getAccountId()`), not `candidates.getFirst()`. Only fall back to `candidates.getFirst()` when `matched == null` (a genuine hash-mismatch across every candidate, where no single row can be identified as "the intended one").

## 2. `revoke` always records an audit event, even when `revokeIfActive` made no change

- **Issue.** `ApiKeyService.revoke` (`:110-119`) calls `apiKeyRepository.revokeIfActive(...)` and discards its return value, then unconditionally records `api_key.revoked`/`SUCCESS`. Revoking an already-revoked key therefore produces a duplicate audit event describing a state change that didn't actually happen. This directly contradicts this codebase's own established idempotency convention: `RoleService.assignRole`/`removeRole` explicitly skip the audit call on a no-op ("idempotent: already assigned, nothing changed, nothing to audit" — that method's own comment). `ApiKeyRepository.revokeIfActive`'s own Javadoc (`ApiKeyRepository.java:55-59`) even claims to mirror that exact precedent, but the calling code doesn't honor it.
- **Severity.** Medium — not a security defect, but a real inconsistency between documented intent, established codebase convention, and actual behavior; produces misleading audit history (repeated "revoked" events for one actual revocation).
- **Evidence.** `ApiKeyService.java:117-118`; `RoleService.java` (`removeRole`'s idempotent-no-op-skips-audit pattern); `ApiKeyRepository.java:55-59`'s own Javadoc claim.
- **Recommendation.** Check `revokeIfActive`'s return value; only call `recordAudit(...)` when it returns `1` (an actual state change), matching `RoleService`'s precedent exactly.

## 3. `exchange`'s malformed-key and unknown-prefix rejection paths skip the constant-time comparison entirely, creating a coarser timing signal

- **Issue.** The malformed-input check (`:137-141`) and the unknown-prefix check (`:144-148`) both return immediately, without ever calling `apiKeyHasher.matches(...)`. This is measurably faster than the well-formed-but-wrong-secret path, which always runs at least one constant-time hash comparison. An observer timing many exchange attempts could in principle distinguish "obviously malformed / unknown prefix" from "well-formed key, wrong secret" — a real, if low-value, timing signal, given the class's own stated goal ("closing the timing side-channel... disposition #4") is specifically about *not* leaking exactly this kind of information.
- **Severity.** Low — the practical value of this signal to an attacker is small (it mostly just confirms whether a presented string matches the expected key *shape*, not whether any part of its content is correct), and unlike finding #1, this doesn't misattribute anything or affect any security decision. Noted for completeness, not as a required fix.
- **Evidence.** `ApiKeyService.java:137-148` vs. `:150-156`.
- **Recommendation.** Optional: perform a dummy constant-time comparison against a fixed placeholder hash on the malformed/unknown-prefix paths before rejecting, to normalize timing. Given the low practical value, this is a candidate for "no action" rather than a required fix — flagging for Phase 8's independent judgment rather than deciding unilaterally here.

## 4. No other correctness, boundary, null-safety, thread-safety, transaction, module-boundary, idempotency, secret-handling, or complexity issues found

- `create`'s three preconditions (status, role, MFA) are all independently checked, not assumed, per the frozen brief's disposition #6.
- `ApiKeyRepository`'s two conditional `@Modifying` methods (`updateLastUsedAt`, `revokeIfActive`) correctly avoid load-mutate-save races; `create`/`revoke`/`exchange` all run inside `@Transactional`, so the `REQUIRES_NEW` audit calls inside them commit correctly (unlike the test-only concern this codebase has previously documented — that was specific to test code lacking a surrounding transaction, not applicable here).
- No `Account` entity import anywhere (L12) — confirmed both by inspection and by `ArchitectureTest` passing in Phase 6's verification run.
- `CreateApiKeyResult`'s plaintext key never persists anywhere beyond its own stack frame and the caller's return value; its `toString()` is overridden to prevent accidental logging, matching `MfaService.BeginEnrollResult`'s precedent. `ApiKeyMetadata` has no hash/secret field by construction.
- `SecureRandom` is documented thread-safe by the JDK; sharing one field-level instance across concurrent `create` calls (matching `MfaService`'s own precedent) is correct.
- `ApiKeyMetadata`/`ExchangeResult` skip compact-constructor null-checks that `CreateApiKeyResult` has — deliberately consistent, not an oversight: both are internal service-to-service data built only from already-validated entity state (`toMetadata`/`exchange` themselves), the same reasoning `RecordAuditEventRequest`'s own Javadoc gives for skipping validation on internal-only records.

No `agents.md` or LOCKED-decision violations found.
