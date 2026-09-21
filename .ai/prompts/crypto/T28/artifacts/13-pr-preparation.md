# crypto · T28 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Close SECURITY-THREAT-MODEL.md rows #1-#6 with named, verified tests (T28)
```

## Commit message

```
Close SECURITY-THREAT-MODEL.md rows #1-#6 with named, verified tests (T28)

Verifies each of the six crypto-service-owned threat-model rows
against the real, current test suite and updates Status from tracked
to closed, each citing the exact test(s) that verify it - not
assumed, checked directly, row by row. Confirms the task statement's
own two standalone checks: kms:Sign is reachable only from the attest
path (KmsSignerArchitectureTest, code-path half; IAM/runtime half
disclosed as infrastructure-owned) and no single-provider fact is
ever emitted (WatcherTest, now covering the single-provider case
explicitly and asserting the observation is still logged even when
quorum never fires).

Row #5's mitigation text spans two services - the hash-chain ledger
and on-chain anchor belong to the not-yet-built Payment Service
(ARCHITECTURE.md 6.5), not crypto-service. Closed only crypto-service's
own portion (verbatim persistence, S3 WORM snapshot, DB-grant-enforced
append-only), annotated explicitly rather than silently absorbed.

Docker became available in this environment mid-task, for the first
time since T23. Re-running with it available upgraded row #5's own
DB-grant test from "not yet executed" to a confirmed pass, and
exposed that row #3's own cited integration test genuinely fails on
a pre-existing, previously undiscoverable Spring-context/Flyway
defect in T26's own EndToEndIntegrationTest - unrelated to this task
and corrected in the citation rather than expanded into a fix. ~10
further, unrelated pre-existing failures also surfaced elsewhere in
the suite for the same reason; flagged as follow-up candidates, not
investigated.

T01SkeletonRegressionTest's own regression guard - written when
"tracked" was the correct expected state - was updated to assert
"closed" and strengthened to require a real test citation plus each
row's own documented caveat, closing a gap its own predecessor
couldn't have anticipated.
```

## Testing performed

- `mvn -pl services/crypto -am test -Dtest=WatcherTest,T01SkeletonRegressionTest` — 74/74 passing,
  covering every test method this task's own edits touched, re-run after each incremental change across
  Phases 6, 9, and 11.
- `mvn -pl services/crypto -am verify` (full module, run fresh at Phases 6, 9, 11, and 12) — final,
  stable result: 759 tests, 6 failures, 4 errors, none in any test this task cites for a threat-model
  row's closure; all in pre-existing, unrelated defects surfaced only because Docker became available
  mid-task.
- Direct source verification throughout: DB grants (`V2__crypto_app_role_and_grants.sql`), Spring bean
  wiring (`Watcher.logObservation`'s call graph), and `ArchRuleDefinition.noClasses()`'s negation
  semantics were all read and confirmed directly, not assumed.
- Full traceability against Phase 1's AC1a/AC1b/AC2/AC3/AC4: `artifacts/12-specification-verification.md`
  — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 28 ("Threat-model closure").
- **Requirements:** R1, R4, R6, R7, R11, R13, R14, R17, R22.
- **LOCKED decisions:** L1, L3, L4, L6, L7, L9, L11 — all confirmed, none modified.
- **Document updated (not under `spec/`):** `SECURITY-THREAT-MODEL.md` — header wording and rows #1–#6's
  `Status` column only; rows #7–#8 and all threat/mitigation text untouched.
- **Flagged, not fixed, for future tasks:**
  - `EndToEndIntegrationTest`'s Spring-context/Flyway defect (`chain.attestations` missing) — directly
    affects row #3's own integration-level citation, highest-priority candidate.
  - 4 further repository-integration-test classes with real, pre-existing failures, discovered
    incidentally, unrelated to any threat-model row.
  - Kimi Phase 11's two rejected, out-of-scope suggestions (classpath-validation tooling for citations;
    a CI/IAM smoke test for the runtime `kms:Sign` property).
  - The recurring wrong-codebase branch-poisoning issue, flagged identically in T25/T26/T27's own Phase
    12 artifacts — not re-tested this task.
