<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T34 · Phase 10 — Test Generation

No tests generated. This is a genuinely doc-only task — unlike T27/T32/T33's "test-only" tasks
(where the deliverable was test code, mapped in this same phase), T34's own frozen brief has no
test at all, planned or existing: no named test (`package.md` §8), and Phase 2/4 explicitly decided
against adding a new one (Kimi Phase 8 Finding 4's disposition, Phase 9: "documentation-only,"
matching Kimi's own "acceptable for the task's literal scope" framing).

## Why no test, and what substitutes for one

`contracts/api/token-claims.md`'s own "Verification" section (final paragraph) is the closest
equivalent this task produces: it names the three ground-truth source files
(`TokenClaimsCustomizer.java`, `ApiKeyTokenIssuer.java`, Spring Authorization Server's
`JwtGenerator.java`) and explicitly states that a future change to any of them should update the
doc in the same change — and explicitly flags that no automated check enforces this today,
suggesting (not requiring) a future lightweight test if that gap is ever worth closing.

**What was actually verified, without shipping as a test:**
- Every claim's presence/absence per path was read directly from source (Phase 0/3/5).
- Path 1's complete claim set was confirmed against a real, running `SasLoginIntegrationTest`
  token (Phase 5) — added as a temporary probe, run once, reverted; not left behind as a
  permanent test since the frozen brief didn't authorize one.
- `scope`'s JSON-array wire shape (Phase 3, correcting Kimi's own wrong hypothesis) and `aud`'s
  bare-string wire shape for all three paths (Phase 7/9, the second catching Path 3's own
  previously-unverified, incorrectly-documented shape) were each confirmed by encoding a real JWT
  through the exact `NimbusJwtEncoder` mechanism every path uses — also temporary probes, run once
  each, reverted.

None of these probes are shipped as permanent tests; the frozen brief's own scope (doc-only, no
test required) was followed. `git status` confirms no test file exists in the final diff for this
task.

## Verification performed

- `git status` — only `contracts/api/token-claims.md` present in this task's diff; no leftover
  scratch/probe files from any of the four temporary verification runs across Phases 5, 7, and 9.
- No `mvn` build step applies (no code was written or changed).

---

**Phase 10 complete — test manifest written (no tests, by design).** Proceed to Phase 11 (Kimi test
review) on approval.
