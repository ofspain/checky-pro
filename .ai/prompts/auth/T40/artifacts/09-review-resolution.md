<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T40 · Phase 9 — Review Resolution

**Human decision:** approve — accept 6, reword-only 1 (the genuine judgment call).

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Escalating re-lock (T11 AC7) not audited — Q5's "every real state transition" overclaims | **Verified accurate**, confirmed against `LockoutStateMachine.applyFailure` (a re-lock while already `LOCKED` still returns `AccountStatusChange.LOCK`, but `AccountService.lock`'s `if status==ACTIVE` guard skips it). **Human-gate decision: reword Q5 only** (`"every real state transition"` → `"every Account.status transition"`), with the limitation explicitly named. Building a new `user.lock-extended` event type is logged as a future option, not built now — genuinely new feature design work beyond a status-bump task's proportionate scope. |
| 2 | `resetLockout`'s new audit/event behavior untested | **Accepted.** Added an `auditService.list(...)` assertion to the existing `resetLockoutOnARealLockedAccountClearsTheRowAndTransitionsToActive` test; re-run confirms 8/8 still pass. |
| 3 | Q3's deferral should be a tracked decision, not an informal note | **Accepted.** Added `auth-decisions.md` D-030, cited from Q3. |
| 4 | Q4 should document what the payload guarantees vs. what Notification Service must source | **Accepted.** Q4 reworded to explicitly enumerate the four guaranteed fields and note token-TTL sourcing is also the Notification Service's responsibility. |
| 5 | §12's Groups A/B exceptions lack objective reproducibility criteria | **Accepted, and corrected during verification** — a real catch of my own. Kimi's suggested Group B criterion ("passes in isolation, fails only under full-suite load") was checked empirically before being written into the spec: running the pair in isolation three times produced a null-response failure, a *different* audit-row-count failure, and a clean pass — proving the clean isolation/full-suite boundary Kimi assumed does **not** hold. §12 corrected to state the honest, messier truth (genuine timing-dependent flakiness, two documented symptom shapes, no clean reproducibility line) rather than ship an inaccurate criterion. Group A's criterion was verified accurate as originally stated. |
| 6 | `Implementer: TBD` next to `Status: READY FOR IMPL` reads inconsistently | **Accepted.** Added a clarifying note directly under the header table. |
| 7 | `lock`/`unlock` remain public, relying on the L12 social contract | **Accepted — no action**, matching Kimi's own conclusion ("no action required for T40"). |

## Verification after applying fixes

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=LockoutPersistenceIntegrationTest` — 8/8 pass, including the
  new `resetLockout` audit assertion.
- **Empirically verified, not assumed**: Kimi's Finding 5 recommendation for Group B's
  reproducibility criterion, before accepting it into the spec — found it factually wrong via three
  real isolated runs, corrected accordingly. This is the second time this session an accepted-in-
  principle Kimi recommendation's specific claim needed independent verification before being
  trusted (cf. T39's `SecurityChainsConfig` citation).
- `mvn -pl services/auth verify` (full suite): 705 tests, 1 failure, 6 errors — unchanged from
  Phase 6, zero regressions from this phase's additional edits.

---

**Phase 9 complete — review resolved, human-approved.** Proceed to Phase 10 (Test Generation) on
approval.
