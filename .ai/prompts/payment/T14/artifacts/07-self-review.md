# payment · T14 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Reviewed the Phase 6 diff against the frozen brief and `agents.md`. Findings only — no fixes
applied here. Re-ran the test 3 more times during this review (6 consecutive passes total
across Phases 6/7) specifically checking for flakiness, since this is genuinely new
infrastructure in this codebase.

## Findings

### 1. An unnecessary `seedInvoice()` call in AC2's own scenario (a)

- **Issue:** `shouldIgnoreOutOfOrderOrStaleChainEvents`'s own "finalized for an unknown payment"
  scenario calls `seedInvoice(unknownWatchId)` and discards the result, but `applyFinalized`'s
  own "no known payment" branch is reached by correlating `(chain, txHash)` — it never resolves
  an invoice at all in this code path (confirmed: `ChainEventConsumer.onChainTxFinalized` calls
  `findInvoiceIdByChainAndTxHash` first, which fails before any invoice lookup is ever
  attempted). The seeded invoice is dead weight.
- **Severity:** Low (no correctness impact — the scenario still correctly proves what it claims;
  just an unnecessary setup call).
- **Evidence:** `ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`, scenario (a) of
  `shouldIgnoreOutOfOrderOrStaleChainEvents`.
- **Recommendation:** Remove the unused `seedInvoice` call and the unused `unknownInvoiceUuid`/
  `unknownWatchId` beyond what the event JSON itself needs (a bare `UUID.randomUUID()` for the
  event's own `watchId` field is sufficient — no invoice needs to exist).

### 2. `shouldIgnoreOutOfOrderOrStaleChainEvents` bundles three independent scenarios into one method

- **Issue:** A failure in scenario (a) would stop the method before scenarios (b)/(c) ever run,
  reducing diagnostic granularity (a single red test doesn't say which of the three staleness
  paths actually broke). This is a real, if minor, testing-practice tension — but splitting into
  three separate `@Test` methods would mean none of them carries the exact name
  `shouldIgnoreOutOfOrderOrStaleChainEvents` that `package.md` §8 maps to R14 (unless one keeps
  that name and the other two are named descriptively, e.g. as siblings).
- **Severity:** Low/Medium (a real trade-off between diagnostic independence and the single
  named-test mapping, not a bug).
- **Evidence:** `ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`,
  `shouldIgnoreOutOfOrderOrStaleChainEvents`'s own three-scenario body.
- **Recommendation:** Worth a genuine Phase 8 assessment on whether to split (one method keeps
  the named-test mapping; two new sibling methods each get an independent pass/fail signal) —
  not pre-decided here, since both the current shape and the split both have a real, legitimate
  rationale.

## Positive confirmations

- All three tests pass, confirmed stable across 6 consecutive runs (3 in Phase 6, 3 more here).
- No `Thread.sleep` anywhere — `Awaitility` exclusively, with `pollDelay` correctly used for
  every negative assertion.
- Every test uses its own freshly-generated `UUID`s/`txHash`es — no shared mutable state or
  cross-test-method ordering dependency.
- Raw JDBC string concatenation is safe here specifically — every interpolated value is an
  internally-generated `UUID`, never external input, consistent with every other integration
  test in this codebase using the identical pattern.
- The Phase 6 production-config fix (cron format) was independently re-verified: the full suite
  (295/295) still passes, and the two existing tests that reference these properties
  (`ConfigPropertiesBindingTest`, `InvoiceExpirySweepJobTest`) were confirmed to test the
  binding mechanism and the annotation's own placeholder text respectively, neither of which
  depends on the real file's own resolved value — correctly unaffected by the fix.
- `@EntityScan`/`@MockBean JwtDecoder` are both correctly scoped, minimal fixes for real
  context-wiring problems, not scope creep.
