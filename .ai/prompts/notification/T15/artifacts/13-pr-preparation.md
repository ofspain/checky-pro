# notification · T15 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T15: consumed-contract conformance test`

## Commit message

```
notification-service T15: consumed-contract conformance test

Add ConsumedEventSchemaConformanceTest, the literal package.md §8
named test (shouldConformToConsumedEventSchemas) R19 calls for. The
substance R19 describes was already thoroughly covered at T06 by
EmailRequestedEventContractTest/UserLifecycleEventContractTest, each
already mirroring services/auth's own producer-side contract-test
pattern exactly - this task closes the one real gap between
package.md's own expected test inventory and what actually existed: a
single, literally-named test proving both of this service's own real
consumed schemas (auth.email.requested, auth.user.lifecycle) conform,
alongside - not replacing - the existing, more detailed per-DTO
coverage.

Checks, for both DTOs: every schema-required field is present, no
undeclared field is serialized, and (a widening both the design
challenge and the frozen brief adopted) every present field's own
JSON type matches the schema's declared type - catching a class of
drift (e.g. occurredAt accidentally serialized as a numeric timestamp)
presence-only checks would miss. Both schema checks run independently
via SoftAssertions rather than two sequential hard assertions, so a
simultaneous drift in both DTOs can never mask one another within a
single run - verified empirically, not assumed, by temporarily
swapping which schema was paired with which DTO to force 8 genuine
simultaneous failures, confirming all 8 surfaced in one run, then
reverting.

Re-read both real schema files against both real DTOs field-by-field
before implementing (not merely assumed clean from the pre-existing,
already-passing tests): no drift exists today, confirmed, not merely
expected - no DTO change was needed.

The smallest task in this pipeline to date: one new test file, zero
production code changes, zero new dependencies, zero files modified.
contracts/events/payments/* remains untested because neither the
directory nor services/payment itself exists - the same, already-
accepted blocker that got T07 skipped, disclosed at every phase, not
silently absent.

Three adversarial review rounds (Kimi Phase 3: 8 findings, all
concrete implementation decisions for an already well-scoped brief,
none requiring rejection; Phase 8: 4 findings, 2 concurring with
self-review, 2 new but correctly self-assessed as already-intentional
behavior; Phase 11: 4 gaps, including Kimi's own sandbox lacking
Maven to confirm the test suite's own passing count, resolved with a
fresh, real verification run) were each independently checked against
actual source before disposition, never taken on word.

A real, latent coverage gap was found and closed during this task's
own Phase 10 self-audit: the checking mechanism's own negative paths
(a missing required field, an undeclared field, a type mismatch) had
zero permanent regression-guard coverage - only the fully-conformant
happy path was ever exercised. Closed with 4 new, in-memory tests,
including one synthetic, deliberately-broken schema/payload pair
proving the mechanism genuinely catches all three drift categories it
claims to, not merely that it passes when nothing is wrong.

362 tests total (358 T01-T14 unaffected + 1 new at Phase 6 + the
SoftAssertions strengthening at Phase 9 + 4 more at Phase 10 closing
the mechanism-coverage gap).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/test/java/com/themistra/notification/consumer/dto/ConsumedEventSchemaConformanceTest.java`

**Modified**

None.

**Deleted**

None.

**Process artifacts**
- `.ai/prompts/notification/T15/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

The smallest task in this pipeline to date — a single new test file, zero production code changes,
zero new dependencies, zero files modified. T15 closed a genuine naming gap between `package.md`
§8's own expected test inventory and what actually existed: the substance of R19 was already
thoroughly covered at T06, but no test anywhere was literally named `shouldConformToConsumedEventSchemas`.
The task's own real value ended up being less about the one named test itself and more about the
disciplined audit around it — confirming (not assuming) no DTO/schema drift exists, strengthening
the test with `SoftAssertions` after a genuine masking risk was identified and independently
confirmed by two separate reviews, and closing a real gap where the checking mechanism's own
negative paths had never been proven to actually work, only that they currently had nothing to
flag.

## Testing performed

- `mvn -pl services/notification clean verify` — 362 tests, 0 failures, 0 errors, `BUILD SUCCESS`,
  run fresh multiple times across Phases 6, 9, 10, and once more in direct response to Kimi's own
  Phase 11 Gap #1 (its own review sandbox lacked Maven and could not itself confirm this count).
- Both real consumed schemas (`contracts/events/auth/email-requested.v1.schema.json`,
  `contracts/events/auth/user-lifecycle.v1.schema.json`) directly re-read against both real DTOs
  field-by-field before implementation — confirmed no drift exists, not merely assumed from the
  pre-existing tests already passing.
- The `SoftAssertions` fix (closing a real masking risk both the self-review and Kimi's own
  independent review raised) was verified empirically: temporarily swapped which schema was paired
  with which DTO to force 8 genuine, simultaneous failures across both DTOs, confirmed the real
  failure report listed all 8 in one run, then reverted the injection and confirmed the real test
  passes cleanly again.
- The checking mechanism's own negative paths (a missing required field, an undeclared field, a
  type mismatch) are proven directly via a synthetic, hand-built schema/payload pair deliberately
  broken in all three ways at once — not only the happy path against the two real, already-
  conformant schemas.
- `git diff --stat 7042bf2..HEAD -- services/auth services/crypto services/payment` — empty; no
  sibling service touched.
- `git diff --stat 7042bf2..HEAD -- spec/` — empty; no specification file modified.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 15 ("Consumed-contract tests").
- **Requirements:** R19 (the `auth` half only — `contracts/events/payments/*` does not exist).
- **LOCKED decisions:** L2, L4 (derived at Phase 1 from `design.md`/`agents.md`, since none were
  cited inline in this task's own header).
- **Named test (`package.md` §8):** `shouldConformToConsumedEventSchemas` — present, passing,
  backed by 4 additional regression-guard tests proving its own underlying mechanism, not only its
  current happy-path result.

## Known, deliberate gaps (not this task's scope)

- **`contracts/events/payments/*` remains entirely untested** — the directory and every schema
  under it do not exist; `services/payment` does not exist. The same already-accepted blocker that
  got T07 skipped; resolves automatically once `spec/payment-service/package.md` reaches
  `READY FOR IMPL`.
- **`matchesJsonSchemaType` supports only JSON Schema's single-string `type` form**, not the legal
  array form (e.g. `["string","null"]`) — confirmed unreachable by any of the three real schemas
  under `contracts/events/auth/` today; documented directly in the helper's own Javadoc rather than
  fixed speculatively for a case nothing in this repository needs.
- **No permanent test proves `shouldConformToConsumedEventSchemas` itself wires `SoftAssertions`
  through**, as opposed to the `assertConformsToSchema` helper it calls — raised at Phase 11,
  acknowledged with no test added; the method is four lines and directly reviewable, and the
  underlying mechanism is already proven at the helper level by a dedicated synthetic test.

## Reviewer notes

- **Kimi's Phase 3 design challenge found no flaw to reject** — all 8 findings were sound,
  concrete implementation decisions (schema-parsing approach, the literal-named-test constraint,
  path conventions) for an already well-scoped Phase 2 brief, every one independently re-verified
  against actual source (both real schema files, both real DTOs) before acceptance, not taken on
  word.
- **Kimi's Phase 8 Findings #1/#2 concurred exactly with the self-review's own two findings**
  (the `type`-array-form gap, the sequential-assertion masking risk) — both independently reached
  the same conclusions by different routes, a useful cross-check that neither review was
  overstating or missing something the other caught.
- **Kimi's Phase 11 Gap #1 (its own sandbox lacking Maven) was resolved by actually re-running the
  suite fresh**, not merely re-asserting the earlier claim — the distinction between "a claim Kimi's
  own environment cannot verify" and "a claim nobody ever actually verified" mattered enough to
  settle definitively rather than wave away.
- **Three of Kimi's own Phase 11 gaps (#2, #3, #4) were each correctly self-assessed by Kimi's own
  text as low-value, already-covered, or requiring no action** — all three independently
  re-confirmed rather than taken on word, and all three dispositioned exactly as Kimi's own
  reasoning already concluded.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T15.**
