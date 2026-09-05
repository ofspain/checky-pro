<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T28 · Phase 9 — Review Resolution

**Human Approval gate. Decided by femi, 2026-08-16.** Consumes `artifacts/07-self-review.md`, which carries both the self-review (Finding 1) and the independent review's findings (2–4, appended to the same artifact in this session rather than a separate `08-independent-review.md`).

---

## Resolution Log

### 1. `revokeAll`'s per-family step isn't atomic between authorization removal and marking revoked
*(Self-review Finding 1, Low)*

**ACCEPTED AS A DOCUMENTED RESIDUAL. No code change.** Already reasoned through at Phase 7: this is the safe-failure direction of the tradeoff (a session could briefly look "active" while actually already dead, never the reverse — never "looks revoked but still works"). Fixing it fully would mean reintroducing per-iteration transaction management, exactly the complexity D3's best-effort design was chosen to avoid.

### 2. `ReuseDetectingAuthorizationServiceTest` had a pre-existing `UnnecessaryStubbingException`
*(Independent review Finding 2, Low, test-only)*

**ACCEPTED — already applied.** A real, pre-existing defect in this test file (predates T28 entirely; the file traces back to the original JWT/reuse-detection work), surfaced because reviewing T28 required running the full suite this test belongs to. Fixed by marking the `getPrincipalName()` stub `lenient()` in the shared `authorizationWithRefreshToken` helper, since the issuance-path test needs it but the rotation-path test doesn't. Re-verified this session: `ReuseDetectingAuthorizationServiceTest` now passes 8/8.

### 3. Full suite unexecuted — Docker unavailable
*(Independent review Finding 3, Medium, procedural)*

**Acknowledged, no action possible this session.** Same standing environment limitation as every task since T25.

### 4. Claimed missing `AccountControllerIntegrationTest`, "referenced in an earlier test-selector invocation"
*(Independent review Finding 4, Low)*

**REJECTED.** Verified by searching the entire repository: no reference to `AccountControllerIntegrationTest` exists anywhere except inside this finding's own text — the claimed "earlier test-selector invocation" does not check out. Separately, the underlying observation (no T28 integration test exists yet) is simply the normal, expected state before Phase 10 runs — true for every task in this pipeline so far, not a T28-specific gap. femi's decision: reject outright rather than treat it as a naming hint for Phase 10's forthcoming integration test file.

---

## Build Verification (post-resolution)

`mvn -q -pl services/auth -am test-compile` — clean, exit 0.

`ReuseDetectingAuthorizationServiceTest` (the one test file touched this phase): 8/8 pass, confirmed this session.

No other Docker-independent test affected — `SessionService`/`SessionExceptionHandler`/`AccountController` themselves are unchanged by this phase's resolution (Findings 1, 3, 4 required no code; Finding 2's fix was scoped entirely to the pre-existing test file).

---

## Files Touched This Phase

- `token/ReuseDetectingAuthorizationServiceTest.java` — one stub marked `lenient()` (applied prior to this resolution log, during the independent review itself; recorded here for completeness).

No other file changed. No file under `spec/` touched. No public API, class name, or method signature changed.

---

**Phase 9 complete — human sign-off given.** Proceed to Phase 10 (Test Generation).
