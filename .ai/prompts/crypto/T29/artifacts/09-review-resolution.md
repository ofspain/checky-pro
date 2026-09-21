# crypto · T29 · Phase 9 — Review Resolution (Human Approval Gate)

## Kimi Phase 8 findings — dispositions

All 9 findings independently verified against real source before disposition.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Bumped to `READY FOR IMPL` while item 13 is unchecked | **REJECTED — already explicitly decided.** | This exact tension was presented to the user directly at Phase 4 (not a routine gate) with two real options: disclose-and-bump vs. block-the-bump-entirely. The user chose disclose-and-bump. Kimi's review, running after that decision, correctly re-surfaces the tension but doesn't add new information beyond what was already weighed. |
| 2 | Item 13's failure is a real build defect; file a tracked issue | **ACCEPTED, without fabricating a ticket ID.** | Strengthened the follow-up language ("Tracked follow-up, needed before first real deploy") but did not invent an issue number — this repository has no ticket tracker wired into this workflow, matching the established T25 precedent (no fabricated ticket IDs). |
| 3 | Test-run numbers aren't reproducible; no failing-test list | **ACCEPTED.** | Added the exact 6 failing and (implicitly, by class) 4 erroring test method names, plus the commit hash the run was captured at, to item 13's citation. |
| 4 | Q7's "verified end-to-end" claim is unclear given Docker/build failures | **ACCEPTED — verified true, strengthened with direct evidence.** | Re-ran `KmsSignerLocalStackIntegrationTest` directly: genuinely passes today (Docker is available). Q7's note now states this explicitly, dated, rather than leaving it ambiguous whether the citation was aspirational or actual. |
| 5 | New regression test is brittle to markdown formatting (exact substrings) | **REJECTED.** | Exact-substring matching is this file's own established, deliberate style (its own class Javadoc: "a 'this content must be present/absent' scan, not a structural bytecode analysis"), used identically by every other method in this file. Low confidence given by Kimi; not worth deviating from established convention for. |
| 6 | New regression test doesn't guard Q1/Q2/Q3/Q7's own resolution notes | **ACCEPTED.** | Added `resolvedOpenQuestionsCarryTheirResolutionNoteAndUnresolvedOnesDoNotClaimThisTasksResolution`, reusing the existing generic `rowStartingWith` helper — no new helper method needed. |
| 7 | Header still has placeholder `<name>`/`TBD` author/implementer fields | **REJECTED — confirmed deliberate, repo-wide convention.** | `spec/auth-service/package.md` — already `READY FOR IMPL`, version `0.2` — uses the identical `<name>`/`TBD` fields and states explicitly: "`Status: READY FOR IMPL` / `Implementer: TBD` is intentional, not stale." Inventing a name/assignee here would be worse than the honest placeholder. |
| 8 | Title still says "Phase 1" despite the status bump | **REJECTED — confirmed intentional naming.** | `ARCHITECTURE.md` line 1: "Themistra — Phase 1 System Architecture... Phase 1 (Blockchain Payment Verification) with explicit extension points for Phases 2–5." "Phase 1" names the platform's own launch scope, not this document's maturity state — `CRYPTO-PHASE1`/`AUTH-PHASE1` are stable spec IDs referencing it, matching the sibling auth spec's identical naming. |
| 9 | No regression guard against item 13 being silently marked complete | **ACCEPTED.** | Added `item13StaysHonestlyDisclosedAsGenuinelyFailingUntilTheFollowUpLands`, asserting the phrase "genuinely fails" remains present — a future fix should update this test deliberately, not leave it silently green by accident. |

## Verification performed

- `spec/auth-service/package.md` and `ARCHITECTURE.md` read directly to confirm Findings #7/#8 describe
  intentional, pre-existing repo conventions, not oversights.
- `mvn -pl services/crypto -am test -Dtest=KmsSignerLocalStackIntegrationTest` — confirmed passing today
  (Docker available), closing Finding #4 with direct evidence.
- `mvn -pl services/crypto -am test -Dtest=TokenAllowlistRepositoryIntegrationTest,ProviderHealthRepositoryIntegrationTest,QuorumDecisionRepositoryIntegrationTest,ObservationRepositoryIntegrationTest`
  — captured the exact 6 failing method names for Finding #3.
- `mvn -pl services/crypto -am test -Dtest=T01SkeletonRegressionTest` — 9/9 passing (7 existing + 2 new).
- `mvn -pl services/crypto -am verify` (full module) — 762 tests, 6 failures, 4 errors, unchanged from
  Phase 7's own record.
