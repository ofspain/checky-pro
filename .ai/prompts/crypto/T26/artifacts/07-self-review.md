# crypto · T26 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`EndToEndIntegrationTest.java`, the only file this task creates)
against the frozen brief and `agents.md`. Findings only — no fixes applied here (Phase 9), per this
phase's own rule. Five findings identified.

## 1. Two unused imports: `java.util.Map`, `java.util.function.Function`

- **Issue:** Neither `Map` nor `Function` is referenced anywhere in the file's body — leftover from an
  earlier draft of the helper methods.
- **Severity:** Low (cosmetic — compiles cleanly either way).
- **Evidence:** `watch/EndToEndIntegrationTest.java:53,57`.
- **Recommendation:** Remove both imports.

## 2. `registerWatch`'s `txHashHint` parameter is declared but never used

- **Issue:** `registerWatch(String txHashHint, String expectedAmount)` accepts a `txHashHint` parameter
  that the method body never reads — the actual transaction hash each flow observes is a separate,
  independently hardcoded literal in each test method's own `tx(...)`/`scriptTx(...)` calls (e.g.
  `"0xtxhash"`, `"0xtxhash2"`). The parameter name actively suggests a connection between the
  registered watch and a specific transaction that does not exist in the real domain model (a watch is
  registered for an *address*, never a specific transaction) and does not exist in this method's own
  implementation either.
- **Severity:** Medium (misleading, not a functional defect — every call site's actual behavior is
  correct regardless of what string is passed for this parameter, since it's silently discarded).
- **Evidence:** `watch/EndToEndIntegrationTest.java:204-214`; every call site (lines 285, 335, 365, 403)
  passes a per-flow txHash-shaped string that is never actually used by the method it's passed to.
- **Recommendation:** Remove the unused parameter entirely.

## 3. `WatchAccessor` is an unnecessary inner class for a one-line operation

- **Issue:** `WatchAccessor` exists solely to wrap `entityManager.find(Watch.class, watchId)` behind a
  constructor + a `load()` method, called as `new WatchAccessor(watchId).load()` at every flow's start.
  `entityManager` is already a plain instance field this test class has direct access to — a private
  method (`private Watch loadWatch(UUID watchId) { return entityManager.find(Watch.class, watchId); }`)
  would do the identical job with less indirection, matching this codebase's own general
  anti-over-engineering stance (`agents.md`/session convention: "three similar lines is better than a
  premature abstraction").
- **Severity:** Low (style/simplification only).
- **Evidence:** `watch/EndToEndIntegrationTest.java:459-469` vs. every call site (lines 286, 336, 366,
  404).
- **Recommendation:** Replace the inner class with a single private method.

## 4. The three-`FakeChainAdapter` setup block is repeated near-identically across all four test methods

- **Issue:** Every one of the four `@Test` methods opens with the identical 7-line block: declare
  `providerA`/`providerB`/`providerC`, build the `List<ProviderSet.NamedAdapter>`, construct and start
  the `Watcher`. Only the txHash-bearing scenario logic that follows actually differs per flow.
- **Severity:** Low (duplication, not a defect — each flow legitimately needs its own fresh adapter
  instances scripted independently, so this isn't wrong, just repetitive).
- **Evidence:** `watch/EndToEndIntegrationTest.java:288-294, 338-344, 368-374, 406-412`.
- **Recommendation:** Extract a shared private helper, e.g.
  `private ScenarioSetup startNewWatcherWithThreeFakeProviders(Watch watch)` returning a small record of
  `(providerA, providerB, providerC, watcher)`, used by all four tests.

## 5. Test data has confusing/contradictory naming, though not incorrect

- **Issue:** `VALID_RECIPIENT` and `VALID_TOKEN_CONTRACT` are set to the literal same address string —
  a payment recipient and an ERC-20 contract address being identical isn't realistic and could read as
  a copy-paste mistake (it isn't; both fields are independently validated for EIP-55 checksum format
  only, so any syntactically valid address satisfies either field regardless of the other). Separately,
  `tx(...)`'s hardcoded `fromAddress`, `"0xfrom-sanctioned-or-not"`, is reused across all four flows
  even though only Flow 4 actually configures its mocked `ScreeningClient` to treat that specific
  address as sanctioned — the same literal carries two contradictory meanings ("definitely not
  sanctioned" in Flows 1-3, "definitely sanctioned" in Flow 4) depending on which test reads it.
- **Severity:** Low (clarity only — no flow's actual pass/fail behavior depends on these two
  addresses being distinct from each other, or on the literal content of `fromAddress` beyond what
  each test's own mocked `ScreeningClient` is independently configured to do with it).
- **Evidence:** `watch/EndToEndIntegrationTest.java:107-108` (`VALID_RECIPIENT`/`VALID_TOKEN_CONTRACT`),
  `216-219` (`tx(...)`'s hardcoded `fromAddress`).
- **Recommendation:** Use two distinct, clearly-different-looking addresses for recipient vs. token
  contract, and rename the `fromAddress` constant per-flow (or at minimum drop "-sanctioned-or-not"
  from the shared one) so a future reader isn't misled about what property of the address actually
  drives each flow's outcome (the mocked `ScreeningClient`'s per-test configuration, not the address
  string itself).

---

No correctness defects were found in the four flows' own logic. Specifically re-checked and confirmed
correct:
- **The `chain.tx.seen`/`chain.tx.confirmed` same-batch race** (the bug fixed during Phase 6, before
  this artifact was written) — re-verified the buffering fix is applied consistently to every
  `awaitRecordOnTopic`/`noRecordAppearsOnTopic` call site, not just Flow 1's.
- **`checkForReorg`'s pull-based re-check** (Flow 3) correctly uses `scriptTx(...)` (a pull-based
  re-script), not `simulateReorg(...)` (a push) — verified against `Watcher.checkForReorg`'s own
  documented "synchronous pull via `getTx`" contract, not assumed.
- **Flow 2's `EXISTENCE`-always-agrees reasoning** (Frozen Brief Finding #3) — re-confirmed
  `QuorumEvaluator`'s pigeonhole-principle guarantee holds regardless of which three specific Boolean
  values are compared, so `tx(true, ...)` for all three providers in Flow 2 is the only way to reach a
  genuine `AMOUNT`-only disagreement without also accidentally producing a `HELD` `EXISTENCE`.
- **`ScreeningResult` persistence in Flow 4's mocked answer** mirrors the real
  `FailClosedScreeningClient`'s own exact `create(...)` parameter order and field semantics, re-checked
  against that class's real source, not assumed from memory.
