<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T40 · Phase 12 — Specification Verification

Compares the final state (Phases 6-11) against `package.md`'s own two literal preconditions, R43,
and the frozen brief (as amended) for **T40 only** — the final task in the entire spec sequence.

---

## Traceability Matrix — Task Statement's Two Preconditions

| Precondition | Status | Evidence |
|---|---|---|
| §11 questions closed | **Honestly closed** — 4 resolved (Q1, Q2, Q5, Q6), 1 partially resolved with an explicit tracked decision (Q3, D-030), 1 explicitly out-of-scope for this service (Q4) | `package.md` §11 (`package.md:148-158`) |
| Tests pass | **Explicitly accepted with named, reproducibility-criteria'd exceptions** — 707 tests, 1 failure, 6 errors, both groups independently corroborated and, for Group A, objectively reproducible; Group B's criterion corrected to reflect genuine, empirically-confirmed flakiness rather than a false clean boundary | `package.md` §12 (`package.md:167-188`) |

## Traceability Matrix — R43 (the gap this task closed)

| Item | Status | Evidence |
|---|---|---|
| Automatic lock audited | **Fixed** | `AccountService.java:315-323`; `LockoutPersistenceIntegrationTest` (exact-count + null-actor) |
| Automatic unlock audited | **Fixed** | `AccountService.java:325-333,351-364`; same test file, two assertions |
| `resetLockout`'s unlock path audited | **Fixed** (same code path, third caller) | `LockoutPersistenceIntegrationTest.resetLockoutOnARealLockedAccountClearsTheRowAndTransitionsToActive` |
| Admin-initiated unlock still correct | **Unchanged, unified** | `AccountService.java:335-349` (`adminUnlock` now delegates to the same private method, eliminating a self-caught double-fire regression) |
| Escalating re-lock (T11 AC7) audited | **Known, accepted limitation** — not fixed | `package.md` Q5 (corrected wording), `auth-decisions.md` D-027-adjacent reasoning |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | §11 accurately reflects every question's real status, verified per-question at Phase 0/4/9/11 |
| AC2 | **Met** | §12 names both groups with verified (not assumed) reproducibility criteria |
| AC3 | **Met** | `package.md` header: `Status: READY FOR IMPL`, `Version: 0.2`, bumped only after AC1/AC2 |
| AC4 | **Met** | `AccountService.lock`/`unlock`/`adminUnlock` all audit/publish correctly, exact-count verified, idempotency verified, zero regressions across 707-test full-suite runs |

## Findings from this phase

None new. This task's own review process (Phase 7: 0 findings; Phase 8: 7 findings; Phase 11: 7
findings — 14 total) already surfaced and resolved every material gap, including:

1. **A real, self-caught regression before it ever reached review**: the first implementation of
   the R43 fix double-fired `adminUnlock`'s audit/event (since it called the now-also-firing plain
   `unlock`). Caught by directly running `AccountServiceTest`, not assumed safe from a code read —
   fixed by unifying both callers onto one private method, a cleaner design than the one first
   planned.
2. **A second real regression, caught during independent review, in the spec text itself**: Kimi's
   suggested Group B reproducibility criterion ("passes in isolation, fails only under full-suite
   load") was checked empirically before being written into `package.md` — three isolated runs
   produced three different outcomes, disproving the clean boundary Kimi assumed. Corrected to state
   the honest, messier truth.
3. **A genuine, real gap found and accurately, honestly bounded**: escalating re-locks remain
   unaudited — not silently fixed, not silently ignored, explicitly named as a known limitation with
   the reasoning for not building a new event type within this task's scope.
4. **Four genuinely valuable regression tests added** at Phase 11, directly targeting the same bug
   class (double-firing) this task's own process caught once already — closing that risk
   permanently, not just for the one instance found.
5. **One disproportionate-tooling suggestion rejected** (automated `package.md`↔`auth-decisions.md`
   traceability checking), matching this session's established reasoning from T38/T39.

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes — both of the task statement's own literal preconditions are
honestly addressed (not silently glossed over), the R43 gap this task's own investigation surfaced
is fixed for the common case and honestly bounded for the escalation edge case, and the spec's
status/version are bumped only after all of this was verified.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1-AC4 all Met, each with direct,
independently-verified evidence, including two self-caught regressions along the way that never
shipped.

**(3) Does it violate any LOCKED decision?** No LOCKED decision was scoped to this task; L11-L13
(indirectly relevant per Phase 0) were not touched or violated.

**(4) Remaining risks?**
- **Escalating re-locks remain unaudited** — a known, accepted, explicitly-documented limitation,
  not a silent gap.
- **Groups A/B remain genuinely unresolved** — Group A has no known code-level fix; Group B is
  confirmed genuinely flaky (not a clean isolation/full-suite boundary), both explicitly named with
  reproducibility guidance for whoever picks this up next.
- **Q3's API-key limit remains a deliberate no-limit decision** (D-030), not a gap — revisit trigger
  named for when it should be reconsidered.
- **`AccountService.lock`/`unlock` remain public**, relying on the L12 social contract with
  `LockoutService` as their only sanctioned caller — Kimi's own Finding 7 concluded no action is
  needed for this task; a future ArchUnit rule could tighten this if ever judged worthwhile.

**Verdict: PASS** — this is the final task in the entire `auth-service` spec sequence, and it closes
honestly: every precondition, every acceptance criterion, and every known residual is named with
direct evidence, not asserted or hidden. The spec is genuinely, defensibly `READY FOR IMPL` at
version `0.2`.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
