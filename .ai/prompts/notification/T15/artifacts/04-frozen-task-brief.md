STATUS: FROZEN

# notification · T15 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings verified directly against actual source before disposition — Kimi's own factual
claims (schema shapes, field names/types, path conventions) were independently re-confirmed by
reading both real schema files and both real DTOs side-by-side, not taken on word. All 8 are
**ACCEPTED**; none rejected — this phase's own job here was mostly concrete implementation decisions
for a narrow, already well-scoped task, not correcting a flawed brief.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | How to validate against JSON Schema without a new dependency | — | **ACCEPTED** | Plain Jackson `ObjectMapper.readTree` on the real schema file; read `required`/`properties` manually, mirroring both existing contract test classes' own identical pattern exactly. |
| 2 | Should the new test also verify type-level drift? | — | **ACCEPTED**, scope widened slightly beyond AC1's literal text | A lightweight per-property JSON-type check (`string`/`integer`/`number`/`boolean`/`object`/`array`) is added alongside the required/undeclared checks. Verified directly: both real schemas' own properties are 100% string-typed today, so this is trivially satisfied now and exists purely to catch a *future* drift (e.g. `occurredAt` accidentally serialized as a numeric timestamp) that presence-only checks would miss - a real, cheap robustness gain, not scope creep, since Phase 1's own "boundary tests implied" section already anticipated exactly this class of check. |
| 3 | Avoiding raw token leakage in assertion failure messages | — | **ACCEPTED** | Every assertion operates on `JsonNode` field names/types only, never the raw serialized JSON string; failure messages name only the field (e.g. `"required field 'token' present"`), never its value. Matches L4/AC3 exactly. |
| 4 | One literal `shouldConformToConsumedEventSchemas()` covering both DTOs | — | **ACCEPTED** | A single `@Test` method calls one private helper per DTO/schema pair in sequence - preserves the exact `package.md` §8 name while still covering both real consumed schemas. |
| 5 | Schema file path convention | — | **ACCEPTED** | `Path.of("../../contracts/events/auth/...")`, identical to both existing contract test classes - already proven to resolve correctly under Surefire's own working-directory convention. |
| 6 | What to do if re-reading uncovers genuine drift | — | **ACCEPTED, and independently reverified**: no drift exists | Directly re-read both real schema files against both real DTOs field-by-field during this phase (not deferred to Phase 6): `email-requested.v1.schema.json`'s 5 required, string-typed, `additionalProperties:false` properties (`accountUuid`, `purpose`, `token`, `email`, `occurredAt`) match `EmailRequestedEvent`'s own 5 record components exactly in name and effective JSON type; `user-lifecycle.v1.schema.json`'s identical shape (`accountUuid`, `status`, `email`, `eventType`, `occurredAt`) matches `UserLifecycleEvent`'s own 5 components exactly. No DTO change is needed - confirmed, not merely expected. |
| 7 | `contracts/events/payments/` scoped out explicitly | — | **ACCEPTED** | Unchanged from Phase 1/2 - `services/payment` and every file under `contracts/events/payments/` do not exist; disclosed as out-of-reach, not a missing deliverable. |
| 8 | No production/dependency/package changes beyond the one new test file | — | **ACCEPTED** | Confirmed - `jackson-databind` and AssertJ are both already present; no `pom.xml` change; new class lives in the same `consumer/dto` package as the DTOs and existing contract tests it sits alongside. |

## Task

Unchanged from Phase 2, with Finding #2's type-check widening folded in: add one new test class,
`consumer/dto/ConsumedEventSchemaConformanceTest.java`, with exactly one test method,
`shouldConformToConsumedEventSchemas()`, proving (for both `EmailRequestedEvent` and
`UserLifecycleEvent`) that the serialized form has every schema-`required` field present, no
undeclared field, and every present field's own JSON type matches the schema's declared `type`.

## Scope

**In:** Unchanged from Phase 2, plus the type-check assertion (Finding #2).

**Out:** Unchanged from Phase 2 - no `contracts/events/payments/*` coverage; no change to the two
existing, already-correct contract test classes; no change to either real schema file; no DTO
change (confirmed unnecessary by this phase's own direct re-read, not merely assumed).

## Business Rules

Unchanged from Phase 1: R19.

## Locked Decisions

Unchanged from Phase 1/2: L2, L4.

## Dependencies

Unchanged from Phase 2: plain Jackson `ObjectMapper`, the two real schema files, the two real DTOs.

## Files to Create

- `services/notification/src/test/java/com/themistra/notification/consumer/dto/ConsumedEventSchemaConformanceTest.java`

## Files to Modify

None. Phase 4's own direct re-read (Finding #6) confirmed no drift exists - the conditional DTO
change Phase 2 disclosed as possible is not needed.

## Files NOT to Modify

Unchanged from Phase 2: both real schema files, both existing contract test classes,
`consumer/AuthEventConsumer.java`, every file under `spec/`, every sibling service.

## Acceptance Criteria

Unchanged AC1-AC4 from Phase 2, with AC1 now explicit about the type check:
1. **AC1.** `shouldConformToConsumedEventSchemas` exists, passes, and asserts for both DTOs: every
   schema-`required` field present, no undeclared field serialized, and every present field's own
   JSON type matches the schema's declared `type`.
2. **AC2.** No genuine drift exists between either DTO and its own real schema file - confirmed
   directly at Phase 4 (see Finding #6's own disposition), re-confirmed by the new test itself at
   Phase 6.
3. **AC3.** The new test never embeds `EmailRequestedEvent`'s own raw `token` value in any assertion
   description or failure message - field names and JSON node types only.
4. **AC4.** No test is written against `contracts/events/payments/*`; disclosed, not silently
   absent.

## Required Tests

Unchanged from Phase 2: the one new named test. The existing `EmailRequestedEventContractTest`/
`UserLifecycleEventContractTest` suites remain required to still pass unchanged.

## Constraints

Unchanged from Phase 2, plus: the type-check helper (Finding #2) must stay a small, private,
general-purpose method mapping JSON Schema primitive type names to Jackson `JsonNodeType`/`JsonNode`
predicates - it must not grow into a half-baked, ad hoc JSON Schema validator (format/pattern/enum
validation stays the existing, more detailed tests' own job, not duplicated here).

## Open Questions

No blockers. All 8 Phase 3 findings resolved above, every one ACCEPTED, none required rejecting a
flawed proposal - this task's own Phase 2 brief was already sound, and Phase 3's own job here was
mostly concrete implementation guidance for a narrow, already well-scoped task.
