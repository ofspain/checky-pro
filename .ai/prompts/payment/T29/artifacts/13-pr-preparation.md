# payment · T29 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**). This artifact contains no code; it is documentation only.

**Branching.** As in every prior task, the work ran on the long-running `spec/service-specs-and-ai-framework` branch. `main` was never touched.

**Trailer.** The trailer names the model that did the work, following the T23–T28 precedent.

## Commit title

`payment T29: bump spec to READY FOR IMPL 0.2 with guarded verification checklist`

## Commit message

```
payment T29: bump spec to READY FOR IMPL 0.2 with guarded verification checklist

Close the last task in spec/payment-service/tasks.md. Both gates hold:
mvn -pl services/payment verify passes (535/535), and T28's threat-model
check is closed.

spec/payment-service/package.md (the sanctioned T29 exception to the
"never modify spec/" guardrail, approved at Phase 4):
- Version 0.1 -> 0.2.
- Status DRAFT -> READY FOR IMPL, with a one-line disclosure: R26 anchor
  deferred, R28 receipt read unbuilt (FU-T29-1).
- Section 9: items 2-12 ticked with typed test citations. Item 10 states
  that outbox atomicity holds by construction, with no rollback test.
  Items 1 (R28 receipt half) and 13 (cross-service unregister) are
  visibly Partial. Item 14 keeps the R26 deferral.

T01SkeletonRegressionTest gains three guards:
- The header bump: one Version row and one Status row, within the header
  table.
- Every section 9 citation is a real @Test in the named class, and every
  cited class contains tests. The R26 exemption is parsed from item 14.
- Items 2-12 stay ticked, items 1 and 13 stay Partial with FU-T29-1, and
  item 14 stays deferred.

16 mutations proven. The hardened guard caught a real wrong-class
citation, in item 6.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed

- **`spec/payment-service/package.md`:** the header and §9 only.
- **`services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`:** three tests and their helpers. The class goes from 9 tests to 12. A Javadoc that Phase 6 displaced was moved back.
- **Pipeline artifacts:** `.ai/prompts/payment/T29/artifacts/00`–`13`, including `11-test-review-resolution.md`.
- **Not changed:**
  - `services/payment/src/main`
  - the other `spec/` files
  - `contracts/`
  - `SECURITY-THREAT-MODEL.md`

## Summary

T29 formally closes the payment-service build pipeline. The bump follows crypto T29: the boxes were not all ticked. Every ticked item cites committed tests, and every gap stays visible:

| Item | Gap |
|---|---|
| 1 | The receipt half of R28 was never built. |
| 13 | The second-match half depends on crypto-service. |
| 14 | The on-chain anchor is deferred. |

Kimi asked for the checklist itself to be guarded, so the citations can't rot. Three review rounds hardened the guard from "the name appears somewhere" to "a real `@Test` in the named class". The stricter version found a real error on its first run: item 6 credited the dedupe test to the wrong class and the wrong test type.

**Human decisions:**
- **Phase 4:** approve the `spec/` edit; bump with partials visible; use `[ ]` **Partial —** rather than `[~]`; accept Kimi F2, F4 and F6; reject F5.
- **Phase 9:** accept F1–F7; cite the reconciler and job tests; reword item 10 honestly; reject F8.
- **Phase 11:** accept Gaps 1–6; document Gap 7.

**Open follow-ups (human-owned):**

| ID | Follow-up |
|---|---|
| FU-T29-1 | A merchant-scoped receipt read endpoint (R28), with its `404` cross-merchant test. |
| R26 | The on-chain anchor (task 21), which needs crypto-service chain-write. |
| — | The crypto-service `unregisterWatch` defect, which affects §9 item 13. |
| FU-T27-1 | Sibling Dockerfiles. |
| FU-T27-2 | `jwk-set-uri`. |
| G2 | Partition-key drift. |
| FU-1 | Layering. |
| FU-2 | `@PreAuthorize` sweep. |
| — | Infra: alert rules on `themistra.payment.ledger.integrity.*`, and IAM `s3:GetObject`/`s3:ListBucket`. |

## Testing performed

- **Full suite.** `mvn -o -pl services/payment verify` from the repo root:
  - Phase 6: 534/534.
  - Phases 9 and 11: **535/535, BUILD SUCCESS**.
- **Mutation proofs (16).** Each was temporary, restored and confirmed with `cmp`:

  | Phase | Mutations |
  |---|---|
  | 6 | M1 Version, M2 Status, M3 name typo |
  | 9 | M4 wrong class, M5 class rename, M6/M7 ticked partials, M8 stray header row, M9 `.method` typo (M1–M3 re-run) |
  | 11 | M10 `@Test` removed, M11 class with no tests, M12 unticked item, M13 malformed marker, M14 follow-up ID dropped, M15 deferred-name drift, M16 duplicate Version row |

  - M5's first attempt was itself flawed: the name didn't end in `Test`. It was redone with a valid rename.
  - M13 fails through the per-item check, so the marker check is reported as a backstop.
- **Checks on the guards themselves:**
  - The citation guard found 25+ citations, so it can't pass vacuously.
  - There are no `@Disabled` tests and no Surefire filters, so no cited test is skipped.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 29 (`:50`).
- **Checklist:** `package.md` header (`:6`, `:9`) and §9 (`:114`–`:127`).
- **Re-verified as evidence:** R7a, R13, R17, R18, R21–R23, R28 (partial), R30, R31. R26 is deferred.
- **Precedent:** crypto T29 (`b303d682`).
