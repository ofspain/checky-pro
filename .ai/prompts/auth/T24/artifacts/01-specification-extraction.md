# auth · T24 · Phase 1 — Specification Extraction

## Business Rules

- **R30.** WHEN an authenticated user with the `MERCHANT` role and confirmed MFA calls `POST /api-keys` with a name, THEN the system SHALL create an API key with prefix `ck_live_`, store a SHA-256 hash of the full key, return the plaintext key exactly once, and record an `api_key.created` audit event. — T24 implements the underlying `create(...)` logic (role/MFA gating, key generation, hashing, persistence, audit); the actual `POST /api-keys` HTTP endpoint is T26's.
- **R32.** WHEN a valid API key is presented for exchange, THEN the system SHALL update the key's `last_used_at` timestamp. — T24's `exchange(...)` method owns this; T25 wires the HTTP endpoint around it.
- **R33.** IF an API key is revoked, expired, malformed, or the presented hash does not match the stored hash, THEN the key exchange SHALL return a uniform `401 Unauthorized`. — T24's `exchange(...)` must fail uniformly across all four causes; T25's controller/exception-handler turns that failure into the actual `401`.

Additionally, the task statement itself (not a numbered requirement) requires `list` and `revoke` methods and a `constant-time hash compare` — these have no dedicated requirement ID in `requirements.md` (R34/R35 cover the HTTP-level list/revoke behavior and are explicitly T26's, not T24's — see Open Questions) but are named directly in the task statement and therefore in scope for this task's service-layer implementation regardless.

R31 (issuing the actual 10-minute JWT on exchange) is explicitly **not** scoped to T24 — that's T25's `ApiKeyTokenIssuer`. T24's `exchange(...)` validates the presented key and returns whatever the matched, now-touched `ApiKey` (or equivalent) is; it does not mint a JWT.

## Locked Decisions

- **L7. API key format.** `ck_live_` + a random 24-character alphanumeric suffix (public prefix/lookup handle, 32 characters total) + `.` + a 32-character secret; only a SHA-256 hash of the *full* key is stored; plaintext returned exactly once. **This task cannot proceed without resolving the conflict with `api_keys.prefix VARCHAR(16)`** already identified and deferred by T23 (frozen brief disposition #1) — see Open Questions. This is the single blocking item carried into Phase 2.
- **L12. Module boundaries.** `ApiKeyService` must not import `com.themistra.auth.account.Account`; account interaction goes through `AccountService`'s public methods (UUID-keyed) or a repository-level UUID→id resolver, mirroring `MfaService`'s established pattern.

(L8–L11 — API-key JWT contract, token claims, MFA enforcement role rule, public-endpoint discipline — govern T25/T26's endpoint-level work, not this task's service logic, except insofar as L10 confirms *why* R30 requires confirmed MFA for a `MERCHANT`.)

## Files involved

**Existing, to read/extend:**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java`, `ApiKeyRepository.java` (T23) — the entity/repository this task's service sits on top of.
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` — `hasConfirmedTotpEnrollment(UUID)`, read-only, exactly what R30's MFA-confirmed precondition needs.
- `services/auth/src/main/java/com/themistra/auth/authz/RoleService.java` — `resolveEffectiveRoles(UUID)`, the only existing mechanism to check role membership outside `@PreAuthorize`.
- `services/auth/src/main/java/com/themistra/auth/audit/AuditService.java` — `record(RecordAuditEventRequest)`, for `api_key.created` (R30) and any exchange-failure audit event R33's rejection implies should exist (see Open Questions — not explicitly required by R33's text, but `agents.md`'s general "every security-relevant action is recorded" mandate, which `MfaService.confirm`'s `mfa.failed` audit on a wrong code already exemplifies as a codebase-wide pattern beyond what individual requirement IDs spell out).
- `services/auth/src/main/java/com/themistra/auth/common/Hashing.java` — `sha256(String)`; has no constant-time comparison method (genuinely new for this task).
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — `getByUuid(UUID)` for basic account existence/state checks, if needed.
- `services/auth/src/main/resources/application.properties` — `themistra.auth.api-key.prefix=ck_live_`.

**New, expected by `design.md` §6 (T24's slice):**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java` — create, list, revoke, exchange.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyHasher.java` — constant-time compare (design.md names this file explicitly).
- Exception classes for this module's failure modes (naming TBD in Phase 2) plus a `apikey/ApiKeyExceptionHandler.java` (not in design.md's list, but required by this codebase's established one-handler-per-module convention — every other module with domain exceptions has one).

Design.md's package map also lists `ApiKeyTokenIssuer.java`, `ApiKeyAuthenticationFilter.java`, `dto/`, `ApiKeyController.java` under `apikey/` — all out of scope for T24 (T25/T26).

## Dependencies

- `ApiKeyRepository` (T23) — will very likely need a new method this task adds (a UUID→internal-`accountId` resolver, mirroring `MfaEnrollmentRepository.findAccountIdByUuid`), since T23 deliberately left this out as speculative and T24 is the first real caller.
- `MfaService.hasConfirmedTotpEnrollment(UUID)`.
- `RoleService.resolveEffectiveRoles(UUID)` — pending Phase 2's resolution of which layer enforces the `MERCHANT` check (see Open Questions).
- `AuditService.record(...)`.
- `Hashing.sha256(String)`, plus a new constant-time byte-array/string comparison (likely `MessageDigest.isEqual(byte[], byte[])`, the standard JDK primitive for this — not yet used anywhere in this codebase).
- `Clock` (injected, never `Instant.now()` inline).
- Config: `themistra.auth.api-key.prefix` (already present). No new config key identified yet for a max-keys-per-merchant cap, since `package.md` §11 Q3 leaves that undecided (see Open Questions).
- `SecureRandom` — for generating the key's random suffix/secret, matching `MfaService`'s existing `SecureRandom` field pattern for recovery codes.

**Contracts named in this task's header, checked against the actual repo:** `contracts/api/auth.yaml` does not exist (`contracts/api/` is empty except `.gitkeep` — same finding as T23 Phase 1). `contracts/events/auth/email-requested.v1.schema.json` and `security-audit.v1.schema.json` also do not exist — only `contracts/events/auth/user-lifecycle.v1.schema.json` is present. None of these gaps block T24's service-layer implementation (no contract validation test is possible against a file that doesn't exist), but the header's own citations remain inaccurate, consistent with the pattern already flagged in T22/T23.

## Acceptance Criteria

- AC1 (R30, MERCHANT+MFA gate). `create(...)` succeeds only for a caller holding the `MERCHANT` role with a confirmed TOTP enrollment; fails otherwise. **Exact failure mode/exception TBD in Phase 2** — depends on which layer enforces this (Open Questions).
- AC2 (R30, key format). Generated key is `ck_live_<suffix>.<secret>` per L7 — pending the L7-vs-column-width resolution.
- AC3 (R30, storage). Only a SHA-256 hash of the full key is persisted (`ApiKey.keyHash`); the plaintext key is returned by `create(...)` and never persisted or logged anywhere.
- AC4 (R30, audit). A successful `create(...)` records an `api_key.created` audit event via `AuditService`.
- AC5 (task statement, list). A `list(...)` method returns the caller's own keys (metadata only — R34's "no secret material" constraint, even though R34 itself is T26's, applies transitively to whatever DTO/shape T24's `list` returns, since T26 will expose it directly).
- AC6 (task statement, revoke). A `revoke(...)` method sets `revokedAt` on the caller's own key and records an `api_key.revoked` audit event (R35's audit requirement, same transitive relevance as AC5).
- AC7 (task statement, constant-time compare). The exchange path's secret comparison uses a constant-time primitive, not `String.equals`/`Arrays.equals`.
- AC8 (R32, exchange side-effect). A successful exchange updates `last_used_at`.
- AC9 (R33, uniform failure). Revoked, expired, malformed, or hash-mismatched presented keys all fail exchange identically (single exception type/uniform outward signal), matching `AccountExceptionHandler.onVerificationTokenRejected`'s established one-response-for-every-reason pattern.

## Tests required

Named tests from `package.md` §8 scoped to this task:
- `shouldCreateApiKeyAndShowPlaintextExactlyOnce` → R30 (per the numbering-drift correction already established in T22/T23: `package.md` §8 maps this to "R27," but `requirements.md`'s authoritative numbering makes it R30).
- `shouldRejectRevokedOrUnknownApiKeyWithUniform401` → R33 (§8 maps to "R29"; authoritative id is R33).

Boundary tests implied, to be proposed formally in Phase 5:
- Create without `MERCHANT` role → rejected.
- Create with `MERCHANT` role but unconfirmed/no MFA → rejected.
- Exchange with a correct prefix but wrong secret → uniform rejection (proves the constant-time compare path, not just the lookup).
- Exchange with a well-formed-looking but entirely unknown prefix → uniform rejection, same shape as a wrong-secret rejection (enumeration-safety parity, matching this codebase's broader convention).
- Revoke of a key not owned by the caller → must not succeed (ownership boundary — not explicit in any requirement ID, but implied by "their own keys" language in R34/R35 and this codebase's consistent authorization posture elsewhere).
- List returns no secret/hash material in any field.

## Open Questions

- **Blocking: L7 vs. `prefix VARCHAR(16)`.** Carried forward from T23, now actually load-bearing. Must be resolved in Phase 2 before `create(...)` can be designed, let alone implemented. Both previously-recorded options remain live: amend L7's suffix length, or add a migration widening the column.
- **Blocking-for-design (not for understanding): which layer enforces "require `MERCHANT` + confirmed MFA."** T24 has no controller; this codebase's only role-check precedent is `@PreAuthorize` on controllers (T26's job). If T24's `create(...)` is expected to independently re-verify both preconditions (matching `MfaService.requireActiveAccount`'s defense-in-depth precedent), that must be decided explicitly in Phase 2, not assumed.
- **`package.md` §11 Q3** (max active keys per merchant? scopes beyond `merchant.api`?) remains genuinely unresolved anywhere in the spec — no `design.md` O-item addresses it either (checked; O1–O5 cover TOTP encryption, rate-limiting, session labels, login page, recovery-code hashing — none mention API keys). If `create(...)` should enforce a cap, Phase 2 needs an explicit decision or an explicit "no cap for now" call, not silence.
- **Whether exchange failures need their own audit event.** R33 only specifies the uniform-401 *response* shape; it does not explicitly require an audit event the way R30/R35 explicitly do for creation/revocation. Given `agents.md`'s general "every security-relevant action is recorded" standing rule and `MfaService`'s `mfa.failed` precedent for a directly analogous "wrong credential presented" case, an audit event on exchange failure seems very likely intended — but it is not written into R33 itself. Flagging for Phase 2 rather than assuming.
- **Whether R31's JWT-issuance boundary is being drawn correctly.** T24's `exchange(...)` is scoped to validate-and-touch-`last_used_at`; actually minting the JWT is R31/T25's job. This split isn't explicitly spelled out anywhere in `requirements.md` — it's inferred from the header's scoped-requirement-ID list (T24: R30/R32/R33; R31 conspicuously absent) plus `design.md`'s package map putting `ApiKeyTokenIssuer.java` under a later task's ambit. Reasonable, but worth confirming explicitly in Phase 2 rather than silently assuming the split is correct.
