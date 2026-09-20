<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T39 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

## Decision packet

All 8 Phase 3 (Kimi) findings verified before disposition.

| # | Finding | Disposition |
|---|---|---|
| 1 | O2's concrete threshold values not stated in the brief | Accepted. Explicit values folded into AC2 below. |
| 2 | "MFA folded into login bucket" claim needs a code citation | Accepted, verified: `RateLimitFilter.java:23-26`'s own Javadoc explicitly states this. Cited below. |
| 3 | O3's "still open" entry — should it stay open or be retroactively closed? | **Human-gate decision: stays open.** Recording a genuine non-decision is honest; retroactively declaring "we chose null as the generic default" would be revisionist — no deliberate choice among the three named options was ever made. |
| 4 | O4 should note the default form still supports the TOTP/recovery-code single-request flow | Accepted, verified: `TotpAuthenticationProvider` + `mfaCode` form field. Cited below. |
| 5 | AC5 should cite `RecoveryCode.java` directly | Accepted. Cited below. |
| 6 | Broader stage-level retrospective scope question needs resolution | **Human-gate decision: limit to the four named items + Q1.** Any broader retrospective for tasks #17-#38's other decisions is a separate, explicitly-scoped future documentation task, not folded into T39. |
| 7 | Q1 confirmation should cite D-025 explicitly | Accepted. |
| 8 | New entries should note "Accept/Modify/Reject Reason"/reference-influence format completeness | Accepted, with a scoping note: several existing entries with no reference-project comparison to make (D-018/D-019/D-021) simply state "Reference influence: None" with no separate verdict line — the same pattern applies to O2-O5's new entries, none of which involve rejecting/adopting a reference-project alternative. |

## Frozen brief (Phase 2 TIB, as amended)

Unchanged in structure; AC2/AC4/AC5 amended with explicit values/citations per Kimi Findings 1/2/4/5/7:

- **AC1** — Q1 confirmed already resolved and recorded: D-025 (`auth-decisions.md:212-219`)
  already records the narrow KMS envelope-encryption exception for `MfaSeedEncryption`; no edit
  needed.
- **AC2** — O2: new entry recording thresholds of **10 login attempts/minute, 5 password-reset
  confirmations/minute, and 30 OAuth2 token requests/minute** (`application.properties:104-106`),
  with MFA verification folded into the `/login` bucket — cross-referencing `RateLimitFilter.java`'s
  own Javadoc (lines 23-26), which explicitly states MFA verification happens inside the same
  `/login` request via `TotpAuthenticationProvider`, and D-013 (the mechanism decision this entry
  completes with concrete numbers).
- **AC3** — O3: new entry honestly stating the device-label source **remains unresolved** —
  `ReuseDetectingAuthorizationService.java:95` passes a literal `null` for `deviceLabel` on every
  real token issuance; the schema/API support the concept (D-003), but the source decision (client-
  supplied / `User-Agent` hash / generic default) was never made. Recorded as an open item, per the
  Phase 4 human-gate decision above.
- **AC4** — O4: new entry recording the by-omission choice of the default Spring Security form login
  page — no custom template exists under `src/main/resources`. Noting explicitly that the default
  form still supports the password + TOTP/recovery-code single-request flow, since
  `TotpAuthenticationProvider` consumes `mfaCode` as an additional form parameter on the same
  `/login` POST — no custom template was required for that reason.
- **AC5** — O5: new entry recording **SHA-256** as the selected recovery-code hashing primitive,
  matching the spec's own suggested default. Evidence: `RecoveryCode.codeHash` stores a SHA-256 hex
  digest (`RecoveryCode.java:15-17,39-41`; confirmed via `MfaService.java:139`,
  `Hashing.sha256(rawCode)`).

### Scope (reaffirmed)

**In**: Q1 confirmation + four new entries (O2, O3, O4, O5). **Out**: any broader retrospective
covering tasks #17-#38's other undocumented decisions — explicitly deferred to a future, separately-
scoped task per the Phase 4 gate decision above, not silently expanded into this one.

### Open Questions

No blockers remaining. Both genuine scope questions (O3's disposition, the broader-retrospective
question) are resolved above via human gate.

---

**Phase 4 complete — task brief frozen and approved.** Proceed to Phase 5 (Implementation Plan).
