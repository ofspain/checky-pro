<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T39 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T39 — Update auth-decisions.md with Q1/O2-O5 resolutions |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the four new decision-log entries (D-026 through D-029) in `services/auth/docs/architecture/auth-decisions.md` and the Phase 7 self-review with fresh eyes. This is a documentation-only task, so the review focuses on accuracy, consistency, and traceability.

---

## Finding 1 — D-026's "Alternatives" section does not list distinct alternatives

**Issue.** D-026's Alternatives section states "no rich numeric trade-off study" and describes how the values were proposed and confirmed. While honest, the document's established format uses the Alternatives section to list genuinely distinct options that were weighed (e.g., D-025's (a)/(b)/(c)). D-026's current shape makes the section read more like process notes than alternatives.

**Evidence.**
- `auth-decisions.md` lines 226-230: D-026 Alternatives.
- Compare to D-025 lines 215-216: three concrete alternatives (a), (b), (c).

**Recommendation.** Either rename the section to "How the values were chosen" or restructure it to list the conceptual alternatives (e.g., "(a) a single per-account bucket for all credential paths; (b) separate buckets per path"), moving the numeric values and confirmation process into Selected. This better matches the document's established format.

**Confidence.** Low.

---

## Finding 2 — D-026 slightly oversimplifies the OAuth2 token rate-limit scope

**Issue.** D-026 states `oauth-token-per-minute=30` applies to `/oauth2/token`. In reality, `RateLimitFilter` only applies this limit to the `refresh_token` grant (`grant_type=refresh_token`); other `/oauth2/token` grants (e.g., `authorization_code`) are not rate-limited by this filter. The decision entry could be read as implying all token requests are limited.

**Evidence.**
- `auth-decisions.md` line 231-232: "`oauth-token-per-minute=30` ... `/oauth2/token`."
- `RateLimitFilter.java` lines 107-111: `isOAuthTokenRefreshRequest` checks `grant_type=refresh_token`.

**Recommendation.** Update D-026's Selected and Impact lines to specify "`/oauth2/token` with `grant_type=refresh_token`" rather than the endpoint alone. This matches the actual implementation and avoids future misinterpretation.

**Confidence.** Medium.

---

## Finding 3 — D-026 omits the per-token granularity of the password-reset limit

**Issue.** D-026 says `password-reset-per-minute=5` applies to "password-reset confirm." The actual filter keys the bucket by the SHA-256 hash of the submitted reset token, not by account. This means the limit is per token, not per account — a nuance the decision entry should record because it was explicitly accepted in `RateLimitFilter`'s Javadoc.

**Evidence.**
- `auth-decisions.md` line 231: "`password-reset-per-minute=5`."
- `RateLimitFilter.java` lines 35-44: Javadoc explains per-token keying and the accepted narrowing.

**Recommendation.** Add a sentence to D-026's Selected or Trade-offs noting that the password-reset bucket is keyed per reset token (SHA-256 hash), not per account, by design.

**Confidence.** Low.

---

## Finding 4 — Line-number citations will go stale as code evolves

**Issue.** D-026-D-029 cite specific line numbers (e.g., `RecoveryCode.java:15-17,39-41`, `ReuseDetectingAuthorizationService.java:95`). This is consistent with the rest of the document, but line numbers are fragile. A future refactor could change line numbers without changing semantics, making the citations misleading.

**Evidence.**
- `auth-decisions.md` lines 250-252, 288-290: line-number citations.
- The existing document also uses line numbers, so this is not a new pattern.

**Recommendation.** No immediate change required, but consider adding method/field names alongside line numbers for stability (e.g., "`RecoveryCode.codeHash` field (lines 39-41)"). This is a general documentation-hygiene improvement, not a T39 blocker.

**Confidence.** Low.

---

## Finding 5 — D-028 could cite `SecurityChainsConfig` directly

**Issue.** D-028's evidence for using the default Spring Security form is the absence of login HTML in `src/main/resources` and `SasLoginIntegrationTest`'s CSRF-scraping helper. A stronger, more direct citation would be the security configuration itself, which explicitly uses `.formLogin(Customizer.withDefaults())`.

**Evidence.**
- `auth-decisions.md` lines 271, 278-280: D-028 Selected and Impact.
- `SecurityChainsConfig.java` (presumably) configures `.formLogin(Customizer.withDefaults())`.

**Recommendation.** Add a citation to the relevant `SecurityChainsConfig` line/method in D-028's Impact, alongside the existing test and absence-of-template evidence.

**Confidence.** Low.

---

## Finding 6 — D-027's non-decision shape is well-justified and acceptable

**Issue.** D-027 intentionally deviates from the standard format because it records an unresolved item rather than a decision. This is unusual for a decision log but is explicitly justified in the entry itself.

**Evidence.**
- `auth-decisions.md` lines 245-263: D-027 entry and its justification.
- `ReuseDetectingAuthorizationService.java` line 95: confirms `deviceLabel` is always `null`.

**Recommendation.** No change. The justification is clear and honest. Consider adding a brief note at the top of the decision log explaining that non-decisions may be recorded when explicitly deferred, so future readers are not surprised.

**Confidence.** N/A — confirmation, not a finding.

---

## Finding 7 — D-029's citation and rationale are accurate

**Issue.** Verified independently that `RecoveryCode.codeHash` is produced via `Hashing.sha256(rawCode)` in `MfaService.java:139` and stored as `CHAR(64)`. The trade-off explanation (high-entropy single-use code, bcrypt unnecessary) is consistent with the implementation and L6.

**Evidence.**
- `auth-decisions.md` lines 288-290.
- `MfaService.java` line 139: `RecoveryCode.create(accountId, Hashing.sha256(rawCode), now)`.
- `RecoveryCode.java` lines 39-41: `codeHash` column `CHAR(64)`.

**Recommendation.** No change.

**Confidence.** High.

---

## Finding 8 — No broader stage-level retrospective entries were added

**Issue.** The Phase 4 gate explicitly excluded a broader tasks-#17-#38 retrospective. The self-review confirms this scope discipline was maintained. The decision log may still be missing other important decisions made during implementation, but that was a deliberate scope boundary.

**Evidence.**
- `auth-decisions.md` ends at D-029; no additional retrospective entries.
- TIB §21-25: scope excludes other decisions.

**Recommendation.** No change for T39. Log a follow-up documentation task if the team wants a broader retrospective.

**Confidence.** High.

---

## Summary

The four new entries are accurate, well-sourced, and consistent with the task's scope. The most material finding is D-026's imprecision about the OAuth2 token rate-limit scope (Finding 2). The remaining findings are minor documentation-hygiene suggestions. D-027's honest recording of an unresolved item is a strength, not a defect.

(End of Phase 8 independent review.)
