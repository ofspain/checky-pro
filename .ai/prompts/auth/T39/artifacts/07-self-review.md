<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T39 · Phase 7 — Self Review

## Findings

### Finding 1 — D-026's original "Alternatives" line overstated a numeric trade-off study that didn't happen

**Issue.** The first draft of D-026 said "Alternatives considered (task #31): thresholds tight
enough to meaningfully slow credential guessing without generating false-positive lockouts" — phrased
as if several concrete numeric alternatives (e.g., 5 vs. 10 vs. 20 login attempts/minute) were
weighed against each other. Checking T31's own Phase 3 design-challenge artifact shows the specific
values (10/5/30) were the Phase 1/2 proposal itself, confirmed via human gate per `package.md` §11
Q2's own "confirm or replace the placeholders" framing — not selected after comparing several
concrete numeric options. Every other entry in this document that has an "Alternatives" section
(18 of 25) names genuinely distinct options that were actually weighed; this draft's version didn't
meet that bar.

**Severity.** Low — the selected values themselves were correct and already verified against source;
this was a documentation-accuracy issue, not a factual error about the outcome.

**Evidence.** `.ai/prompts/auth/T31/artifacts/03-design-challenge.md:73-75` (the actual threshold
figures originate from the Phase 2 TIB's own proposal, not a comparative study).

**Recommendation.** Fixed directly (this phase): reworded D-026's Alternatives line to accurately
describe what happened — a direct proposal, explicitly flagged for confirmation per Q2, confirmed
via human gate — rather than implying a trade-off study that this session has no record of.

## Checked and cleared (no finding)

- **D-027's central claim** (`deviceLabel` always `null` in production) re-verified directly against
  `ReuseDetectingAuthorizationService.java:95` — accurate.
- **D-026's MFA-folded-into-login-bucket claim** — re-verified against `RateLimitFilter.java`'s own
  Javadoc and cross-checked against T31's design-challenge history, which confirms this was a real,
  deliberate Phase 4 decision (not a silent reinterpretation) at the time it was made.
- **D-028/D-029's citations** — all line numbers and code references re-confirmed against current
  source, not carried forward unverified.
- **Format consistency** — all four new entries follow the document's existing structure; D-027's
  deliberately different shape (no "Selected"/"Trade-offs" split, since it records a non-decision) is
  explicitly justified in its own text, not silently inconsistent.
- **Scope discipline** — no entry drifts into the broader tasks-#17-#38 retrospective the Phase 4
  gate explicitly excluded.

---

**Phase 7 complete — self review written, one finding fixed.** Proceed to Phase 8 (Independent
Review) on approval.
