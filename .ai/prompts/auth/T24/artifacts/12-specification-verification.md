# auth · T24 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R30** — create: MERCHANT + confirmed MFA, `ck_live_` prefix, SHA-256 hash stored, plaintext returned once, `api_key.created` audited | Yes | `ApiKeyService.java:82` (`create`), `:209` (`requireMerchantWithConfirmedMfa`) | `ApiKeyServiceIntegrationTest.java:90` (`shouldCreateApiKeyAndShowPlaintextExactlyOnce`, named test), `:106`/`:114`/`:124` (role/MFA/status gate boundaries) | No | None |
| **R32** — successful exchange updates `last_used_at` | Yes | `ApiKeyService.java:154` (`exchange`); `ApiKeyRepository.java:53` (`updateLastUsedAt`) | `:174` (`exchangeUpdatesLastUsedAt`) | No | None |
| **R33** — revoked/expired/malformed/mismatched all uniform 401 | Yes | `ApiKeyService.java:154-200` (`exchange`, single `ApiKeyExchangeRejectedException` for every cause) | `:144` (`shouldRejectRevokedOrUnknownApiKeyWithUniform401`, named test — covers all four causes plus two boundary shapes added in Phase 11) | No | None at the service layer; the HTTP-level "401" itself doesn't exist yet — `ApiKeyExceptionHandler` explicitly deferred to T25/T26 (frozen brief disposition #7), so R33 is proven as far as this task's own scope goes, not end-to-end over HTTP. |
| **L7** — key format, `ck_live_` + 24-char suffix + `.` + 32-char secret, SHA-256 stored | Yes | `V7__widen_api_key_prefix.sql` (schema); `ApiKeyService.java:87-91` (generation); `ApiKey.java:45` (`length = 32`, fixed in Phase 9 after being caught by independent review) | `:90-99` (`shouldCreateApiKeyAndShowPlaintextExactlyOnce` — asserts the exact regex shape and 32-character stored prefix) | No | Resolved via `V7` migration (human-authorized), not by shrinking L7's suffix — implements L7 exactly as written, the deviation is from the *original, too-narrow* schema, not from L7 itself. |
| **L12** — no `Account` entity import | Yes | `ApiKeyService.java` imports only `AccountService`/`AccountResponse`/`AccountStatus`/`AccountNotFoundException`/`InvalidAccountStateException` (all public, non-entity); `ApiKeyRepository.java`'s UUID/id resolvers are native queries against the `accounts` table, not the `Account` Java entity | `ArchitectureTest` (re-run clean after Phase 6 and again after Phase 9's edits) | No | None |
| Task statement — `list` | Yes (task-statement-level, no requirement ID; transitively R34's "metadata, no secret") | `ApiKeyService.java:103` (`list`) | `:234` (`listReturnsOnlyTheCallersOwnKeysWithNoSecretMaterial`), `:284` (`listIncludesRevokedKeys`, Phase 11 addition) | No | None |
| Task statement — `revoke` | Yes (transitively R35's "revoke + audit") | `ApiKeyService.java:117` (`revoke`); `ApiKeyRepository.java:63` (`revokeIfActive`, idempotent) | `:250` (`revokeIsIdempotent` — regression-proves the Phase 9 fix), `:264` (`revokeOfNonOwnedKeyFails`), `:275` (`revokeOfUnknownKeyFails`, Phase 11 addition) | No | None |
| Task statement — constant-time hash compare | Yes | `Hashing.java` (`constantTimeEquals`); `ApiKeyHasher.java` (`matches`) | `ApiKeyHasherTest.java:15,21,31` | No | None |

## Beyond the matrix: what this task actually delivered, and what it deliberately didn't

- **Two real bugs were found and fixed that would otherwise have shipped silently:** the `V7`/entity-mapping drift (`ApiKey.prefix` still declared `length = 16` after the migration widened the column to 32 — would have failed Spring context startup under `ddl-auto=validate`), and the `exchange` audit-target misattribution on prefix collision. Both were caught independently by self-review (Phase 7) and Kimi's independent review (Phase 8) converging on the same findings, then fixed and regression-tested (Phase 9/10/11).
- **Two Kimi findings were verified against source and rejected** (Phase 9, #2 and #3) — Kimi cited the Phase 2 TIB's file list rather than the Phase 4 frozen brief that actually superseded it. Checking the citation before accepting avoided reopening an already-made, deliberate scope decision (deferring `ApiKeyExceptionHandler` to T25/T26).
- **One optional Phase 11 gap (a timing-oracle micro-benchmark test) was declined**, not silently dropped — this codebase has no precedent for timing-based test assertions anywhere, and Kimi's own finding flagged it as low-priority.
- **`ApiKeyExceptionHandler` and new `ProblemTypes` entries remain deliberately absent.** `ApiKeyNotAuthorizedException`, `ApiKeyExchangeRejectedException`, and `ApiKeyNotFoundException` all exist and are thrown correctly, but nothing maps them to an HTTP status yet — they will currently surface as `500` if hit through any hypothetical caller today. This is by design (frozen brief disposition #7) since no controller exists to need them yet, but it means R33's "401" is proven only at the exception-type level, not the HTTP-status level, until T25/T26.

## Answers

**1. Is the task fully complete?**
Yes, for everything within T24's own frozen scope: create, list, revoke, and exchange are all implemented, independently and adversarially reviewed twice, and verified against a real Postgres instance with 18 passing tests (15 integration + 3 unit).

**2. Does it satisfy every acceptance criterion?**
Yes — AC1 through AC10 (frozen brief, `04-frozen-task-brief.md`) are all met and each has at least one corresponding test, confirmed in the traceability matrix above.

**3. Does it violate any LOCKED decision?**
No. L7 is implemented exactly as written (via the authorized `V7` migration, not by weakening the format). L12 is respected and CI-verified.

**4. Remaining risks**
- `exchange` doesn't verify the owning account is still `ACTIVE` — a suspended/deleted merchant's existing key remains valid until explicitly revoked or expired. Documented as a deliberate, known scope limit in `exchange`'s own Javadoc (Phase 9 disposition #9), not silently assumed — but it's a real gap a future task should weigh in on.
- `ApiKeyExceptionHandler`/`ProblemTypes` entries don't exist yet — T25/T26 cannot safely assume R33's "401" until those are built; this is explicitly T25/T26's responsibility per the frozen brief, not an oversight of T24.
- `package.md` §11 Q3 (max keys per merchant, additional scopes) remains unresolved by the spec author; T24 enforces neither a cap nor any scope beyond the fixed `merchant.api` — consistent with the frozen brief's explicit "no cap for now" resolution, but worth revisiting if the spec is ever updated.
- The six pre-existing, already-documented test failures elsewhere in the suite (Kafka timing, audit-FK ordering ×3, Mockito strict-stubbing ×2) remain unfixed and confirmed unrelated to this task (516 tests total, same 6 errors, both before and after T24's additions).

## Verdict

**PASS** — every requirement and task-statement behavior scoped to T24 is implemented, independently reviewed twice with real bugs caught and fixed both times, and verified against a real database; no LOCKED-decision violations; the two deliberate scope boundaries (no `ApiKeyExceptionHandler`, no account-`ACTIVE` check in `exchange`) are explicitly documented for T25/T26 rather than silently assumed.
