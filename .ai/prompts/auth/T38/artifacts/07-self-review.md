<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T38 · Phase 7 — Self Review

Zero code diff to review — the standard correctness/boundary/null-safety/thread-safety checklist
doesn't apply to a read-only verification task. This self-review instead critically examines the
verification *methodology* itself for gaps a later phase might otherwise have to catch.

## Findings

### Finding 1 — AC1's comment/source scan is a heuristic, not a proof of absence

**Issue.** The regex-based scan for credential-shaped literals (`password\s*=\s*["']...`, etc.)
only catches secrets assigned to a variable whose *name* contains "password"/"secret"/"key". A
credential hardcoded under an unrelated variable name (e.g., `String dbPass = "realSecret123"`)
would not match. This is the same class of limitation a proper entropy-based scanner (gitleaks)
exists to close — which is exactly why the gap-analysis names gitleaks, not a hand-written regex, as
the durable defense (AC1's own open note).

**Severity.** Low — a broader heuristic (scanning for any suspiciously long base64/hex-shaped string
literal, regardless of variable name) was additionally run during this self-review and found only
two matches, both clearly non-secret character-set constants (`TotpGenerator.BASE32_ALPHABET`,
`ApiKeyService.ALPHANUMERIC`) — not a credential. This corroborates, but does not prove, the
name-pattern scan's conclusion.

**Recommendation.** No action within T38's own scope — this is exactly why AC1's evidence already
carries an explicit "gitleaks CI gate is outside this task's scope to confirm" caveat rather than
claiming certainty the regex scan alone can't provide. Worth remembering as a general lesson: a
grep-based secret scan is directional evidence, not a substitute for an entropy-based tool.

## Checked and cleared (no finding)

- **AC2's evidence is grounded in this codebase's real controller inventory**, not a guessed prefix
  — all five existing admin controllers map under `/admin/**` uniformly (confirmed via the T33
  endpoint inventory earlier this session), so checking specifically for `/admin` is appropriately
  scoped, not an arbitrary or incomplete guess.
- **AC3's real evidence is the Phase 0 full dependency inventory** (every `groupId`/`artifactId`
  pair visually inspected), not just the narrower substring grep restated at Phase 6 — the grep is a
  quick re-confirmation, not the primary evidence; no gap here.
- **AC4/AC5** are straightforward negative greps against specific, unambiguous API names
  (`Long.getLong`, `allow-circular-references`, etc.) with no realistic false-negative shape to
  worry about — these are complete, not heuristic.

---

**Phase 7 complete — self review written, one methodology caveat noted (already reflected in AC1's
existing open note, not a new gap).** Proceed to Phase 8 (Independent Review) on approval.
