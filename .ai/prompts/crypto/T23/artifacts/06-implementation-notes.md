# crypto · T23 · Phase 6 — Implementation Notes

## What changed

**Created (contract files — no runtime code touched):**
- `contracts/api/crypto-internal.yaml` — 4 routes, 6 `components.schemas`, one `bearerAuth` security
  scheme; mirrors `auth.yaml`'s exact structure.
- `contracts/events/chain/tx-finalized.v1.schema.json` — copied byte-for-byte from `design.md` §4c
  (verified via `diff` against the extracted spec text: the only difference was the markdown code-fence
  lines from the source document, not the JSON content itself).
- `contracts/events/chain/tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json`,
  `tx-reorged.v1.schema.json`, `provider-degraded.v1.schema.json` — authored from each event's own real
  payload record field list (re-verified by direct source reading in Phase 5), not from `design.md`'s
  prose guidance alone.

**Created (tests):**
- `common/CryptoInternalOpenApiContractTest.java` (10 tests) — mirrors `AuthOpenApiContractTest`'s exact
  9-method structure, plus the two new Phase 4 findings (`watchIdPathParameterIsDeclaredOnTheDeleteOperation`,
  `attestResponseOutcomeSchemaEnumMatchesTheTwoValuesTheDtoCanActuallyProduce`).
- `watch/SeenPayloadContractTest.java`, `ConfirmedPayloadContractTest.java`, `FinalizedPayloadContractTest.java`,
  `reorg/ReorgedPayloadContractTest.java` (1 test each) — mirror `UserLifecycleEventPayloadContractTest`'s
  exact technique.
- `provider/ProviderDegradedPayloadContractTest.java` (2 tests) — the structural check plus the
  `DegradationReason` enum-drift guard (AC5).
- `MoneyFieldsAreDecimalStringsContractTest.java` (2 tests, root `com.themistra.crypto` package) — the
  new, centralized money-discipline test (Finding #6) walking all 6 contract files.

**Modified:**
- `services/crypto/pom.xml` — added `jackson-dataformat-yaml` (test-scope), copied exactly from
  `services/auth/pom.xml`'s identical addition.

## Deviations from the plan, forced by reality

One real bug caught and fixed during implementation, not assumed away: `realInstancesByComponentName()`'s
initial design used synthetic keys `"AttestResponse-signed"`/`"AttestResponse-blocked"` to check both
`AttestResponse` variants (Phase 4 Finding #4) against the *same* YAML schema entry, but the test's
schema-lookup line (`components.get(componentName)`) would have looked for a schema literally named
`"AttestResponse-signed"`, which doesn't exist. Caught by writing the test, not by inspection — fixed by
stripping the `-` suffix before the schema lookup while keeping the full synthetic key in assertion
messages for clarity (so a failure still names which variant failed).

No other deviation — every other file matched Phase 5's planned structure exactly on first attempt.

## Mapping to acceptance criteria

AC1 (`CryptoInternalOpenApiContractTest.shouldConformToCryptoInternalOpenApiContract`, the named test),
AC2 (the 5 event-payload contract tests, all passing), AC3 (all 5 schemas correctly document
`idempotencyKey`'s presence or, for `provider-degraded`, its documented absence), AC4
(`MoneyFieldsAreDecimalStringsContractTest`, now executable), AC5 (`everyDegradationReasonValueIsCoveredByTheSchemaEnum`
and `attestResponseOutcomeSchemaEnumMatchesTheTwoValuesTheDtoCanActuallyProduce`), AC6 (`tx-finalized`'s
verbatim fidelity, verified via `diff`).

## Verification

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly.
`mvn -pl services/crypto test -Dtest=CryptoInternalOpenApiContractTest,SeenPayloadContractTest,ConfirmedPayloadContractTest,FinalizedPayloadContractTest,ReorgedPayloadContractTest,ProviderDegradedPayloadContractTest,MoneyFieldsAreDecimalStringsContractTest`
— 18/18 pass. Full module regression (`mvn -pl services/crypto -am test`): 738 tests (720 pre-existing +
18 new), 6 failures, all pre-existing and unrelated to this task (disclosed since T18-T22). Zero
regressions.
