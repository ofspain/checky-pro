# auth · T19 — Phase 7: Self-Review

Consumes `artifacts/06-implementation.md`. Fresh, critical re-read of the implementation and its
tests before Kimi's independent Phase 8 review, following this pipeline's own established
discipline: verify every claim against real behavior, never trust a green check without having
proven it can fail, disclose everything found.

---

## Finding 1 · All 11 `ArchitectureTest` rules re-verified, not just the 2 Phase 3 named

Phase 6 only re-ran the two canaries Phase 3 Finding 9 specifically named
(`shouldPreventCrossModuleEntityImports`, `shouldEnforcePublicEndpointAllowlist`). For
completeness, this phase re-ran all 11 `@ArchTest` rules in the file (same Surefire-bypass
standalone-runner technique, deleted after use) against the real class graph with `MfaController`
included. **All 11 pass**, including `controllersDependOnlyOnTheirOwnModuleServices` (T35) —
`MfaController` depends only on `MfaService`, its own module's service, so it trivially satisfies
the same-module check with no allowlist entry needed.

**Resolution:** no code change; broader confidence established, not just the two rules Kimi's
Phase 3 named.

## Finding 2 · A stale Javadoc claim in `MfaServicePersistenceIntegrationTest` corrected

This file's class-level Javadoc (inherited from T18) claimed it was "not expected to run green
today" due to an undiagnosed Hibernate `existsByEmail` byte-array conversion defect. That defect
was resolved 2026-08-06/08/09 (`docker-testcontainers-handshake-issue` memory) — confirmed directly
by running the class: all 8 pre-existing tests pass against real Docker. The claim was simply
never updated after the fix landed, an unrelated pre-existing staleness this task's own work
happened to touch.

**Resolution:** fixed the Javadoc to state the corrected status, with the evidence that confirmed
it.

## Finding 3 · `regenerateRecoveryCodes` has no persistence-level proof that old codes are actually invalidated

Phase 6's tests proved the *returned* new codes differ from the *returned* old codes
(`MfaControllerIntegrationTest`) and that the service *calls* `deleteByAccountId` before inserting
(`MfaServiceTest`, mocked). Neither proves an old code is actually *rejected* if subsequently
submitted — the real, end-user-facing guarantee R49 implies ("invalidate every existing recovery
code").

**Resolution:** added `regenerateRecoveryCodesInvalidatesOldCodesAndPersistsTenGenuinelyUsableNewOnes`
to `MfaServicePersistenceIntegrationTest` (real DB, mirrors the file's own established style):
confirms an old code now throws `InvalidRecoveryCodeException` via `verifyRecoveryCode`, and a new
code is genuinely usable. Passes.

## Finding 4 · A genuine concurrency gap, empirically characterized (initial hypothesis was wrong)

`regenerateRecoveryCodes` has no guard analogous to `confirm`'s atomic `confirmIfUnconfirmed` —
it's an unconditional read-check-delete-insert cycle, since there's no single "enrollment" row a
conditional update could version (recovery codes are a set; no schema change is in this task's
scope, per the frozen brief). **Initial hypothesis**: two concurrent regenerate calls for the same
account, both using the same still-valid TOTP code, would both silently succeed — the loser's
*returned* codes already stale by the time the response is read, and (worse) a disable-vs-regenerate
race could leave live recovery codes after a "successful" disable.

**Empirically disproven, in a better direction, by actually writing and running the test** (not
assumed safe from a code read): Hibernate's own first-level session tracking on
`recoveryCodeRepository.deleteByAccountId` throws `ObjectOptimisticLockingFailureException` for
whichever transaction loses the race — the rows it loaded to delete no longer exist by the time its
own DELETE executes. That transaction rolls back **entirely**, including its own audit write. The
loser gets an opaque 500 (no specific handler exists for this exception; falls through to the
generic catch-all, R46-safe — no detail leaked), not silently stale or corrupted data. This also
closes the disable-vs-regenerate worry: the loser's entire transaction rolls back, so the account
never ends up in a mixed state.

**Resolution:** added `concurrentRegenerateRecoveryCodesCallsResultInExactlyOneSuccessAndTenRecoveryCodes`
to `MfaServicePersistenceIntegrationTest` (mirrors the file's existing
`concurrentConfirmCallsResultInExactlyOneSuccessAndTenRecoveryCodes`), proving the real outcome
under genuine concurrent transactions: exactly one of two concurrent calls succeeds, the other
throws `ObjectOptimisticLockingFailureException`, exactly 10 rows survive. Re-ran 3 times to confirm
not flaky — stable every time. **Judged low severity and left as a known, accepted limitation, not
fixed further**: requires the account's own valid password+TOTP code used twice concurrently (a
double-click/retry scenario, not an unauthenticated attack vector — whoever has both credentials
already has full account control); the failure mode is a safe, if unhelpful, 500 rather than data
corruption. Retry/backoff handling for this opaque 500 is a reasonable future enhancement but
outside this task's own scope (frozen brief: no schema change, self-contained new method only).

## Finding 5 · `MfaControllerTest`'s per-endpoint exception-propagation coverage considered, judged sufficient as-is

Each of the four endpoints currently has exactly one "an exception thrown by the service
propagates uncaught" test (`beginEnrollPropagatesAlreadyEnrolledUncaught`,
`confirmPropagatesInvalidCodeUncaught`, `disablePropagatesPasswordMismatchUncaught`,
`regenerateRecoveryCodesPropagatesNotEnrolledUncaught`) — not one per exception type per endpoint
(e.g. `confirm` can also throw `MfaNotEnrolledException`/`MfaAlreadyEnrolledException`; `disable`
can also throw `MfaNotEnrolledException`/`InvalidTotpCodeException`; `regenerateRecoveryCodes` can
also throw `MfaCurrentPasswordMismatchException`/`InvalidTotpCodeException`).

**Considered and judged sufficient, not a gap**: `MfaController` contains zero exception-specific
logic — every exception simply propagates unmodified, so one example per endpoint already proves
the general "nothing here catches or wraps" behavior; exhaustively re-testing it per exception type
would test Mockito's own stubbing, not this controller. The full exception × endpoint matrix *is*
covered, just split by design across three test classes: `MfaExceptionHandlerTest` maps every
exception type to its correct `ProblemDetail` once, and `MfaControllerIntegrationTest` exercises
real end-to-end failure paths for wrong-password/wrong-code/already-enrolled/not-enrolled across
all four endpoints. Matches `ApiKeyControllerTest`'s own established precedent (also one or two
exception-propagation tests per endpoint, not exhaustive per-exception coverage).

## Verification performed this phase

- Full MFA-related suite + `AuthOpenApiContractTest` re-run together: `MfaServiceTest` 36,
  `MfaControllerTest` 13, `MfaExceptionHandlerTest` 6, `MfaControllerIntegrationTest` 14,
  `MfaServicePersistenceIntegrationTest` 10 (8 original + 2 new), `MfaPersistenceIntegrationTest`
  22 (unrelated to this task, confirmed still green), `AuthOpenApiContractTest` 10 — all green.
- The new concurrency test re-run 3 times in isolation to rule out flakiness — stable every run.
- All 11 ArchUnit rules re-verified (Finding 1).

## Disposition summary

4 real findings (2 code/doc corrections applied, 2 new persistence-level tests added closing real
coverage gaps — one of which corrected my own initial, wrong hypothesis before it could be written
into the record uncorrected); 1 considered-and-rejected (Finding 5, judged already-sufficient by
design, not a gap). No findings deferred to Phase 8 — ready for Kimi's independent review.
