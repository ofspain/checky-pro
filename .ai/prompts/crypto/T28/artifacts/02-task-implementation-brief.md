# crypto · T28 · Phase 2 — Task Implementation Brief

## Task

Verify each `SECURITY-THREAT-MODEL.md` row (#1–#6) has a corresponding passing test; confirm no
non-attest path can reach `kms:Sign`; confirm no single-provider fact is ever emitted. Update
`SECURITY-THREAT-MODEL.md`'s own `Status` column for rows #1–#6 from `tracked` to `closed`, citing the
verifying test(s), once each is confirmed.

## Purpose

Closes the document's own stated precondition ("must be completed before the first line of
crypto-service code") for the six rows this service owns, and is the direct prerequisite for task 29
(bumping the spec from `DRAFT` to `READY FOR IMPL` 0.2).

## Scope

**In:**
- Reading and citing the existing test suite against each of the six rows.
- Re-running the full suite to confirm the cited tests are real and currently passing (not assumed from
  memory).
- Updating `SECURITY-THREAT-MODEL.md`'s `Status` column (`tracked` → `closed`, with the citing test(s)
  noted inline) for rows #1–#6. This file is **not** under `spec/`, so the "never modify spec/" guardrail
  does not apply to it.
- For row #5: closing only crypto-service's own actual scope (L3 verbatim persistence + S3 WORM
  snapshot, `package.md`'s own "Implementing task" column: T08/T09), and annotating explicitly that the
  hash-chain-ledger and on-chain-anchor portions of the row's mitigation text belong to the Payment
  Service (`ARCHITECTURE.md` §6.5, not yet built) — never silently marking the row `closed` as if the
  full mitigation text were crypto-service's to deliver, and never leaving it `tracked` as if
  crypto-service's own, actually-complete portion were still outstanding.

**Out:**
- Any application/test code change, unless AC1's row-by-row check finds a genuine gap (none identified
  in Phase 0/1; if Phase 6 finds one, it becomes the only in-scope code change).
- Rows #7–#8 (auth-service/payments concerns, explicitly out of this table's crypto-service scope per
  the document's own text).
- `package.md` §11 Q6 (Payment Service's anchor-write endpoint) — cross-service, not cited by any of the
  six rows.
- Any file under `spec/crypto-service/`.
- Task 29 itself (spec status bump) — a separate task.

## Business Rules

- R1 — 2-of-3 quorum fact-truth (Threat #1).
- R4 — verbatim observation logging before quorum decision (Threat #5, crypto-service's own scope).
- R6 — Ethereum finality requires beacon `finalized` checkpoint (Threat #3).
- R7 — Tron finality requires solidified block (Threat #3).
- R11 — reorg walks cursor backward, emits `chain.tx.reorged` (Threat #3).
- R13 — token identity by contract address only (Threat #2).
- R14 — non-allowlisted contract → `UNKNOWN_TOKEN` (Threat #2).
- R17 — address-poisoning prefix/suffix flagging (Threat #6).
- R22 — `kms:Sign` reachable only from the attest path (Threat #4, and the task's own standalone check).

## Locked Decisions

- L1 — 2-of-3 quorum, no single-provider truth.
- L3 — observation log verbatim and written first.
- L4 — finality is a per-chain policy object.
- L6 — reorg is a first-class transition.
- L7 — token identity is contract address only.
- L9 — address-poisoning flagging.
- L11 — KMS-only signing, single path, enforced by ArchUnit + IAM.

## Dependencies

None new. Depends entirely on existing, already-passing tests: `QuorumEvaluatorTest`,
`QuorumDecisionServiceTest`, `WatcherTest`, `TokenValidatorTest`, `AddressValidatorTest`,
`EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`, `ReorgDetectorTest`, `KmsSignerArchitectureTest`,
`ObservationLogTest`, `ObservationSnapshotStoreTest`, `AddressPoisoningDetectorTest`.

## Inputs

`SECURITY-THREAT-MODEL.md`'s current table (6 rows, all `tracked`); the real, current test suite state
(697 tests as of T27 Phase 11).

## Outputs

`SECURITY-THREAT-MODEL.md` with rows #1–#6 updated to `closed`, each citing its verifying test(s);
`artifacts/06-implementation-notes.md` recording the full row-by-row verification with file:line
citations and the fresh `mvn verify` result confirming every cited test currently passes.

## State Changes

None to application state. One document edit: `SECURITY-THREAT-MODEL.md`'s `Status` column, rows #1–#6
only.

## Files to Create

None.

## Files to Modify

- `SECURITY-THREAT-MODEL.md` — `Status` column, rows #1–#6 only.

## Files NOT to Modify

- Every file under `spec/crypto-service/`.
- `SECURITY-THREAT-MODEL.md` rows #7–#8 (out of this service's scope).
- Every existing main/test source file, unless Phase 6 finds a genuine, previously-undetected test gap
  (none expected; if found, the fix is scoped to exactly that gap, nothing more).

## Acceptance Criteria

1. **AC1.** Each of rows #1–#6 cites a real, currently-passing test (file + method), verified by a fresh
   `mvn -pl services/crypto -am verify` run, not assumed from memory or a prior session's own record.
2. **AC2.** `KmsSignerArchitectureTest`'s two ArchUnit rules (R22/L11) are confirmed passing, closing the
   task statement's own standalone "no non-attest path can reach `kms:Sign`" check.
3. **AC3.** The quorum-layer tests (L1) are confirmed passing, closing the task statement's own
   standalone "no single-provider fact is ever emitted" check; if only per-unit coverage exists (no
   system-level assertion), that is disclosed explicitly rather than silently treated as equivalent.
4. **AC4.** `SECURITY-THREAT-MODEL.md` rows #1–#6 show `Status: closed`, each citing its verifying
   test(s); row #5 explicitly scoped to crypto-service's own portion, with the Payment-Service-owned
   remainder noted, not silently absorbed or ignored.

## Required Tests

None authored fresh (Phase 1, unchanged) — this task's own "test" is the fresh, full-suite verification
run itself, per AC1.

## Constraints

- **No production or test code changes**, except in the sole case Phase 1/Phase 0 already anticipated
  (a genuine, previously-undetected gap discovered while checking AC1) — disclosed explicitly if it
  occurs, not assumed away.
- **Document-only edit is scoped narrowly**: `SECURITY-THREAT-MODEL.md`'s `Status` column, rows #1–#6
  only — never its threat descriptions, mitigation text, or rows #7–#8.
- **Verification must be fresh, not remembered**: every cited test must be confirmed passing by an
  actual `mvn verify` run in this phase's own implementation step, not carried over from T27's own
  record without re-checking (T27's own branch-integrity incident is exactly why: remembered state can
  go stale).

## Open Questions

No blockers. Both of Phase 1's open questions are resolved as working decisions above (update
`Status` to `closed` with citations; scope row #5 to crypto-service's own portion, annotate the rest) —
subject to Phase 3/4 challenge like any other design choice in this pipeline, not left ambiguous.
