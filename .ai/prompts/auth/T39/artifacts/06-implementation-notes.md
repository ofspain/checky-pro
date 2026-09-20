<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T39 · Phase 6 — Implementation Notes

Documentation-only change. Appended four new entries (D-026 through D-029) to
`services/auth/docs/architecture/auth-decisions.md`, immediately after D-025.

## What changed

- **D-026** (resolves O2) — rate-limit thresholds (10/5/30 per minute) with the MFA-folded-into-
  login-bucket design note.
- **D-027** (O3, recorded as still unresolved) — honestly documents that the device-label source was
  never actually decided; `deviceLabel` is `null` by omission, not by a chosen default.
- **D-028** (resolves O4) — the default Spring Security login form, no custom template, with the
  reasoning (the form already supports the single-request password+MFA flow via `mfaCode`).
- **D-029** (resolves O5) — SHA-256 recovery-code hashing, with the reasoning distinguishing it from
  password hashing (a recovery code is high-entropy and single-use, so bcrypt's deliberate slowness
  isn't buying anything a low-entropy secret would need it for).

**Q1 confirmed to need no edit** — D-025 already records its resolution.

## Deviations forced by reality

None. Every entry's evidence was verified directly against source at Phase 0/3/4 before drafting;
no discrepancy appeared during actual writing.

## Verification performed

- Re-read the full 262-line file after editing to confirm formatting consistency with the existing
  25 entries — headings, bullet structure, and cross-reference style all match.
- Every citation in D-026/D-028/D-029 (`application.properties` line numbers, `RateLimitFilter.java`
  line numbers, `RecoveryCode.java`/`MfaService.java` line numbers) re-confirmed against current
  source, not carried forward unverified from earlier phases.
- D-027's central claim (`deviceLabel` always `null` in production) re-confirmed directly against
  `ReuseDetectingAuthorizationService.java:95`.

---

**Phase 6 complete — implementation written (documentation only).** Proceed to Phase 7 (Self
Review) on approval.
