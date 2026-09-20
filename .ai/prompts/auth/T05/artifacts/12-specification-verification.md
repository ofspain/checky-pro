# auth · T05 — Phase 12: Specification Verification

Verifying the final implementation (`06-implementation-notes.md`, `09-review-resolution.md`) and
tests (`10-test-generation.md` + its Phase 11 addendum) against `requirements.md`, `design.md`, and
`tasks.md` for T05 only.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R3 (partial — `issue` provides the data an `auth.email.requested` event needs; T05 does not emit it) | Yes | `VerificationTokenService.java:63-85` (`issue`) | `shouldActivateAccountWithValidVerificationToken`, `shouldRoundTripBothPurposes`, `shouldGenerateUrlSafeRawTokenOfExpectedLength` | No | None — event emission is explicitly T06's scope, per the frozen brief |
| R4 (partial — `verify`/`consume` correctly recognize a valid token; T05 does not activate the account) | Yes | `VerificationTokenService.java:87-133` (`verify`, `consume`) | `shouldActivateAccountWithValidVerificationToken`, `shouldTreatTokenOneTickBeforeExpiryAsValid` | No | None — account activation is T06's scope |
| R5 — uniform rejection across not-found/expired/used/deleted/suspended | Yes | `VerificationTokenService.java:87-133`; `resolveUsableAccount` at `:135-138` | `shouldNotRevealAccountExistenceForInvalidVerificationToken` (now covers both `verify()` and `consume()` for every failure reason, per the Phase 11 addendum) | No | None |
| L5 — enumeration-safe response *shape* | Yes | `Optional<UUID>` uniform return on `verify`/`consume`, no exception hierarchy distinguishing failure reasons | Same as R5 | No | None — full HTTP-response-level uniformity is T06/task 10's scope, as scoped at Phase 4 |
| L1 (widened) — no new migration; V1's `verification_tokens` table used as-is | Yes | `VerificationToken.java` entity mapping only, no Flyway file added | N/A (structural) | No | None |
| Finding 1 — raw token format (32 bytes, URL-safe Base64, 43 chars) | Yes | `VerificationTokenService.java:146-149` (`generateRawToken`) | `shouldGenerateUrlSafeRawTokenOfExpectedLength` (strengthened at Phase 11 with an explicit charset regex) | No | None |
| Finding 2 — atomic `consume` | Yes | `VerificationTokenRepository.java:26-29` (`markConsumed`, folds `usedAt IS NULL` and `expiresAt > :now` into one statement) | `shouldRejectSecondConsumeOfTheSameTokenAtomically` | No | None |
| Finding 3 — `verify`/`consume` contract split | Yes | `VerificationTokenService.java:87` (`verify`, read-only), `:105` (`consume`, sole mutating path) | Covered throughout | No | None |
| Finding 4 — account-state scope (option a) | Yes | `isAccountUsable` at `VerificationTokenService.java:141-144` — `DELETED`/`SUSPENDED` only | `shouldNotRevealAccountExistenceForInvalidVerificationToken` | No | None |
| Finding 5 — `issue` error semantics | Yes | `VerificationTokenService.java:68-69` (`AccountNotFoundException`) | `shouldThrowAccountNotFoundExceptionWhenIssuingForUnknownAccount` (strengthened at Phase 11 with `verifyNoInteractions(tokenRepository)`) | No | None |
| Finding 6 → **superseded by Phase 9's simplification** (single-attempt fail-fast, not retry) | Yes | `VerificationTokenService.java:74-84` | `shouldThrowIllegalStateExceptionOnTokenHashCollision` | No | **Yes — documented, human-approved deviation from the original Phase 4 "retry up to 3 times" spec** (see below) |
| Finding 7 — TTL `@Min`/`@Max` validation | Yes | `VerificationTokenProperties.java:17` | `VerificationTokenPropertiesTest` (5 tests, strengthened at Phase 11 to assert the exact property path and annotation) | No | None |
| Finding 8 — reissue invalidates prior tokens | Yes | `VerificationTokenService.java:66` (`invalidateActive` call before creating the new token) | `shouldInvalidatePriorActiveTokenBeforeIssuingANewOne` (call-level) + `shouldMakePriorTokenUnverifiableAfterReissue` (behavioral, added at Phase 11 per Kimi Gap 1) | No | None |
| Finding 9 — Clock-derived timestamps only | Yes | `VerificationTokenService.java:65,68,96,122,132`; `VerificationToken.java` has no `@PrePersist`/`@PreUpdate` | Implicit in every test via the fixed `Clock` | No | None |
| Finding 10 — `issue` result type, no raw-token leak | Yes | `VerificationTokenService.java:150-164` (`VerificationTokenResult`, custom `toString()`) | `shouldNeverLeakRawTokenViaResultToString` | No | None |
| Kimi Phase 8 Finding 3 — `consume` account re-check after mark-used | Yes | `VerificationTokenService.java:129-132` | `shouldRejectConsumeWhenAccountBecomesUnusableBetweenTheTwoChecks` | No | None |

**Documented deviation (Finding 1/6):** the frozen brief (Phase 4) originally locked "retry up to 3
times" on a `token_hash` collision. Self-review and Kimi's independent review (Phase 7/8) both
found this unimplementable correctly within a single PostgreSQL transaction — a same-transaction
retry cannot work because Postgres aborts the whole transaction on the first constraint violation.
At the Phase 9 human-approval gate, this was explicitly simplified to a single insert attempt that
throws `IllegalStateException` (with the original exception chained) on collision, rather than
building `REQUIRES_NEW`-per-attempt transaction machinery for an event with ~2⁻²⁵⁶ probability.
This is a knowing, recorded, human-approved deviation from Phase 4's original wording — not a
silent one — and does not weaken any acceptance criterion: a collision still fails loudly rather
than corrupting state or looping indefinitely.

## Principal-engineer assessment

**(1) Is the task fully complete?**
Yes. `VerificationToken`, `VerificationTokenRepository`, and `VerificationTokenService` are
implemented exactly per the frozen brief's Files to Create, with every Phase 3/8/9 finding folded
in (one — Finding 6 — via an explicitly documented simplification rather than the original
wording, decided at the human-approval gate). No endpoint, `AccountController`, or
`AccountService` change was made, consistent with the scope boundary established at Phase 0 after
investigating T06.

**(2) Does it satisfy every acceptance criterion?**
Yes. R3/R4 (partial, as scoped), R5 (full uniformity across both `verify` and `consume`, for every
failure reason), and L5's shape requirement all have passing, name-matched tests. The two named
tests (`shouldActivateAccountWithValidVerificationToken`,
`shouldNotRevealAccountExistenceForInvalidVerificationToken`) are implemented at the service level
per the Phase 0 scoping decision, with their literal HTTP-level realization correctly deferred to
T06/task 10.

**(3) Does it violate any LOCKED decision?**
No. L5's uniform-shape requirement is honored throughout (`Optional<UUID>`, no
failure-reason-distinguishing exception types). L1 is honored — no migration added, the existing
V1 `verification_tokens` table is used exactly as defined.

**(4) Remaining risks?**
- The pre-existing, unrelated `token` package compile failure (noted since T03, still unfixed)
  continues to block a real `mvn -pl services/auth test` / Surefire-mediated run. All 19 tests were
  verified via direct `javac` + JUnit Platform `Launcher` execution against the real resolved
  classpath — the same engine Surefire delegates to — but that unrelated fix is still pending.
- `markConsumed`'s and `invalidateActive`'s real atomicity and WHERE-clause correctness are
  properties of the JPQL translated to SQL and PostgreSQL's transaction semantics — genuinely
  provable only by a Testcontainers/integration test, which is outside a unit-test-only task's
  scope (per the frozen brief) and outside T05 entirely. This is a standing risk carried forward,
  not a defect: the unit tests prove the *service* correctly interprets the repository's contract
  (0 vs. 1 rows affected), not that the underlying SQL itself is bug-free.
- T06 (self-service verification endpoints) and task 7 (password-reset flow) are the first real
  consumers of this service; neither exists yet, so `issue`'s `AccountNotFoundException` contract,
  the `PASSWORD_RESET` purpose path, and the raw-token-in-email-link handling remain unexercised
  end-to-end until those tasks land.
- `package.md` §8's original named-test-to-task assignment ambiguity (Phase 0's central finding)
  remains unreconciled in the spec itself — T05 proceeded on a reasoned, human-confirmed
  interpretation, but the spec files themselves are unchanged (outside this task's permission to
  edit `spec/`).

## Verdict

**PASS** — every R3/R4/R5 acceptance criterion in scope and LOCKED decisions L5/L1 are implemented
exactly as specified (with one explicitly documented, human-approved simplification of the
collision-retry mechanism), backed by 19 passing, deterministic tests including a genuine
behavioral proof of reissue invalidation; remaining items are pre-existing infrastructure issues,
inherent unit-test-vs-integration-test boundaries, or forward-looking notes for T06/task 7, none of
which block this task.
