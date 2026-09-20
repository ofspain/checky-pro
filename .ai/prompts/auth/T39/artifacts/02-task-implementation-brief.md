<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T39 · Phase 2 — Task Implementation Brief

## Task

Update `services/auth/docs/architecture/auth-decisions.md` to record the resolution of Q1 (already
done, confirm only) and O2-O5 (four new entries needed).

## Purpose

Keep the durable decision log honest and current — several real decisions made during
implementation (rate-limit thresholds, login-page choice, recovery-code hashing) and one genuinely
still-open item (device-label source) are not yet reflected in the log a future reader would consult.

## Scope

**In**: confirming Q1's existing D-025 entry is sufficient; adding four new entries for O2, O3, O4,
O5, each following the document's own established format.

**Out**: any other decision made across tasks #17-#38 not named in the task statement (carried as an
open question to this phase's own gate, not silently expanded into scope without a decision);
modifying `spec/auth-service/` files (the source-of-truth open-items lists themselves, e.g. marking
O2/O4/O5 as resolved in `design.md` — out of this task's file scope, which is `auth-decisions.md`
only).

## Business Rules

None scoped.

## Locked Decisions

None scoped.

## Dependencies

None.

## Inputs

Current `auth-decisions.md` content; `design.md` §4b's O2-O5 original text; the concrete
values/states verified at Phase 0 (`application.properties`'s three rate-limit properties,
`ReuseDetectingAuthorizationService`'s null device label, absence of a custom login template,
`RecoveryCode`'s SHA-256 hashing).

## Outputs

Four new decision-log entries (D-026 through D-029, continuing the existing sequence) plus
confirmation that Q1/D-025 needs no change.

## State Changes

None to runtime behavior, schema, or code — documentation only.

## Files to Create

None.

## Files to Modify

- `services/auth/docs/architecture/auth-decisions.md`

## Files NOT to Modify

Everything else, including every `spec/auth-service/` file (D-026 through D-029 record what was
decided; they do not retroactively edit `design.md`'s own O-numbered list).

## Acceptance Criteria

- AC1 — Q1 confirmed already resolved and recorded (D-025); explicitly stated in the new content,
  not silently skipped.
- AC2 — O2: new entry with the three concrete threshold values and the MFA-folded-into-login-bucket
  design note, cross-referencing D-013 (the mechanism decision this entry completes).
- AC3 — O3: new entry honestly stating the device-label source remains unresolved — `deviceLabel` is
  always `null` in production; the schema/API exist, the source decision (client-supplied/User-Agent
  hash/generic default) was never made.
- AC4 — O4: new entry recording the by-omission choice of the default Spring Security form login
  page, no custom template built.
- AC5 — O5: new entry recording SHA-256 as the selected recovery-code hashing primitive, matching the
  spec's own suggested default.

## Required Tests

None — documentation-only task.

## Constraints

- **Format**: every new entry must match the document's existing Decision · Context · Alternatives ·
  Selected Approach · Trade-offs · Impact · Reference-Project Influence structure exactly.
- **Honesty**: O3's entry must not fabricate a resolution — recording "still open" is the correct,
  complete answer for that item.
- **No spec-file edits**: `design.md`'s own O-numbered list is not touched by this task.

## Open Questions

**Blocker for Phase 4, not for implementation planning.** Whether to also add a broader stage-level
retrospective entry (or entries) covering tasks #17-#38's other undocumented decisions, beyond the
four named O-items — genuinely undecided, carried from Phase 1.

---

**Phase 2 complete — implementation brief written.** Proceed to Phase 3 (Design Challenge) on
approval.
