# notification · T15 · Phase 2 — Task Implementation Brief

## Task

Resolve the one real gap in this service's own consumed-contract-test coverage: the literal named
test `shouldConformToConsumedEventSchemas` (`package.md` §8) does not exist anywhere today, even
though the substance R19 describes is already thoroughly covered by two existing, T06-era contract
test classes (`EmailRequestedEventContractTest`, `UserLifecycleEventContractTest`), each already
mirroring `services/auth`'s own producer-side pattern exactly. Add one new, small test proving both
of this service's own real consumed schemas conform in a single, literally-named place, and confirm
(not assume) neither existing DTO has silently drifted from its own real schema file since T06.

## Purpose

Closes a naming gap between `package.md` §8's own expected test inventory and what actually exists,
without duplicating the detailed coverage the two existing classes already provide. Gives R19's own
requirement a single, direct regression guard a future reader can find by its own exact name,
consistent with every other named test in this spec package already having a literal, passing
implementation.

## Scope

**In:**
- `consumer/dto/ConsumedEventSchemaConformanceTest.java` — new, small test class, one test method
  named exactly `shouldConformToConsumedEventSchemas`, proving both `EmailRequestedEvent` and
  `UserLifecycleEvent` serialize with every schema-`required` field present and no undeclared field,
  against the real `contracts/events/auth/{email-requested,user-lifecycle}.v1.schema.json` files.
- Re-reading `EmailRequestedEvent`/`UserLifecycleEvent` against their own real schema files
  field-by-field (not just via the existing tests' own already-passing assertions) to confirm no
  genuine drift exists that the existing tests' own structural checks might not catch (e.g. a field
  present in both but with a type mismatch the existing tests don't explicitly assert).

**Out:**
- Any `contracts/events/payments/*` coverage — the directory and every schema under it do not
  exist; `services/payment` does not exist. Out of reach, not a missing deliverable (same
  precondition that got T07 skipped).
- Any change to the existing `EmailRequestedEventContractTest`/`UserLifecycleEventContractTest`
  classes' own test methods — their own coverage is already correct and sufficient; this task adds
  a new, narrower, literally-named test alongside them, it does not replace or duplicate their own
  more exhaustive checks (format constraints, enum/purpose coverage, token-redaction).
- Any change to the real schema files themselves — this service consumes, it does not author or own
  `contracts/events/auth/*` (`services/auth` does); a consumer never unilaterally edits another
  service's own contract.
- ArchUnit / `shouldPreventCrossModuleEntityImports` — a different named test, belonging to task 16
  ("ArchUnit/module boundaries"), not this one.

## Business Rules

- **R19.** The consumed event schemas under `contracts/events/{auth,payments}/` are deserialized
  against; a schema mismatch fails a contract test, not a production delivery.

## Locked Decisions

- **L2.** Consume-only — this task's own tests never make a synchronous cross-service call; they
  exercise local (de)serialization only.
- **L4.** No secrets or tokens in messages or logs — the new test must never print
  `EmailRequestedEvent`'s own raw `token` field (e.g. via an assertion failure message embedding the
  whole record); the existing `toString()` exclusion is read, not modified.

## Dependencies

Jackson `ObjectMapper` (plain, `.findAndRegisterModules()`, `disable(WRITE_DATES_AS_TIMESTAMPS)` —
matches both existing contract test classes exactly, no new dependency); `EmailRequestedEvent`,
`UserLifecycleEvent`; the two real schema files under `contracts/events/auth/`.

## Inputs

None at runtime — this is a static, self-contained unit test exercising hand-constructed DTO
instances against on-disk schema files, mirroring the existing two classes' own identical shape.

## Outputs

A new JUnit test class with one passing test method. No production behavior changes.

## State Changes

None.

## Files to Create

- `services/notification/src/test/java/com/themistra/notification/consumer/dto/ConsumedEventSchemaConformanceTest.java`

## Files to Modify

- `consumer/dto/EmailRequestedEvent.java` / `consumer/dto/UserLifecycleEvent.java` — **only if** the
  field-by-field re-read against the real schema files uncovers a genuine drift the existing tests
  don't already catch. Not expected; disclosed as conditional, not assumed clean without checking.

## Files NOT to Modify

- `contracts/events/auth/email-requested.v1.schema.json`, `contracts/events/auth/user-lifecycle.v1.schema.json`
  — owned by `services/auth`, not this service.
- `consumer/dto/EmailRequestedEventContractTest.java`, `consumer/dto/UserLifecycleEventContractTest.java`
  — already correct; this task adds alongside, not instead of.
- `consumer/AuthEventConsumer.java` — no behavior change, only a new test.
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment`.

## Acceptance Criteria

1. **AC1** (R19). `ConsumedEventSchemaConformanceTest.shouldConformToConsumedEventSchemas` exists,
   passes, and asserts (for both `EmailRequestedEvent` and `UserLifecycleEvent`) every schema-
   `required` field is present in the serialized form and no undeclared field is serialized.
2. **AC2** (R19). No genuine drift exists between either DTO and its own real schema file as of this
   task's own close — confirmed by direct re-reading, not assumed from the pre-existing tests
   already passing.
3. **AC3** (L4). The new test never embeds `EmailRequestedEvent`'s own raw `token` value in an
   assertion description/failure message.
4. **AC4** (R19, scoped by what exists). No test is written against `contracts/events/payments/*`;
   this gap is disclosed in the artifact, not silently absent.

## Required Tests

Named: `shouldConformToConsumedEventSchemas` (R19) — the one new test this task adds. No other new
test is required; the existing `EmailRequestedEventContractTest`/`UserLifecycleEventContractTest`
suites remain the detailed coverage and must still pass unchanged.

## Constraints

- **No new dependency** — plain Jackson only, matching the established, already-justified
  (`target-design.md` §17.5) decision not to add a JSON-Schema-validation library for this few
  contract files.
- **Null handling** — not applicable; no new production code path, only a new test exercising
  already-validated, non-null-by-construction DTO instances.
- **Module boundaries (L11)** — the new test lives in `consumer/dto/`, the same package as the DTOs
  and existing contract tests it sits alongside; no new cross-package dependency.

## Open Questions

No blockers. The one real design decision Phase 1 flagged (how to satisfy the literal named test)
is resolved above: a new, small, literally-named test added alongside the existing coverage, not a
rename or a no-op disposition — consistent with every other named test in this spec package having
a real, passing implementation of its own.
