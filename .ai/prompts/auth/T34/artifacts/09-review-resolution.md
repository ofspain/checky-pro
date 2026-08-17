<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T34 · Phase 9 — Review Resolution

Consumes `artifacts/07-self-review.md` (2 findings) and `artifacts/08-independent-review.md` (Kimi,
4 findings — 2 of which independently confirm my own self-review findings verbatim). All findings
verified against actual source/evidence before disposition. All four accepted directly — none
carried genuine trade-off weight requiring a human gate; every one was either a confirmed factual
error or a low-risk documentation enhancement.

## Comment resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| Self-review Finding 1 / Kimi Finding 1 | Path 1's `email_verified` "in practice always true" claim is factually wrong — contradicted by the Phase 5 real-token evidence | **ACCEPTED.** | Rewrote the `email_verified` row: states the rule (`true` iff `email` scope authorized for that specific request) with no claim about typical behavior; cites the real token showing `email_verified=false` as direct evidence. |
| Self-review Finding 2 / Kimi Finding 2 | `aud`'s wire shape (bare string vs. JSON array) undocumented despite the same rigor applied to `scope` | **ACCEPTED.** | Added the wire-shape note to `aud` in the top-level glossary and to each of the three per-path tables. **While fixing this, empirically verified Path 3's `aud` shape too** (not previously checked) — found the doc's original `["checky-api-key"]` claim was *also* wrong: encoded a real JWT through the same `NimbusJwtEncoder` `ApiKeyTokenIssuer` uses and got `"aud":"checky-api-key"`, a bare string, exactly like Paths 1-2. Corrected all three paths' `aud` rows to state the bare-string shape, with Path 3's own verification method noted. |
| Kimi Finding 3 | Top-level 13-claim table doesn't show per-path presence, easy to misread | **ACCEPTED.** | Added Path 1/2/3 presence columns (✓/—) directly to the top-level table. |
| Kimi Finding 4 | No CI-enforced sync check between doc and code | **ACCEPTED, documentation-only** (matches Kimi's own "acceptable for the task's literal scope" framing — no test added). | Strengthened the existing Verification section's closing note to explicitly name the absence of an automated check and suggest a future lightweight test, rather than only implying it. |

## Summary

One file changed: `contracts/api/token-claims.md`. All four review findings accepted and applied.
Notably, **fixing Finding 2 surfaced a second, previously-unverified factual error** (Path 3's
`aud` shape) that neither my own self-review nor Kimi's independent review had caught — both had
only verified `aud`'s shape for Paths 1-2 via the Phase 5 probe, and assumed (rather than verified)
that Path 3's manually-constructed `List.of(CLIENT_ID)` would serialize differently. It doesn't:
the same `NimbusJwtEncoder` collapses it identically. This was caught only by re-verifying
empirically while making an unrelated fix, not by inspection — consistent with this whole session's
repeated finding that assumptions about "this probably behaves the same elsewhere" are worth a
quick real check rather than trusting the pattern to hold.

No production code changed (none was ever in scope). `git status` confirms only the one authorized
file is modified.

---

**Phase 9 complete — resolution log written; sign-off recorded.** Proceed to Phase 10 (Test
Generation) on approval.
