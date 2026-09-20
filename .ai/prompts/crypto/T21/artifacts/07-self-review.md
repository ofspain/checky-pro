# crypto · T21 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief (`artifacts/04-frozen-task-brief.md`) and
`agents.md`. Findings only — no fixes applied here (Phase 9), per this phase's own rule; none of these
findings are build-breaking or load-bearing in the way T20's Phase 7 `@Autowired` omission was, so none
were fixed inline.

## 1. `AttestationRefusedException` is `public`, inconsistent with this codebase's own established
   convention for a module-internal exception

- **Issue:** `watch.WatchNotFoundException` and `watch.InvalidWatchRequestException` — the direct
  precedent this task's own exception/handler pair mirrors — are both package-private
  (`class WatchNotFoundException extends RuntimeException`, no `public` modifier). `attest
  .AttestationRefusedException` is declared `public`, even though it is only ever thrown by
  `AttestationService` and caught by `AttestExceptionHandler`, both in the same `attest` package.
- **Severity:** Low
- **Evidence:** `attest/AttestationRefusedException.java:15`; contrast with
  `watch/WatchNotFoundException.java:9` and `watch/InvalidWatchRequestException.java:9`.
- **Recommendation:** Narrow to package-private, matching the established convention exactly.

## 2. The "persist a `REFUSED` row, then throw" pattern is duplicated three times

- **Issue:** `attest(...)` and `requireAllFactsAgreed(...)` each independently call
  `persist(chain, txHash, receiptDigest, AttestOutcome.REFUSED, null, null)` immediately followed by
  `throw new AttestationRefusedException(chain, txHash)`, three separate times (missing fact, empty
  cursor list, screening `ERROR`/exception). A future edit to one call site (e.g., changing what gets
  persisted, or adding a step before the throw) could easily update two of the three and miss the third.
- **Severity:** Low
- **Evidence:** `attest/AttestationService.java:81-82, 90-91, 97-99, 114-115` — four near-identical
  two-line sequences (one of the four, line 111-118, is actually inside a loop, but the pattern at each
  iteration is identical to the other three call sites).
- **Recommendation:** Extract a private `refuse(String chain, String txHash, String receiptDigest)`
  method that persists `REFUSED` and throws, called from all three (four, counting the loop) sites.

## 3. `requireAllFactsAgreed` issues up to four sequential database round-trips

- **Issue:** Each of the four required fact types is checked via its own
  `quorumDecisionService.isAgreed(...)` call, i.e. up to four separate `SELECT`s per attest request in
  the worst case (finality fails last). The frozen brief's own Performance constraint didn't call for
  batching, and this matches the proportionate-effort bar every other task in this package has held to,
  but it's a real, measurable cost if `/attest` ever becomes latency-sensitive.
- **Severity:** Low (informational — not required by the frozen brief)
- **Evidence:** `attest/AttestationService.java:111-118`; `quorum/QuorumDecisionService.java`'s
  `isAgreed` has no batched/multi-fact-type variant.
- **Recommendation:** Not required now. If this ever matters operationally, a single query
  (`findByChainAndTxHashAndFactTypeIn` or similar) would replace the loop.

## 4. Confirmed, not a defect: a KMS/infrastructure failure's `500` body is already safe

- **Observation:** Verified directly that `common.ApiExceptionHandler`'s catch-all
  (`@ExceptionHandler(Exception.class)`) already returns a generic `"An unexpected error occurred..."`
  detail plus a `trace_id`, never the raw exception message — so `KmsSigner.sign(...)`'s uncaught
  exceptions (Finding #10/L-T21b's deliberate `500` path) can never leak KMS-internal error text to a
  caller, even though `AttestationService` itself does nothing special to guard against that. This
  confirms the frozen brief's own disposition holds all the way through to the actual HTTP response, not
  just as an unverified assertion in the brief.
- **Evidence:** `common/ApiExceptionHandler.java:81-91`.
- No action needed — recorded as a verified, positive confirmation.

## 5. Significant, easily-misdiagnosed consequence: every real `/attest` call currently resolves to
   `REFUSED`, by design

- **Observation:** The only `ScreeningClient` implementation that exists today,
  `screening.FailClosedScreeningClient` (T19), unconditionally returns `ERROR` for every call — it never
  returns `CLEARED` or `BLOCKED` (no real vendor is wired yet, Q2 unanswered). Since this task's own
  gate sequence converts any screening `ERROR` into `AttestationRefusedException` (`409`), **every
  attest request that reaches the screening step in the current, real, deployed system will resolve to
  `REFUSED`** — `200 SIGNED` and `200 BLOCKED` are both currently unreachable outcomes outside of a test
  double. This is not a defect - it is the correct, intended consequence of composing this task's fail-
  closed design with T19's own fail-closed stub (L12) - but it is exactly the kind of behavior someone
  smoke-testing `/attest` in a dev/staging environment could easily mistake for a bug in this task's own
  logic, rather than an already-known, already-approved upstream limitation.
- **Severity:** Medium (for visibility, not correctness — no code change follows from this finding)
- **Evidence:** `screening/FailClosedScreeningClient.java` (`screen(...)` unconditionally returns
  `ScreeningOutcome.ERROR`); `attest/AttestationService.java:97-100`.
- **Recommendation:** No code change. Worth a one-line callout in the Phase 13 PR description so a
  reviewer or an on-call engineer doesn't lose time re-diagnosing already-known, already-disclosed
  behavior.

---

No thread-safety, null-handling, or transaction-boundary defects were found beyond what Phase 3/8 (not
yet run) might surface. Module-boundary correctness (both new repository methods stay package-private,
cross-module reads go only through the two services) was verified by the code compiling and by AC7's own
test coverage.
