# auth · T05 — Phase 10: Test Generation

No production code changed in this phase. Two test files created:

- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java` (13 tests)
- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenPropertiesTest.java` (5 tests)

**All 18 tests verified passing** (~700ms, no Spring context, no database). This closes Kimi's
Phase 8 Finding 4 (no tests existed).

---

## Test manifest

### `VerificationTokenServiceTest` (Mockito-mocked `VerificationTokenRepository`/`AccountRepository`, fixed `Clock`)

| Test | Verifies |
|---|---|
| `shouldActivateAccountWithValidVerificationToken` | Named test — issue then consume resolves to the correct account; hash/expiry are computed correctly on issue. |
| `shouldNotRevealAccountExistenceForInvalidVerificationToken` | Named test — not-found, expired, already-used, deleted-account, and suspended-account tokens all produce `Optional.empty()` from `verify`/`consume`; suspended-account `consume` never even attempts `markConsumed` (proves the pre-check ordering). |
| `shouldTreatTokenExactlyAtExpiryAsExpired` | TTL boundary — `expiresAt == now` is expired. |
| `shouldTreatTokenOneTickBeforeExpiryAsValid` | TTL boundary — one nanosecond before expiry is still valid. |
| `shouldRejectSecondConsumeOfTheSameTokenAtomically` | Atomicity (Finding 2 origin) — service correctly treats a second `markConsumed` call returning 0 rows as rejection. |
| `shouldRejectConsumeWhenAccountBecomesUnusableBetweenTheTwoChecks` | Phase 9 fix (Kimi Finding 3) — account re-checked after the atomic mark-used write; a suspend landing in between still yields `Optional.empty()`. |
| `shouldInvalidatePriorActiveTokenBeforeIssuingANewOne` | Reissue invalidation (Finding 8) — `invalidateActive` called with the correct account/purpose/instant before the new token is created. |
| `shouldThrowIllegalStateExceptionOnTokenHashCollision` | Phase 9 simplified collision handling — a `DataIntegrityViolationException` from `saveAndFlush` becomes `IllegalStateException` with the original exception chained as cause. |
| `shouldThrowAccountNotFoundExceptionWhenIssuingForUnknownAccount` | Finding 5 — `issue` for an unresolvable UUID throws `AccountNotFoundException`, not the uniform R5 path. |
| `shouldRejectNullArgumentsWithIntentionalException` | Null handling — all three public methods reject `null` with `NullPointerException`, not a bare NPE from deeper in the call stack. |
| `shouldRoundTripBothPurposes` | `EMAIL_VERIFY` and `PASSWORD_RESET` both issue and store correctly. |
| `shouldNeverLeakRawTokenViaResultToString` | Finding 2 (self-review + Kimi) — `VerificationTokenResult.toString()` never contains the raw token. |
| `shouldGenerateUrlSafeRawTokenOfExpectedLength` | Finding 1 — raw token is exactly 43 characters and decodes as valid URL-safe Base64. |

### `VerificationTokenPropertiesTest` (plain `jakarta.validation.Validator`, no Spring context)

| Test | Verifies |
|---|---|
| `shouldBeValidWithinBounds` | `ttlMinutes = 30` (the default) passes validation. |
| `shouldRejectZeroTtl` | `@Min(1)` rejects `0`. |
| `shouldRejectNegativeTtl` | `@Min(1)` rejects negative values. |
| `shouldRejectTtlAboveOneYear` | `@Max(525_600)` (Phase 9 fix) rejects `525_601`. |
| `shouldAllowTtlAtOneYearBoundary` | `525_600` itself is valid (inclusive boundary). |

---

## Bugs found and fixed while writing these tests (test-only, no production code touched)

Writing `VerificationTokenServiceTest` surfaced two real Mockito mistakes on the first run (10 of
18 tests failed):

1. **Nested stubbing.** A shared `usableAccount(status)` helper builds a mocked `Account` via its
   own `when(...).thenReturn(...)` calls. Several tests originally called this helper *inline* as
   the argument to another mock's `.thenReturn(Optional.of(usableAccount(...)))` — starting a new
   Mockito stubbing context before the outer one completed, throwing
   `UnfinishedStubbingException` on every such call site. Fixed by always assigning the account
   fixture to a local variable first, then passing that variable to the outer `when(...)`.
2. **Missing/over-strict stub.** The same helper never stubbed `account.getId()` at all — Mockito's
   default answer for an unstubbed method returning a boxed `Long` is `0L` (not `null`), so
   `issue`'s call to `invalidateActive(account.getId(), ...)` silently received `0L` instead of the
   intended account ID, and the assertion caught it. Fixed by stubbing `getId()` too. Because the
   same shared helper is reused by tests that only exercise `issue` (never touching
   `getAccountUuid()`/`getStatus()`) and by tests that exercise `verify`/`consume` (which need all
   three), Mockito's strict-stubbing mode then flagged the *other* two stubs as unused in
   `issue`-only tests. Marked all three stubs `lenient()` — the documented, correct use of
   `lenient()` for a shared test-data builder consumed unevenly across tests, not a workaround for
   a real defect.

Neither of these was a production-code bug — both were caught and fixed within this test file
before it was considered done.

---

## Verification

Same method as T03/prior tasks: `mvn -pl services/auth test` still cannot run due to the
pre-existing, unrelated `token` package compile failure. Verified via targeted `javac` against the
module's resolved test-scope classpath (`-sourcepath` covering both `src/main/java` and
`src/test/java`) plus the JUnit Platform `Launcher` API (`junit-platform-launcher:1.12.2`, matching
the project's resolved `junit-platform-engine` version).

**Result: 18/18 tests successful, 0 failed, 0 skipped, ~700ms.**
