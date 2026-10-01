# notification · T15 · Phase 1 — Specification Extraction

## Business Rules

- **R19**. WHERE the consumed event schemas under `contracts/events/{auth,payments}/` are authored,
  THEN the consumers SHALL deserialize against them and a schema mismatch SHALL fail a contract
  test rather than a production delivery.

## Locked Decisions

- **L2**. Consume-only at launch — this service reacts to events and renders what they carry; it
  makes no synchronous cross-service call. Directly frames this task's own scope: a contract test
  proves the *shape* of what's consumed is still correct, it does not call any other service.
- **L4**. No secrets or tokens in messages or logs — `EmailRequestedEvent` carries a raw
  verification/reset `token` field; any contract test touching this record must preserve its
  existing `toString()` exclusion (already established at T06), not weaken or bypass it.

`agents.md` (lines 37-39, verbatim): "Event schemas live in `contracts/events/`, are versioned, and
evolve backward-compatibly only. Deserialization models are generated from `contracts/` — never
hand-written. A schema mismatch fails a contract test, not a production delivery." The last sentence
is R19's own source text, restated as a numbered requirement. The middle sentence ("never
hand-written") is a standing rule this service's own existing `EmailRequestedEvent`/
`UserLifecycleEvent` DTOs already disclosed violating at T06 (no codegen tooling exists anywhere in
this repo for `contracts/events/*` — `services/auth`'s own producer-side payload records are
hand-written for the identical reason) — an already-accepted deviation this task does not reopen,
only one this task's own contract tests continue to substitute for.

## Files involved

**Existing — already appear to satisfy this task's own substance, to confirm/extend, not replace:**
- `consumer/dto/EmailRequestedEventContractTest.java` / `consumer/dto/UserLifecycleEventContractTest.java`
  (T06) — already assert required-field presence, no-undeclared-field, enum-value coverage, and
  (for `UserLifecycleEvent`) format constraints, against the real schema files, mirroring
  `services/auth`'s own identical pattern exactly (confirmed directly — both Javadocs state this).
- `consumer/dto/EmailRequestedEvent.java` / `consumer/dto/UserLifecycleEvent.java` (T06) — the
  records under test; not expected to change unless a genuine schema drift is found.
- `contracts/events/auth/email-requested.v1.schema.json` / `user-lifecycle.v1.schema.json` — the
  schemas themselves; `spec/` and `contracts/` guardrails mean these are read-only from this task's
  own perspective regardless.

**Named in the task statement but not reachable today:**
- `contracts/events/payments/*` — does not exist. No schema file, no directory. `services/payment`
  does not exist either (confirmed at Phase 0). Nothing in this task's own real scope can test
  against a schema that was never authored.

## Dependencies

Jackson `ObjectMapper` (plain, `.findAndRegisterModules()`, no new library — `target-design.md`
§17.5's own stated reason for not adding a JSON-Schema-validation dependency for this few contract
files); the two real schema files under `contracts/events/auth/`; `EmailRequestedEvent`/
`UserLifecycleEvent` themselves; `services/auth`'s own
`EmailRequestedEventPayloadContractTest`/`UserLifecycleEventPayloadContractTest` as the pattern to
mirror (already mirrored, to be confirmed for drift, not re-authored from scratch).

## Acceptance Criteria

1. **AC1** (R19). Every event payload this service actually consumes (`auth.email.requested`,
   `auth.user.lifecycle` — the only two real `@KafkaListener` topics in this codebase) has a
   contract test proving its own DTO's serialized/deserialized shape matches the real, authored
   schema file byte-for-byte in substance (required fields present, no undeclared fields, every
   known enum value covered).
2. **AC2** (R19, `package.md` §8). The named test `shouldConformToConsumedEventSchemas` exists and
   passes — either as a new test with this exact name, or by confirming the existing, differently-
   named tests already satisfy its own intent and resolving the naming gap explicitly (a Phase 2/3
   design decision, not assumed here).
3. **AC3** (L4). No contract test, and no DTO it exercises, ever lets the raw `token` field reach a
   log line or an unredacted string representation — the existing `EmailRequestedEvent.toString()`
   exclusion and its own dedicated test (`toStringExcludesTheRawToken`) must remain intact.
4. **AC4** (R19, scoped by what actually exists). No contract test is written against
   `contracts/events/payments/*`, since neither the directory nor any schema under it exists — this
   is a disclosed, out-of-reach gap, not a missing deliverable this task can produce.

## Tests required

- Named (`package.md` §8): `shouldConformToConsumedEventSchemas` (R19) — resolution of exactly how
  this name is satisfied (rename, new thin test, or an explicit documented mapping to the two
  existing, differently-named contract test classes) is Phase 2's own job.
- Already present and presumed to remain required: `EmailRequestedEventContractTest`'s and
  `UserLifecycleEventContractTest`'s own full existing test methods (schema conformance, format
  constraints, enum/purpose coverage, the token-exclusion test) — this task's own job includes
  confirming none of them has silently drifted from the real schema files since T06, not only
  adding something new.
- Boundary / implied: a genuine schema/DTO mismatch (e.g. a required field the schema lists that the
  DTO's own serialized form omits) must make the relevant contract test fail — already implicitly
  true by construction (the existing tests read the schema's own `required` array and assert
  presence against it), worth confirming by inspection rather than by deliberately breaking
  production code to prove a negative.

## Open Questions

- **Not a blocker, but a genuine design decision for Phase 2**: how should the literal named test
  `shouldConformToConsumedEventSchemas` (`package.md` §8) be satisfied, given the substance it
  describes already exists under two different, task-appropriately-named test classes? Options
  (not decided here): (a) add a new, thin test literally named `shouldConformToConsumedEventSchemas`
  that exercises both existing DTOs' own schema conformance in one place; (b) rename one existing
  test method to this exact name; (c) document explicitly that the named test's own intent is
  already satisfied by the two existing classes and no literal rename/addition is needed. This does
  not block extraction — it is squarely what Phase 2's own Task Implementation Brief exists to
  decide.
- **Not a blocker**: the "payments" half of R19's own literal wording
  (`contracts/events/{auth,payments}/*`) cannot be satisfied today — `contracts/events/payments/`
  and `services/payment` do not exist (the same precondition that got T07 skipped,
  memory-recorded). This task's own real scope is therefore the `auth` half only, until
  `spec/payment-service/package.md` reaches `READY FOR IMPL` and T07 unblocks — consistent with how
  T08/T09 already handled the identical payment-event gap without it blocking their own progress.
- **No blocker to starting Phase 2.**
