# crypto · T23 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 7 self-review and Phase 8 (Kimi) independent review.

Phase 8's headline claim — that the 6 primary deliverable files were "absent from both the working tree
and the git index/history" — was **partially inaccurate**: all 6 files existed on disk and were the
actual basis of Phase 6/7's work. The literal claim of "not present in the working tree" was verified
false via `find`. But the substance was real: the "cs t23 kimi independent review prep" commit
(`79e2a44`) staged only the new test files and `pom.xml`, never `git add`ed the 6 contract files
themselves, so Kimi's review tooling — which reads committed/tracked state — genuinely never saw them.
This is a real process gap, not a Kimi hallucination, and is fixed by this phase's own commit.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | 6 contract files (`crypto-internal.yaml` + 5 event schemas) untracked by git, absent from the review-prep commit (CRITICAL) | **ACCEPTED** | Verified the files exist on disk and match Phase 5/6's design exactly (re-read all 6 in full). Staged and committed alongside this phase's fixes so the repository state now matches what was implemented and reviewed. |
| 2 | Phase 7 self-review's claims unverifiable against committed repo state (HIGH) | **RESOLVED BY #1** | The self-review's claims were true against the filesystem at the time they were written (the files existed and were re-checked); the gap was purely that the commit hadn't caught up. No separate action needed beyond #1. |
| 3 | `tx-finalized` schema documents `confirmations`/`addressPoisoningFlag`, neither ever emitted by the real `FinalizedPayload` record (MEDIUM) | **ACCEPTED, DOCUMENT-ONLY** | No code or schema change. Both fields are optional (not in `required`), copied verbatim from `design.md` §4c per the frozen brief's explicit "byte-for-byte" instruction — a Phase 4 decision already locked in, not something Phase 9 should relitigate. `design.md` itself frames `confirmations` as forward-looking guidance for the sibling `seen`/`confirmed` events; its presence on `finalized` is the design's own choice, not an authoring error. Verified via direct source read that `TxLifecyclePublisher.FinalizedPayload` (watch/TxLifecyclePublisher.java:128-131) has neither field today. Flagged as a candidate follow-up task once/if confirmation-count or address-poisoning detection is actually wired into the finalized path — not before. |
| 4 | `watchIdPathParameterIsDeclaredOnTheDeleteOperation` didn't verify `schema.type`/`schema.format`, only `name`/`in`/`required` (LOW) | **ACCEPTED** | Extended the assertion to also require `schema.type == "string"` and `schema.format == "uuid"`, matching the frozen brief's explicit requirement. Verified the YAML already declares both correctly (`contracts/api/crypto-internal.yaml:46-48`), so this only strengthens the test, no contract change. |
| 5 | Unused `PathVariable` import (LOW, cosmetic — also Self-Review #1) | **ACCEPTED** | Import removed. |
| 6 | Missing `controllerRoutes()` bare-`@RequestMapping` limitation disclosure (LOW, documentation — also Self-Review #2) | **ACCEPTED** | Added the disclosure Javadoc, adapted to name `WatchController`/`AttestController`/`VerificationKeysController`, mirroring `AuthOpenApiContractTest`'s identical comment. |

## Files changed in this phase

- `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java` —
  removed the unused `PathVariable` import; strengthened `watchIdPathParameterIsDeclaredOnTheDeleteOperation`
  to check `schema.type`/`schema.format`; added the `controllerRoutes()` disclosure Javadoc.
- `contracts/api/crypto-internal.yaml`, `contracts/events/chain/tx-seen.v1.schema.json`,
  `tx-confirmed.v1.schema.json`, `tx-finalized.v1.schema.json`, `tx-reorged.v1.schema.json`,
  `provider-degraded.v1.schema.json` — staged for the first time (Finding #1); content unchanged from
  Phase 6.

No production code changed — this task has no production code, only contracts and tests.

## Verification

`mvn -pl services/crypto test -Dtest=CryptoInternalOpenApiContractTest,SeenPayloadContractTest,ConfirmedPayloadContractTest,FinalizedPayloadContractTest,ReorgedPayloadContractTest,ProviderDegradedPayloadContractTest,MoneyFieldsAreDecimalStringsContractTest`
— 18/18 pass, including the strengthened watchId assertion.
