# notification · T15 · Phase 10 — Test Generation

T15's own tests were written incrementally across Phase 6 (the one named test) and Phase 9
(`SoftAssertions`, strengthening it). This phase's own job was an audit against the frozen brief's
Required Tests list, which found one real gap: the checking mechanism itself — `matchesJsonSchemaType`
and the three-category check inside `assertConformsToSchema` (missing-required, undeclared,
type-mismatch) — had zero permanent regression-guard coverage. The one named test only ever
exercises the fully-conformant happy path against the two real, currently-matching schemas; if any
of the three checks were silently broken or removed, nothing would fail. No production code
changed in this phase.

## Gap found and closed in this phase

- **The checking mechanism's own negative paths were never proven.** Closed with four new tests, all
  in-memory (no real schema file needed for the negative-path proofs):
  - `matchesJsonSchemaTypeAcceptsEveryDeclaredJsonSchemaType` — direct unit coverage of all 6 valid
    JSON Schema primitive types the helper supports, not only `"string"` (the only one either real
    schema exercises today).
  - `matchesJsonSchemaTypeRejectsAMismatchedNode` — proves the helper actually returns `false` for a
    genuine mismatch, not merely `true` for a match.
  - `matchesJsonSchemaTypeThrowsForAnUnrecognizedDeclaredType` — the `default` branch.
  - `assertConformsToSchemaCatchesAMissingRequiredFieldAnUndeclaredFieldAndATypeMismatch` — a
    synthetic, hand-built schema/payload pair deliberately broken in all three ways
    `shouldConformToConsumedEventSchemas` relies on catching (AC1): a missing required field, an
    undeclared field, and a type mismatch, all in one payload. Uses a real, non-throwing
    `SoftAssertions` instance directly (not `assertSoftly`) so the exact 3 collected errors can be
    inspected without the test itself failing.

## Test manifest

| Test method | Verifies | AC / Requirement |
|---|---|---|
| `shouldConformToConsumedEventSchemas` (named) | Both real consumed schemas (`auth.email.requested`, `auth.user.lifecycle`) conform: required-present, no-undeclared, type-match, with both DTOs checked independently via `SoftAssertions` | **R19**, AC1, AC2 |
| `matchesJsonSchemaTypeAcceptsEveryDeclaredJsonSchemaType` | All 6 valid JSON Schema primitive types the helper supports | AC1 (regression guard) |
| `matchesJsonSchemaTypeRejectsAMismatchedNode` | The helper correctly rejects a genuine type mismatch | AC1 (regression guard) |
| `matchesJsonSchemaTypeThrowsForAnUnrecognizedDeclaredType` | The disclosed, documented `default` branch | — |
| `assertConformsToSchemaCatchesAMissingRequiredFieldAnUndeclaredFieldAndATypeMismatch` | The full mechanism genuinely catches all three drift categories AC1 claims, in one synthetic, deliberately-broken payload | AC1 (regression guard) |
| `EmailRequestedEventContractTest` (4 tests, T06, unaffected) | Detailed per-DTO coverage (format constraints, purpose values, token redaction) | R19 |
| `UserLifecycleEventContractTest` (4 tests, T06, unaffected) | Detailed per-DTO coverage (format constraints, event-type/status enum coverage) | R19 |

## Verification

`mvn -pl services/notification clean verify` — 362 tests, 0 failures, 0 errors (358 + 4 new). No
production code was modified in this phase.
