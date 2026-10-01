<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T15 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T15 — Consumed event schema conformance |
| **Spec section** | R19 / package.md §8 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + final test class |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T15 regression-guard tests against the acceptance criteria.

---

## What is covered

- **`shouldConformToConsumedEventSchemas` (named, AC1)** — exists, is literally named, and checks both `EmailRequestedEvent` and `UserLifecycleEvent` against their real auth schemas for required-field presence, no undeclared fields, and type matching. Uses `SoftAssertions` so both DTOs are checked independently.
- **No genuine drift (AC2)** — the type-match check goes beyond the existing T06 contract tests' presence checks.
- **No raw token leakage (AC3)** — all assertions use field names/types; the fake token value is never embedded in a failure message.
- **Payments schemas excluded (AC4)** — only `contracts/events/auth/*` is covered; the absence of `contracts/events/payments/` is documented.
- **Regression guards for the checking mechanism itself (Phase 10)** — four extra tests cover the helper's positive/negative branches and prove `assertConformsToSchema` catches all three drift categories it claims to catch.

---

## Gap 1 · Maven verification claim cannot be confirmed in this environment

**Why it matters:** `artifacts/10-test-generation.md` states `mvn -pl services/notification clean verify` ran with 362 tests, 0 failures. This environment does not have `mvn` available.

**Evidence:**
- `bash`/`mvn` returns `command not found` in this workspace.
- The test class compiles syntactically and is internally consistent, but no local execution was performed.

**Suggested action:** Run the Maven command in an environment where it is available, or add a CI check, before considering the task fully verified.

---

## Gap 2 · No test proves `SoftAssertions` is actually wired into the named test

**Why it matters:** The synthetic negative-path test proves `assertConformsToSchema` can collect errors when given a `SoftAssertions` instance, but it does not prove `shouldConformToConsumedEventSchemas` passes a real `SoftAssertions` instance through `assertSoftly`. A future edit could replace `SoftAssertions.assertSoftly` with direct assertions and the synthetic test would still pass.

**Evidence:**
- `assertConformsToSchemaCatchesAMissingRequiredFieldAnUndeclaredFieldAndATypeMismatch` constructs its own `SoftAssertions` and calls the helper directly.
- `shouldConformToConsumedEventSchemas` uses `SoftAssertions.assertSoftly`, but no test fails if that changes.

**Suggested test:** Add a deliberately drifted version of one DTO (or a synthetic stand-in) inside a dedicated test that calls the same `SoftAssertions.assertSoftly` wrapper and asserts both checks run. This is somewhat artificial and low value given the implementation is a single block; acceptable to leave as a manual code-review item rather than a permanent test.

---

## Gap 3 · No synthetic happy-path test for `assertConformsToSchema` with a non-real-schema payload

**Why it matters:** The negative-path synthetic test proves the helper catches drift, but there is no lightweight, isolated test proving a fully conformant synthetic payload produces zero collected errors.

**Evidence:**
- The only happy-path coverage is the named test against the two real schemas.

**Suggested test:** A trivial test with a synthetic schema/payload pair that matches exactly and asserts `softly.errorsCollected()` is empty. Low priority — the named test already exercises the happy path.

---

## Gap 4 · No test exercises a schema with `additionalProperties: true`

**Why it matters:** The serialized-fields-subset check would behave differently if a future consumed schema allowed extra properties. The current tests only exercise `additionalProperties: false` schemas.

**Evidence:**
- Both auth schemas set `additionalProperties: false`.
- The helper does not read the `additionalProperties` flag; it always enforces no undeclared serialized fields.

**Assessment:** This is a deliberate consumer-side guarantee, not a bug. No action required unless the team decides consumers should allow extra DTO fields when a schema does.

---

## Summary

T15's test coverage is complete for its tiny scope. The named test satisfies AC1–AC4, the Phase 10 regression guards close the mechanism-coverage gap identified in Phase 8, and the `SoftAssertions` improvement addresses the masking concern from the independent review. The only material uncertainty is the unverified Maven run claim (Gap 1); the other gaps are minor or philosophical.
