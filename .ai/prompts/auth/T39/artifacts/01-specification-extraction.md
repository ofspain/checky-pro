<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T39 · Phase 1 — Specification Extraction

## Business Rules

None scoped (process/documentation task, no requirement ID).

## Locked Decisions

None scoped. No LOCKED decision constrains how `auth-decisions.md` is updated — its own format is a
standing document convention (header instruction), not a numbered decision.

## Files involved

**To modify:**
- `services/auth/docs/architecture/auth-decisions.md` — append new entries.

**To read/cross-reference (no changes):**
- `spec/auth-service/package.md` §11 — Q1's original text (already resolved, D-025).
- `spec/auth-service/design.md` §4b — O1-O5's original text.
- `services/auth/src/main/resources/application.properties` — O2's concrete threshold values.
- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java` —
  O3's current (null) state.
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java`,
  `RecoveryCode.java` — O5's SHA-256 confirmation.
- No custom login template exists under `src/main/resources` — O4's by-omission resolution.

## Dependencies

None — documentation-only, no runtime dependency.

## Acceptance Criteria

- AC1 — Q1's resolution is confirmed already recorded (D-025); no new entry needed, only
  confirmation.
- AC2 — O2 (rate-limit thresholds) gets a new decision-log entry with the concrete values and the
  MFA-folded-into-login-bucket design note.
- AC3 — O3 (device-label source) gets a new entry honestly recording it as still unresolved, not a
  fabricated decision.
- AC4 — O4 (login page presentation) gets a new entry recording the by-omission default-form-login
  choice.
- AC5 — O5 (recovery-code hashing) gets a new entry recording SHA-256 as selected, matching the
  spec's own suggested default.

## Tests required

None — documentation-only task, no test surface.

## Open Questions

**Not a blocker, genuine scope question for Phase 4.** The task statement's "especially" wording
("Record decisions made while implementing (**especially** the resolution of Q1 and any O2-O5
choices)") does not exclude other undocumented decisions made across tasks #17-#38 (MFA completion,
API-key design, contract/ArchUnit hardening, this session's own T36-T38 human-gate decisions). This
phase's own scope is limited to Q1/O2-O5 per the header's explicit framing as the priority list;
whether to also add broader stage-level retrospective entries (matching D-023/D-024's own precedent)
is carried to Phase 4 as a genuine, undecided scope question, not resolved here.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
