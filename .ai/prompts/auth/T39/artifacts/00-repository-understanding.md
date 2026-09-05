<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T39 · Phase 0 — Repository Understanding

Task statement: update `auth-decisions.md` to record decisions made while implementing, especially
the resolution of Q1 and any O2-O5 choices.

---

## 1. Architecture summary

`services/auth/docs/architecture/auth-decisions.md` is a continuously-maintained decision log,
D-001 through D-025, format: Decision · Context · Alternatives · Selected Approach · Trade-offs ·
Impact · Reference-Project Influence · Accept/Modify/Reject Reason. It cross-references
`package.md` §11's open questions (Q1-Q6) and `design.md` §4b's OPEN decisions (O1-O5) by ID where a
decision resolves one.

**The log currently stops at D-024/D-025, chronologically around the MFA implementation task
(#16)** — D-024 is explicitly "Controller stage" work, D-025 resolves O1 (TOTP seed encryption,
task #16's blocker). Nothing from roughly task #17 onward (MFA completion, API keys T24-T27, rate
limiting T31, contract files T33, ArchUnit hardening T32/T35, token-claims doc T34, end-to-end test
T36, full-suite fixes T37, gap-analysis review T38) has a corresponding decision-log entry yet.

## 2. Existing code this task touches

This task edits exactly one file: `services/auth/docs/architecture/auth-decisions.md`. No source
code changes. Investigated the current state of each of the task statement's named items:

- **Q1** (`package.md` §11): "TOTP seed encryption KMS approach." **Already resolved and already
  recorded** — D-025 explicitly states "(resolves Q1/O1)". No new action needed beyond confirming
  this (the task statement's "especially the resolution of Q1" is already satisfied by existing
  content).
- **O2** (`design.md` §4b): "Per-account rate-limiting mechanics... propose thresholds." **Partially
  captured, not fully.** D-013 ("resolves OD-3" — an older numbering, same topic) already documents
  the *mechanism* choice (in-process Bucket4j per replica + durable lockout backstop, not Redis) —
  but not the *specific threshold values* O2's own text explicitly asks for. Verified in
  `application.properties`: `login-per-minute=10`, `password-reset-per-minute=5`,
  `oauth-token-per-minute=30`. No separate MFA-verify threshold exists — MFA is folded into the
  login bucket by construction (submitted via the same `/login` POST, T20's single-request design),
  a deliberate T31 Phase 3 finding (D1), not an oversight. **This needs a new decision-log entry.**
- **O3** (`design.md` §4b): "Session/device label source." **Confirmed still genuinely
  unresolved** — `ReuseDetectingAuthorizationService.java:95` passes a literal `null` for
  `deviceLabel` on every real token issuance; `SessionResponse`'s own Javadoc already documents this
  ("`deviceLabel` is `null` for every session today... remains unresolved by the spec author"). The
  schema/API support the concept (D-003); the actual "what determines the label" choice (client-
  supplied vs. `User-Agent` hash vs. generic default) was never made. **This needs an entry stating
  it remains open, not a fabricated resolution.**
- **O4** (`design.md` §4b): "Login page presentation." **Resolved by omission, not yet recorded.**
  No custom login template exists anywhere under `src/main/resources` — the default Spring Security
  form login page is used (confirmed via `SasLoginIntegrationTest`'s own reliance on "the real
  default-login-page markup" for its CSRF-scraping helper). **This needs a new decision-log entry**
  documenting the by-omission choice explicitly, per O4's own text ("propose one option; proceed if
  low-risk").
- **O5** (`design.md` §4b): "Recovery-code hashing primitive." **Resolved, matches the suggested
  default, not yet recorded.** `RecoveryCode.codeHash` is SHA-256 hex (confirmed via
  `MfaService.java:139`, `Hashing.sha256(rawCode)`) — the default O5's own text names. **This needs
  a new decision-log entry**, per O5's own text ("default to SHA-256 unless changed" still implies
  recording that the default was in fact taken, not silently assumed).

## 3. Established patterns to follow

- **The decision-log's own format** (Decision · Context · Alternatives · Selected Approach ·
  Trade-offs · Impact · Reference-Project Influence · Accept/Modify/Reject Reason) is fixed by the
  document's own header instruction ("Maintained continuously per the provisioning prompt") — every
  new entry must follow it exactly, matching D-001 through D-025's own style.
- **Cross-referencing by ID** — every entry that resolves a named open item states so in its title
  (e.g., "(resolves Q1/O1)", "(resolves OD-3)") — new entries for O2/O4/O5 (and O3's still-open
  status) should follow the same convention.
- **Stage-level entries exist** (D-023 "Testing-stage scope", D-024 "Controller stage: scope
  corrections") for broader implementation-stage retrospectives, distinct from single-decision
  entries — a possible model if T39's scope is judged to extend beyond just Q1/O2-O5 (a Phase 1/4
  question, not decided here).

## 4. Testing conventions

Not applicable — this is a documentation-only task with no test surface.

## 5. Known gaps / unknowns

- **O3 has no resolution to record** — the honest content for this task's O3 entry is "still open,"
  not a decision. This is expected and correct, not a blocker.
- **Whether T39's scope should extend beyond Q1/O2-O5 to also capture other significant decisions
  made across tasks #17-#38** (MFA completion, API-key design, rate-limiting design trade-offs
  beyond the threshold numbers, contract/ArchUnit hardening decisions, the various T36-T38 human-gate
  decisions this session itself made) is genuinely ambiguous from the task statement's own wording
  ("Record decisions made while implementing (**especially** the resolution of Q1 and any O2-O5
  choices)" — "especially" implies a non-exhaustive priority list, not an exclusive one). Carried to
  Phase 1 as an open question, not decided here.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
