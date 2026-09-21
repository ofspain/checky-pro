# crypto · T29 · Phase 6 — Implementation Notes

## Files modified

- `spec/crypto-service/package.md` — header (`Version` `0.1`→`0.2`, `Status` `DRAFT`→`READY FOR IMPL`),
  §9 (all 14 items ticked with typed evidence citations, item 13 left honestly partial), §11 (Q1, Q2,
  Q3, Q7 each given a `**Resolved**`/`**Open follow-up**` note, mirroring Q8's own precedent style).
- `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java` — one new
  regression-guard method, `packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo`.

## What ran, in order

1. Applied all four `package.md` edits exactly as pinned in Phase 5.
2. Added the new `T01SkeletonRegressionTest` method and its `CRYPTO_PACKAGE_SPEC` path constant.
3. `mvn -pl services/crypto -am test -Dtest=T01SkeletonRegressionTest` — 7/7 passing (6 existing + 1
   new).
4. `mvn -pl services/crypto -am verify` (full module) — **760 tests, 6 failures, 4 errors** — one more
   test than T28's own final count (the new regression guard), same failures/errors as T28 Phase 12's
   own disclosed, pre-existing, unrelated set (`TokenAllowlistRepositoryIntegrationTest`,
   `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
   `ObservationRepositoryIntegrationTest`, `EndToEndIntegrationTest`). No regression introduced by this
   task's own edits.
5. Re-read `package.md` in full to confirm no line outside the header/§9/§11 scope was touched.

## Disposition summary

| Section | Result |
|---|---|
| Header | `Version: 0.2`, `Status: READY FOR IMPL` |
| §9 (14 items) | 13 ticked `[x]` with typed evidence; item 13 (`mvn verify` / Docker build) left `[ ]`, honestly disclosing the partial pass and the genuine, newly-discovered Docker build failure |
| §11 Q1/Q2/Q3/Q7 | Each resolved (engineering half) with an explicit, cited open follow-up where a real vendor/procurement/config decision remains |
| New regression guard | `T01SkeletonRegressionTest.packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo` — 7/7 passing |
| Full suite | 760 tests, 6 failures, 4 errors — unchanged from T28's own disclosed state, no new regressions |
