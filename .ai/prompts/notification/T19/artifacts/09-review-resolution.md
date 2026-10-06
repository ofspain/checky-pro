# notification · T19 · Phase 9 — Review Resolution

**Human Approval gate.** Resolution log for the Phase 7 self-review (3 findings) and Kimi's Phase 8
independent review (6 findings). Every claim was checked against the test code and a real run before
disposition.

## Finding 1 (self-review + Kimi concur) · Template-version check covered EMAIL rows only

**ACCEPTED and fixed.** The check now runs over every row the chain renders, EMAIL and IN_APP alike.
Before the fix, the IN_APP rendered row was unchecked, so AC3's stated scope was wider than its test.
Verified by the fresh run: all rows carry a non-null template version.

## Finding 2 (self-review + Kimi concur) · The AC5 scan is textual and partial

**ACCEPTED as disclosed, no code change.** The scan catches literal `UPDATE`/`DELETE` SQL and
`deliveryLogRepository.delete*` calls. It does not catch `EntityManager` operations or dynamically
built SQL. The authoritative guarantee is AC1: the database grant rejects any such statement however
it was built. Recorded for the Phase 12 verification.

## Finding 3 (self-review + Kimi concur) · The scan's relative path depends on the working directory

**ACCEPTED, no change.** Surefire runs from the module directory, so the path resolves. A wrong
working directory fails loudly with `NoSuchFileException`, not silently.

## Finding 4 (Kimi) · AC2 should assert the exact attempt sequence, not only a distinct count

**ACCEPTED and fixed.** The EMAIL attempts are now asserted to be exactly `{1, 2}`, making the
append-only progression explicit for a future reader. Verified by the fresh run.

## Finding 5 (Kimi) · AC4 should assert the SUPPRESSED row carries no template version

**ACCEPTED and fixed.** Verified against source first: `DeliveryOrchestrator.java:199` writes
SUPPRESSED rows with a null template version, because nothing was rendered. The test now asserts it,
tying AC4 to AC3's rendered-rows scope. Verified by the fresh run.

## Finding 6 (Kimi) · Maven verification cannot be confirmed in Kimi's environment

**ACCEPTED as correctly flagged for Kimi's sandbox, re-verified here.** Kimi has no Maven. This
environment has it, and the full suite was re-run fresh after these changes: 373 tests, 0 failures,
0 errors, exit 0.

## Summary

Three real strengthenings applied (Findings 1, 4, 5), each verified against source and a fresh run.
Findings 2 and 3 are disclosures with no code change. Finding 6 is closed by the re-run.

Full suite: `mvn -pl services/notification clean verify` — 373 tests, 0 failures, 0 errors, exit 0.
