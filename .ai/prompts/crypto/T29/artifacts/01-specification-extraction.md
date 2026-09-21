# crypto · T29 · Phase 1 — Specification Extraction

## Business Rules

No individual R-numbered requirement targets this task specifically — like T27/T28, this is a
process/verification gate, not a functional behavior. The governing text is twofold:

- `tasks.md`'s task 29 itself: "§11 questions (esp. Q1, Q2, Q3, Q7) are closed **and tests pass**."
- `package.md` §9's own 14-item verification checklist — the literal, itemized form of "tests pass,"
  each unchecked (`[ ]`) in the document's own raw text today. T29's own "tests pass" half is most
  naturally read as: verify each checklist item against real, current evidence, and tick it `[x]` if
  true — mirroring how T28 moved `SECURITY-THREAT-MODEL.md`'s own rows from `tracked` to `closed` with
  citations, not a silent, undocumented status change.

## Locked Decisions

Every LOCKED decision (L1–L15) is indirectly in scope, since §9's checklist enumerates most of them by
number (L1, L3, L4, L5, L6, L7, L10, L11, L12, L13) as explicit gate items. None is newly implemented by
this task — T29 verifies, cites evidence, and (for §9) records the result; it does not design or code
against any of them.

## Files involved

**The one write target:** `spec/crypto-service/package.md` — header block (`Version`, `Status`), §9's
checklist (`[ ]` → `[x]` per item, with a citation, if the evidence supports it), and §11 (Q1/Q2/Q3/Q7
addressed — resolved-with-citation or explicitly-deferred-with-citation, mirroring Q8's own precedent
text: `**Resolved (date):** ... Open follow-up: ...`).

**Read-only, evidence for the above:**
- `package.md` §3 (acceptance criteria) and §8 (named tests) — the ~29 test-method → requirement-ID
  mappings to verify exist and pass.
- The full `services/crypto` test suite (currently 759 tests as of T28 Phase 12).
- `KmsSigner.java`, `FailClosedScreeningClient.java`, `application.properties`'s provider config block —
  already identified in Phase 0 as the concrete evidence for Q3/Q7/Q2/Q1's engineering-level state.
- `services/crypto/Dockerfile` (T27) — for §9's own "Docker image builds" checklist item, now
  potentially actually testable given Docker became available during T28.

**Not touched:** `requirements.md`, `design.md`, `tasks.md`, `agents.md` (read for context only); any
other file under `spec/`; every `services/crypto` source file (no code changes expected — T29 verifies
what T01–T28 already built, it does not build anything new).

## Dependencies

None new. This task depends entirely on the already-implemented, already-tested state of the whole
`services/crypto` module, and on Phase 0's own findings about Q1/Q2/Q3/Q7's real implementation status.

## Acceptance Criteria

1. **AC1 (§11 gate).** Each of Q1, Q2, Q3, Q7 is explicitly addressed in `package.md` §11 — resolved
   with a code citation where the engineering-level question is genuinely settled (Q2, Q3, Q7 per Phase
   0's findings), or explicitly marked as a deliberate, documented deferral to deployment/procurement
   where a real vendor/business decision is the only remaining gap (Q1's "which 3 commercial vendors"
   half). Never left silently untouched, and never marked "Resolved" for a business decision this
   repository's own code cannot make.
2. **AC2 (§9 checklist gate).** Each of the 14 checklist items is checked against real, current
   evidence (a fresh test run, a direct source read — not memory) and marked `[x]` with a citation if
   true, or left `[ ]` with an explicit note if not — never silently ticked without verification.
3. **AC3 (header bump).** Once AC1 and AC2 both genuinely hold, `package.md`'s header changes `Version`
   `0.1` → `0.2` and `Status` `DRAFT` → `READY FOR IMPL`. If either does not fully hold, the header is
   **not** bumped, and this task's own artifacts state clearly why, rather than bump it anyway.

## Tests required

None authored — like T27/T28, this task's own "test" is the verification work itself (AC1/AC2), not new
JUnit tests. If AC2's checklist verification finds a genuine, previously-undetected code gap, Phase 2
will need to decide whether closing it is in this task's own narrow scope or a new follow-up candidate
(mirroring T28's own precedent for the `EndToEndIntegrationTest` defect it found).

## Open Questions

1. **What does "tests pass" mean given T28 Phase 12's own disclosed, pre-existing, unrelated failures**
   (6 failures / 4 errors across `TokenAllowlistRepositoryIntegrationTest`,
   `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
   `ObservationRepositoryIntegrationTest`, `EndToEndIntegrationTest`)? Does AC2/AC3 require these fixed
   first, or does "tests pass" mean this spec's own §3/§8-scoped acceptance criteria and named tests —
   distinct from the wider integration-test surface, most of which predates and is unrelated to this
   spec's own completeness? Not a blocker for Phase 2 to design around, but the Phase 2 TIB must pick
   one interpretation explicitly, mirroring how Phase 0/1 already resolved the identical-shaped ambiguity
   for Q1/Q2/Q3/Q7 (engineering-complete vs. business-final).
2. **Carried forward from Phase 0, unchanged:** whether "closed" for Q1/Q2/Q7 requires a final,
   vendor-confirmed answer (outside this repository's own reach) or a complete, tested,
   deployment-swappable engineering answer with the vendor choice explicitly deferred (which the
   evidence consistently supports and which Q1's own original wording anticipates).
