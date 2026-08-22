<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T39 · Phase 9 — Review Resolution

**Human decision:** approve — accept 4, no-action-accept 3 (confirmations/already-fine), light-touch
accept 1.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | D-026's Alternatives section reads as process notes, not distinct options | **Accepted as already-addressed at Phase 7, no further restructure.** Several existing entries (D-018/D-019/D-021) similarly lack a formal multi-option list when there genuinely wasn't one to weigh — the honest "no rich trade-off study, here's what actually happened" framing (already applied at self-review) matches that established sub-pattern. Kimi's own confidence was Low. |
| 2 | D-026 imprecise: `oauth-token-per-minute` only applies to the `refresh_token` grant | **Accepted, verified.** Confirmed `RateLimitFilter.isOAuthTokenRefreshRequest` checks `grant_type=refresh_token` explicitly; `authorization_code` requests are unlimited by this filter. D-026's Selected line corrected to state this precisely. |
| 3 | D-026 omits that password-reset/refresh-token limits are keyed per-token, not per-account | **Accepted, verified.** Confirmed via `RateLimitFilter.java`'s own Javadoc (lines 32-44): both buckets are keyed by the SHA-256 hash of the submitted token. Added to D-026's Trade-offs, including the original T31 Kimi-finding/gate-decision provenance. |
| 4 | Line-number citations will go stale as code evolves | **Accepted — no action**, matching Kimi's own recommendation ("no immediate change required... general documentation-hygiene improvement, not a T39 blocker") and the document's own pre-existing, unchanged convention. |
| 5 | D-028 could cite `SecurityChainsConfig` directly | **Accepted, with a correction.** Kimi's suggested citation (`.formLogin(Customizer.withDefaults())`) doesn't match the actual code; the real configuration is `.formLogin(form -> form.authenticationDetailsSource(...).failureHandler(...).successHandler(...))` with no `.loginPage(...)` override — which is what actually determines the default-page behavior. Cited accurately in D-028's Impact. |
| 6 | D-027's non-decision shape is well-justified | **Accepted — confirmation, no change.** Kimi's suggestion to add a general note about non-decisions at the document's top is a reasonable future idea but not required for this task's own scope. |
| 7 | D-029's citation and rationale are accurate | **Accepted — confirmation, no change.** |
| 8 | No broader retrospective entries added, scope discipline maintained | **Accepted — confirmation, no change.** Matches the Phase 4 gate decision exactly. |

## Verification after applying fixes

- Re-read the full `auth-decisions.md` after all Phase 9 edits — D-026's threshold precision and
  per-token-keying additions, D-028's `SecurityChainsConfig` citation — all consistent with the
  document's existing formatting and with current source.
- All corrected claims (`grant_type=refresh_token` scoping, per-token keying, the exact
  `.formLogin(...)` configuration) independently re-verified against source at this phase, not
  carried forward from Kimi's own citations unverified.

---

**Phase 9 complete — review resolved, human-approved. Documentation-only changes.** Proceed to
Phase 10 (Test Generation) on approval.
