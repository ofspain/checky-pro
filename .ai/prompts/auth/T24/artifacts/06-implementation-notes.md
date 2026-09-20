# auth · T24 · Phase 6 — Implementation Notes

All 9 planned files created, all 3 planned files modified, exactly per the Phase 5 plan — plus one method the plan missed (flagged below, not hidden). No test file written this phase (T24 is not test-only; tests are Phase 10, per this phase's own instruction).

## Files created

- **`V7__widen_api_key_prefix.sql`** — `ALTER TABLE api_keys ALTER COLUMN prefix TYPE VARCHAR(32);`, exactly as frozen.
- **`ApiKeyProperties.java`** — `@ConfigurationProperties(prefix = "themistra.auth.api-key")`, `@NotBlank String prefix`, matching `MfaProperties`'s established shape.
- **`ApiKeyHasher.java`** — `hash(String)` / `matches(String, String)`, thin wrapper over `Hashing.sha256`/`Hashing.constantTimeEquals`.
- **`ApiKeyNotAuthorizedException.java`**, **`ApiKeyExchangeRejectedException.java`**, **`ApiKeyNotFoundException.java`** — plain, no-arg `RuntimeException` subclasses, each documented with the enumeration-safety reasoning for carrying no differentiating detail.
- **`ApiKeyService.java`** — `create`, `list`, `revoke`, `exchange`, and the three result records (`CreateApiKeyResult`, `ApiKeyMetadata`, `ExchangeResult`), plus private helpers exactly as planned (`requireValidName`, `requireMerchantWithConfirmedMfa`, `resolveAccountId`, `randomAlphanumeric`, `toMetadata`, `recordAudit`) plus one addition — `resolveAccountUuidQuietly` — needed to cleanly separate "resolve an audit target, tolerate failure" from `resolveAccountId`'s "resolve or throw" contract used everywhere else.

## Files modified

- **`ApiKeyRepository.java`** — added `findByAccountId`, `findAccountIdByUuid`, `findAccountUuidById`, `updateLastUsedAt`, `revokeIfActive`, all exactly as planned, **plus `findByKeyUuid`** (see Deviations).
- **`common/Hashing.java`** — added `constantTimeEquals(String, String)`, exactly as planned.
- **`application.properties`** — added `themistra.auth.api-key.prefix=${API_KEY_PREFIX:ck_live_}`, matching the `${ENV_VAR:default}` convention every other property in this file already uses (the frozen brief only specified the bare key=value; the env-var wrapper is this codebase's own established style, applied consistently, not a new decision).

## Deviations forced by reality

1. **`ApiKeyRepository.findByKeyUuid(UUID)` — not in the Phase 5 plan, added during implementation.** `revoke(accountUuid, keyUuid)` needs to resolve a key's internal id from its external `keyUuid` — the only identifier a caller outside this module ever holds for one specific key — and no such lookup existed anywhere in the plan. This is the same category of gap as `findByAccountId` (already added in Phase 5 with a note that it "wasn't separately called out... but is a direct, unavoidable requirement"): a plain derived-query method, not a new design decision. Flagged here rather than silently added.

2. **`exchange`'s audit-target resolution for hash-mismatch required an interpretation the frozen brief's disposition #10 didn't fully spell out.** Disposition #10 groups "hash-mismatch/revoked/expired rejections (a row was matched)" together as auditable-with-a-known-account, versus "malformed/unknown-prefix... audit with `accountUuid = null`." But by definition a hash-mismatch means no candidate's hash matched — so "a row was matched" can't mean "hash matched" for that case. The implemented interpretation: **prefix-level candidate existence**, not hash match, is what makes an account "known" for audit purposes — if `findByPrefix` returns at least one row (regardless of whether any hash matches, or whether the matching one is revoked/expired), the audit targets that row's account (arbitrarily the first candidate if more than one, which in practice is never more than one given real key generation). Only a genuinely empty `findByPrefix` result (unknown prefix) or an unparseable presented key audits with `accountUuid = null`. This is a defensible reading consistent with the security intent (surface "someone tried a wrong secret against merchant X's known key" to that merchant's audit trail) but is a real interpretation of ambiguous phrasing, not a restatement of an unambiguous instruction — documented in `ApiKeyService.exchange`'s own Javadoc, not left implicit.

## Mapping to acceptance criteria

| ID | Status |
|---|---|
| AC1 (MERCHANT+MFA+ACTIVE gate) | Met — `requireMerchantWithConfirmedMfa` checks all three, in `create`. |
| AC2 (key format, L7) | Met — `ck_live_` (configured prefix) + 24-char suffix + `.` + 32-char secret, `SecureRandom`, alphabet `[A-Za-z0-9]`. |
| AC3 (storage — hash only) | Met — only `apiKeyHasher.hash(fullKey)` is persisted; `fullKey` exists only inside `create`'s stack frame and its returned (never-logged) result. |
| AC4 (create audit) | Met — `api_key.created`, `SUCCESS`, actor = subject (self-service). |
| AC5 (list, no secret material) | Met — `ApiKeyMetadata` has no hash/secret field by construction (can't leak what it can't hold). |
| AC6 (revoke + audit) | Met — `api_key.revoked` recorded only on an actual state change path (idempotent no-op still records — see Open Questions in Phase 7/8 candidate list, not raised as a defect here since the frozen brief didn't specify idempotent-revoke's audit behavior either way). |
| AC7 (constant-time compare) | Met — every `findByPrefix` candidate is passed through `apiKeyHasher.matches` inside the loop with no early `break`/`return`; the decision to succeed or fail is made only after the loop completes. |
| AC8 (exchange updates `last_used_at`) | Met — `updateLastUsedAt` called only on the eligible-match path. |
| AC9 (uniform R33 rejection) | Met — `ApiKeyExchangeRejectedException` thrown identically for malformed input, unknown prefix, hash mismatch, and revoked/expired. |
| AC10 (name validation) | Met — `requireValidName` rejects blank or >100-char names before any other check runs. |

## Verification performed this phase
- `mvn -pl services/auth -am compile` — clean.
- `mvn -pl services/auth -am test-compile` — clean.
- `mvn -pl services/auth test -Dtest=ArchitectureTest` — clean, confirms no `Account` entity dependency was introduced (L12) and `ApiKeyRepository` stays package-private.

No Testcontainers verification yet — that's Phase 10's `ApiKeyServiceIntegrationTest`, per the plan.
