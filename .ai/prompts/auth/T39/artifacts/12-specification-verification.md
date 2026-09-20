<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T39 · Phase 12 — Specification Verification

Compares the final state (Phases 6-11) against `package.md` §11, `design.md` §4b, and the frozen
brief (as amended) for **T39 only**. `spec/auth-service/` confirmed unchanged throughout — this task
never edits `design.md`/`package.md`, only `auth-decisions.md`.

---

## Traceability Matrix — Named Items

| Item | Status | Evidence (file:line) | Deviation? |
|---|---|---|---|
| Q1 (`package.md` §11) | Already resolved, confirmed only | D-025 (`auth-decisions.md:212-219`), pre-existing | No |
| O2 (`design.md` §4b) | Resolved, newly recorded | D-026 (`auth-decisions.md:221-252`) — thresholds, MFA-bucket folding, `refresh_token`-grant scoping, per-token keying (all corrected at Phase 9) | No |
| O3 (`design.md` §4b) | **Confirmed still open**, honestly recorded as such | D-027 (`auth-decisions.md:254-274`) — cross-referenced back to `design.md` §4b O3 (Phase 11) | **Deliberate**: recorded as a non-decision, not fabricated as resolved — the Phase 4 human-gate choice |
| O4 (`design.md` §4b) | Resolved, newly recorded | D-028 (`auth-decisions.md:276-292`) — default form login, `SecurityChainsConfig` citation corrected at Phase 9 | No |
| O5 (`design.md` §4b) | Resolved, newly recorded | D-029 (`auth-decisions.md:294-301`) — SHA-256, matches spec's suggested default | No |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | Q1/D-025 confirmed to need no edit |
| AC2 | **Met** | D-026, with two Phase-9 accuracy corrections (grant-type scoping, per-token keying) |
| AC3 | **Met** | D-027, honestly recorded as unresolved per the Phase 4 human-gate decision |
| AC4 | **Met** | D-028, with a Phase-9 citation correction (`SecurityChainsConfig`'s actual `.formLogin(...)` shape, not Kimi's initially-guessed one) |
| AC5 | **Met** | D-029 |

## Findings from this phase

None new. This task's own review process (Phase 7: 1 finding, self-fixed; Phase 8: 8 findings;
Phase 11: 6 findings — 15 total) already surfaced and resolved every material gap, including:

1. **A self-caught documentation-accuracy issue**: D-026's first draft implied a numeric
   trade-off study for the rate-limit thresholds that, per T31's own history, never happened —
   corrected before Kimi's own review ever saw it.
2. **Two genuine factual corrections from independent review**, both verified against source before
   accepting: the `oauth-token-per-minute` limit only applies to the `refresh_token` grant, not
   every `/oauth2/token` request; the password-reset and refresh-token buckets are keyed per-token
   (SHA-256 hash), not per-account.
3. **One incorrect citation caught and fixed, not blindly copied**: Kimi's suggested
   `SecurityChainsConfig` citation (`.formLogin(Customizer.withDefaults())`) didn't match the actual
   code; the real, verified configuration was cited instead.
4. **Two genuine human-gate decisions**, both resolved explicitly rather than assumed: O3 recorded
   honestly as still-open rather than retroactively fabricated as resolved; T39's own scope bounded
   to the four named O-items rather than expanded into a full tasks-#17-#38 retrospective.
5. **Several Kimi Phase 11 suggestions rejected as disproportionate tooling investment** for a
   documentation-only task (stale-citation CI guards, O-item traceability parsers, numbering-
   contiguity checks) — each individually reasoned, not reflexively dismissed.

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. Q1 confirmed already resolved; O2, O4, O5 newly resolved and
recorded with corrected, source-verified evidence; O3 honestly recorded as still open, which is the
*correct* complete answer for an item that was never actually decided.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1-AC5 all Met.

**(3) Does it violate any LOCKED decision?** No LOCKED decision was scoped to this task; none was
touched.

**(4) Remaining risks?**
- **O3 remains a genuine, unresolved gap** in production behavior (session device labels are always
  `null`) — not a risk this task introduced or was scoped to fix, but worth surfacing again here: a
  future task should pick this up.
- **Line-number citations in D-026 through D-029 will drift** as the cited source files are edited —
  an accepted, already-present convention throughout the document, not a new risk.
- **The broader tasks-#17-#38 decision retrospective remains undone** — a deliberate, Phase-4-gated
  scope boundary, not an oversight.

**Verdict: PASS** — Q1 and O2/O4/O5 are resolved and accurately recorded, each with source-verified
evidence corrected through two rounds of adversarial review; O3 is honestly recorded as unresolved
rather than misrepresented; every scope boundary (the broader retrospective, `design.md` itself not
being edited) is explicit, not silent.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
