# crypto · T29 · Phase 2 — Task Implementation Brief

## Task

Verify `package.md` §11's Q1/Q2/Q3/Q7 and §9's 14-item checklist against real, current evidence;
address each explicitly (resolved-with-citation, or explicitly-deferred-with-citation); bump the header
`Version` `0.1`→`0.2` and `Status` `DRAFT`→`READY FOR IMPL` only if both gates genuinely hold.

## Purpose

Closes the final task in `spec/crypto-service/tasks.md`'s own ordered list — the formal declaration that
this spec is ready for implementation, now that T01–T28 have actually implemented and verified it.

## Scope

**In:**
- `package.md` header (`Version`, `Status`).
- `package.md` §9 — tick `[ ]` → `[x]` per item with a citation, only where evidence genuinely supports
  it.
- `package.md` §11 — Q1/Q2/Q3/Q7, using Q8's own precedent style (`**Resolved (date):** ... Open
  follow-up: ...` where a real deferral remains).

**Out:**
- Any change to `services/crypto` source (no code gap is expected; if AC2's checklist verification finds
  one, it becomes a new follow-up candidate per the T28 precedent, not an in-scope fix here).
- `requirements.md`, `design.md`, `tasks.md`, `agents.md`.
- `package.md` §8's own named-test text, even though Phase 1's verification found 3 of 27 checked names
  exhibit cosmetic naming drift from their real, implemented method names (`shouldOnlyAllowAttestPathToInvokeKmsSign`
  vs. the real `...IsCheckedDuringStandardBuild` canary suffix; the deterministic-idempotency-key and
  internal-scope tests are covered per-emitter/per-endpoint rather than by one unified method). This is
  the same, already-accepted pattern this session found and closed with "no new action" in auth-service
  T35 — package.md's own prose drifting from implementation reality is a standing, known, low-priority
  documentation gap, not a functional one, and not this task's own scope to fix.

## Business Rules

None individually targeted (Phase 1, unchanged) — governed by `tasks.md` task 29's own text and
`package.md` §9's checklist.

## Locked Decisions

L1–L15, all indirectly gated via §9's checklist; none designed or implemented fresh here.

## Working decisions (Phase 1's open questions, resolved)

1. **"Tests pass" means this spec's own §3/§8-scoped acceptance criteria and named tests, not the
   entire, wider integration-test surface.** All 27 checked named tests from §8 have real, passing
   corresponding coverage (verified directly this phase — see below), closing this half of the gate.
   T28 Phase 12's own disclosed, pre-existing, unrelated failures (`TokenAllowlistRepositoryIntegrationTest`,
   `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
   `ObservationRepositoryIntegrationTest`, `EndToEndIntegrationTest`) predate this spec's own scope and
   are already separately tracked as follow-up candidates — they do not block this task's own gate, and
   this distinction will be stated explicitly in the header-bump rationale, not silently glossed over.
2. **Q1/Q2/Q7 "closed" means a complete, tested, deployment-swappable engineering answer, with the pure
   vendor/procurement decision explicitly and permanently deferred** — matching Q1's own original
   wording ("blocker for real deployment, not for fake-provider tests") and Q8's own precedent
   (`**Resolved:** ... **Open follow-up:** ...`). Not a final, vendor-confirmed business answer, which no
   amount of code verification can produce and which is not this repository's own decision to make.

## Dependencies

None new.

## Files to Create

None.

## Files to Modify

- `spec/crypto-service/package.md` — header, §9, §11 only.

## Files NOT to Modify

- Every other file under `spec/`.
- Every `services/crypto` source file.

## Acceptance Criteria

1. **AC1.** §11 Q1, Q2, Q3, Q7 each carry an explicit resolution note, citing real code (Q2/Q3/Q7) or
   explicitly deferring the vendor-selection half (Q1), per the working decision above.
2. **AC2.** §9's 14 items are each checked against fresh evidence (a real test run this phase, not
   memory) and marked `[x]` with a citation where true. Already confirmed this phase: all 27 named tests
   from §8 exist and pass (3 with disclosed cosmetic naming drift, not a functional gap).
3. **AC3.** The header is bumped to `0.2`/`READY FOR IMPL` only if AC1 and AC2 both hold in full: no item
   left unaddressed, no citation invented.

## Required Tests

None authored (Phase 1, unchanged).

## Constraints

- No production or test code changes expected; if AC2 finds a genuine gap, it is disclosed as a new
  follow-up candidate, not silently fixed or silently ignored.
- Every `[x]` and every "Resolved" in §9/§11 must cite real, freshly-checked evidence — never a
  carried-over assumption from an earlier phase's memory.

## Open Questions

No blockers. Both of Phase 1's open questions are resolved as working decisions above, subject to Phase
3/4 challenge like any other design choice in this pipeline.
