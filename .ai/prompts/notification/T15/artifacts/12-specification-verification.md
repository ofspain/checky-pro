# notification · T15 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T15 — Consumed-contract tests |
| **Consumes** | All prior T15 artifacts (Phases 0-11, including the Phase 11 addendum) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R19** — consumed event schemas under `contracts/events/{auth,payments}/` are deserialized against; a schema mismatch fails a contract test, not a production delivery | Yes (for the `auth` half — the only half that exists) | `consumer/dto/ConsumedEventSchemaConformanceTest.java:45` (`shouldConformToConsumedEventSchemas`, the literal `package.md` §8 name); `consumer/dto/EmailRequestedEventContractTest.java`/`UserLifecycleEventContractTest.java` (T06, unaffected, more detailed per-DTO coverage) | `shouldConformToConsumedEventSchemas`; the 4 Phase 10 regression-guard tests (`matchesJsonSchemaType*`, `assertConformsToSchemaCatches*`) proving the mechanism itself, not only its happy path | `contracts/events/payments/*` — does not exist, `services/payment` does not exist | No — disclosed, not silent; matches T07's own identical, already-accepted blocker |
| **L2** — consume-only, no synchronous cross-service call | Yes | The entire new test class makes no network/DB call of any kind — plain Jackson against on-disk files and in-memory records only | Implicit — no Spring context, no Testcontainers anywhere in this task | No | No |
| **L4** — no secrets or tokens in messages or logs | Yes | `ConsumedEventSchemaConformanceTest.java:68-86` (`assertConformsToSchema`) — every assertion's own `.as(...)` description names only a field or a declared type string, never a field's own value; confirmed directly by code review (no assertion anywhere calls `.asText()`/`.toString()` on a value and embeds it in a description) | AC3's own disclosed intent; verified by direct inspection, not merely trusted | No | No |
| **AC1** — `shouldConformToConsumedEventSchemas` exists, passes, asserts required-present/no-undeclared/type-match for both DTOs | Yes | `consumer/dto/ConsumedEventSchemaConformanceTest.java:45-62` | `shouldConformToConsumedEventSchemas` itself, plus the 4 Phase 10 tests proving the underlying mechanism genuinely catches all three drift categories, not only passes when nothing is wrong | No | No |
| **AC2** — no genuine drift between either DTO and its own real schema file | Yes | Confirmed independently at Phase 4 (direct field-by-field re-read) and again empirically the moment the new test first ran and passed (Phase 6) | `shouldConformToConsumedEventSchemas` itself is the live, ongoing proof | No | No |
| **AC3** — no raw token value ever reaches an assertion description/failure message | Yes | `consumer/dto/ConsumedEventSchemaConformanceTest.java:68-86` | Verified by direct code review; `EmailRequestedEvent`'s own pre-existing `toStringExcludesTheRawToken` test (T06) remains the dedicated, unaffected proof for the DTO itself | No | No |
| **AC4** — no test against `contracts/events/payments/*` | Yes (correctly absent) | Confirmed by direct directory listing — neither the directory nor any schema under it exists | N/A — nothing to test | No | No, disclosed at every phase |

## Answers

**(1) Is the task fully complete?** Yes, for the only half of R19's own literal wording that is
actually reachable (`auth`). Every file the frozen brief's own "Files to Create" list named exists;
"Files to Modify" was correctly empty (Phase 4's own direct re-read confirmed no DTO drift, and the
new test's own first run confirmed it empirically). The task went through adversarial review (Kimi
Phases 3, 8, 11) plus this session's own self-review (Phase 7), with every finding either fixed,
correctly disposed with a stated reason, or explicitly documented as already-correct, intentional
behavior. One real, latent correctness gap (the mechanism's own negative paths had zero permanent
regression-guard coverage) was found and closed during this task's own Phase 10, not left for a
future task to discover.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC4, see matrix above. AC1 is
the criterion this task's own Phase 10 strengthened most: the original Phase 6 implementation only
ever proved the happy path (both real DTOs already conform); by Phase 10's own close, four
additional tests prove the checking mechanism itself — including `matchesJsonSchemaType`'s own full
branch coverage and a synthetic, deliberately-broken payload — genuinely catches a missing required
field, an undeclared field, and a type mismatch, not merely that it passes when everything is
already fine.

**(3) Does it violate any LOCKED decision?** No. L2 and L4 both hold, per the matrix. This is the
smallest task in the pipeline to date (one new test file, zero production code, zero new
dependencies) and introduces no new risk to either decision.

**(4) Remaining risks?**
- **`contracts/events/payments/*` remains entirely untested** because it does not exist —
  unchanged, disclosed risk, identical in kind to T07's own already-accepted blocker. Resolves
  automatically once `spec/payment-service/package.md` reaches `READY FOR IMPL` and a real payment
  consumer/schema exists for a future task to test.
- **`matchesJsonSchemaType` only supports JSON Schema's single-string `type` form**, not the legal
  array form (e.g. `["string","null"]`) — disclosed at Phase 7/8, confirmed unreachable by any of
  the three real schemas under `contracts/events/auth/` today (including the unconsumed
  `security-audit`), documented directly in the helper's own Javadoc rather than fixed
  speculatively.
- **No permanent test proves `shouldConformToConsumedEventSchemas` itself (as opposed to the
  `assertConformsToSchema` helper it calls) wires `SoftAssertions` through** — raised at Phase 11
  (Gap #2), acknowledged with no test added, matching Kimi's own assessment that the method is
  short and directly reviewable and that duplicating the already-proven mechanism at this level
  would add low-value, artificial coverage.

## Verdict

**PASS** — T15 fully satisfies R19 (for the only half that is actually reachable), and every locked
decision (L2, L4) and acceptance criterion (AC1-AC4) it touches. The literal `package.md` §8 named
test now exists, passes, and — after this task's own Phase 10 self-audit — is backed by real
regression-guard coverage proving its own checking mechanism actually works, not only that it
currently has nothing to flag. The full suite is green at 362 tests, 0 failures, 0 errors.
