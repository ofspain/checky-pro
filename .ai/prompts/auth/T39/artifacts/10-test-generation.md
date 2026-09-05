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

## Kimi Phase 11 test review — gaps closed

All 6 findings evaluated against source and against this task's own proportionate scope before
disposition.

| Gap | Disposition |
|---|---|
| Gap 1 — no automated guard against stale line-number citations | **Rejected.** Building CI/pre-commit tooling to detect citation drift is real, disproportionate infrastructure work for a decision-log update task — matches the existing document's own unchanged convention (line numbers throughout, never guarded) and Kimi's own framing as "a general documentation-hygiene improvement, not a T39 blocker." |
| Gap 2 — no test verifying every `design.md` O-item is tracked | **Rejected**, same reasoning as Gap 1 — a parser/static-analysis tool is disproportionate for this task. Traceability is manually complete as of this task (O1=D-025, O2=D-026, O3=D-027, O4=D-028, O5=D-029). |
| Gap 3 — D-027 should cross-reference `design.md` §4b O3 explicitly | **Accepted.** One-line addition to D-027 pointing back to the original framing, making the relationship explicit for a reader who encounters `design.md` first. |
| Gap 4 — no test verifying D-xxx numbering is contiguous | **Rejected** — disproportionate tooling for a low-probability, low-consequence manual-edit error, matching Kimi's own "low-value" framing. |
| Gap 5 — no automated verification D-025 still covers Q1/O1 | **Accepted — no action**, matching Kimi's own conclusion ("No new test is needed"). Noted here for the record: D-025's claims are corroborated by the still-passing MFA integration tests and the compile-time KMS dependency, not merely a document-to-document cross-reference. |
| Gap 6 — new entries lack an explicit verdict line | **Rejected — already resolved at Phase 4**, same disposition as the earlier, identical concern (Phase 3 Finding 8): several existing entries (D-018/D-019/D-021) also state only "Reference influence: None" with no separate verdict line when there is no reference-project comparison to accept/reject — the same pattern applied consistently to D-026 through D-029, none of which involve a reference-project alternative. |

## Verification performed

- All four entries' citations independently re-verified against current source at Phases 6, 7, and
  9 (not carried forward unverified between phases).
- Full `auth-decisions.md` re-read after every edit (Phases 6, 9, 11) to confirm formatting
  consistency with the existing 25 entries.
- No production/test code touched — nothing to compile or run.

---

**Phase 10 complete — test manifest written and updated post-Phase-11 (no tests, documentation-only
task).** Proceed to Phase 12 (Specification Verification) on approval.
