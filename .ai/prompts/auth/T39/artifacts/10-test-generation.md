<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T39 · Phase 10 — Test Generation

No tests — documentation-only task, no code or runtime surface to test.

## Deliverable manifest

| Entry | Resolves | Content |
|---|---|---|
| D-026 | O2 | Rate-limit thresholds (10/5/30 per minute); MFA folded into `/login` bucket; `oauth-token-per-minute` scoped to `refresh_token` grant only; password-reset/refresh buckets keyed per-token, not per-account (all corrected/verified at Phase 9) |
| D-027 | O3 (recorded unresolved) | Device-label source never decided; `deviceLabel` is `null` by omission in production |
| D-028 | O4 | Default Spring Security login form, no custom template; `TotpAuthenticationProvider`'s `mfaCode` field explains why none was needed |
| D-029 | O5 | SHA-256 recovery-code hashing, matching the spec's suggested default |

Q1 confirmed to need no new entry (already D-025).

## Verification performed

- All four entries' citations independently re-verified against current source at Phases 6, 7, and
  9 (not carried forward unverified between phases).
- Full `auth-decisions.md` re-read after every edit to confirm formatting consistency with the
  existing 25 entries.
- No production/test code touched — nothing to compile or run.

---

**Phase 10 complete — test manifest written (no tests, documentation-only task).** Proceed to
Phase 11 (Kimi Test Review) on approval.
