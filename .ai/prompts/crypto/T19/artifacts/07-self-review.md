# crypto · T19 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief (`artifacts/04-frozen-task-brief.md`) and
`agents.md`. Findings only — no fixes applied here (Phase 9).

## 1. `screen(...)` logs and reads the clock before validating its own inputs

- **Issue:** `FailClosedScreeningClient.screen` calls `log.warn(...)` with `chain`/`txHash` and
  `clock.instant()` before `ScreeningResult.create(...)` performs its `Objects.requireNonNull` checks on
  `chain`/`address`. A caller passing a `null` `chain` or `null` `address` still triggers the warning log
  and a clock read before the `NullPointerException` is thrown two lines later — a partial, observable
  side effect (a misleading "stub active" log entry describing a call that never actually completed)
  precedes the failure. This codebase already established the opposite convention for an analogous
  method: `ReorgDetector.reorg` validates every argument with `Objects.requireNonNull` as the very first
  statement, before any side effect, specifically so it "fails loudly here rather than producing a
  silently-corrupt [side effect]" (T18 Phase 9, Kimi Finding #9).
- **Severity:** Medium
- **Evidence:** `screening/FailClosedScreeningClient.java:38-46`.
- **Recommendation:** Move the null-validation for `chain` and `address` to the top of `screen(...)`,
  before the log statement, mirroring `ReorgDetector.reorg`'s own established ordering.

## 2. `ScreeningClient.screen`'s Javadoc documents `txHash`'s nullability but not `chain`/`address`'s

- **Issue:** The interface's `@param` block explicitly states `txHash` may be `null`, but says nothing
  about whether `chain`/`address` may be `null` — that constraint currently exists only implicitly, via
  `FailClosedScreeningClient`'s own reliance on `ScreeningResult.create`'s `Objects.requireNonNull`. A
  future second implementation (the real vendor adapter) reading only the interface has no documented
  signal that these two parameters are non-nullable.
- **Severity:** Low
- **Evidence:** `screening/ScreeningClient.java:29-36`.
- **Recommendation:** Add an explicit `@throws NullPointerException if chain or address is null` (or
  equivalent prose) to the interface's Javadoc.

## 3. The fail-closed stub's warning log omits the one field most relevant to it — `address`

- **Issue:** `screen(...)`'s log line reports `chain` and `txHash` but not `address` — the counterparty
  address the message itself says "was not screened." An operator reading this log in production has no
  way to tell which address triggered it without cross-referencing the persisted `ScreeningResult` row.
- **Severity:** Low
- **Evidence:** `screening/FailClosedScreeningClient.java:39-40`.
- **Recommendation:** Include `address` in the log statement's parameters.

## 4. No test exercises `screen(...)` with a `null` `chain` or `null` `address`

- **Issue:** `FailClosedScreeningClientTest` covers a normal input, a `null` `txHash`, and a malformed
  address string, but nothing calls `screen(...)` with a `null` `chain` or `null` `address` to confirm
  the resulting `NullPointerException` actually surfaces (and, per Finding #1, that it surfaces only
  after the log/clock-read side effects already ran). `ScreeningResultTest` covers `ScreeningResult
  .create`'s own null-checks directly, but that does not exercise `FailClosedScreeningClient`'s own
  calling code path.
- **Severity:** Low
- **Evidence:** `screening/FailClosedScreeningClientTest.java` (no such case present).
- **Recommendation:** Add a test asserting `screen(null, "0xaddr", "0xtx")` and
  `screen("ETHEREUM", null, "0xtx")` each throw `NullPointerException`.

## 5. `ScreeningResult`'s class Javadoc mis-groups `token_allowlist` as an "audit-trail table"

- **Issue:** The Javadoc's opening paragraph lists `token_allowlist` alongside `observations` and
  `attestations` as an example of another "audit-trail table." `token_allowlist` is seeded, versioned
  configuration data, not a log of attempt outcomes — the property that actually transfers (append-only,
  `INSERT`/`SELECT`-only grant, no revision in place) is accurate, but the "audit-trail" characterization
  for that specific comparison is imprecise.
- **Severity:** Low (documentation precision only; no behavioral impact)
- **Evidence:** `screening/ScreeningResult.java:17-23`.
- **Recommendation:** Rephrase to compare only on the shared append-only/grant property, or drop
  `token_allowlist` from that specific list.

---

No correctness, thread-safety, transaction-boundary, module-boundary, idempotency, money-type, or
secret-handling defects were found. Schema fidelity was checked directly against `V1__chain_baseline.sql`'s
literal column definitions (`chain VARCHAR(32)`, `address VARCHAR(128)`, `tx_hash VARCHAR(128)`,
`outcome VARCHAR(16)`, `provider VARCHAR(64)`) and each entity `@Column(length = ...)` matches exactly.
