# auth · T02 · Phase 2 — Task Implementation Brief (TIB)

## Task

Resolve Q1 (TOTP encryption): obtain an author decision on the TOTP-seed encryption mechanism, then record that decision in `spec/auth-service/design.md`, `spec/auth-service/package.md`, and `services/auth/docs/architecture/auth-decisions.md`. No `mfa/` code is written in this task.

## Purpose

`target-design.md` specifies AES-GCM encryption of TOTP seeds with a KMS-enveloped data key, but D-010 forbids AWS SDK code inside the service. This is a genuine, named blocker (`package.md` §11 Q1) for any future MFA implementation task. This task exists to close that blocker with an explicit, documented decision before code is written against it.

## Scope

**In:** Presenting the O1 options with trade-offs; obtaining the author's selection; writing that selection into the three files above.
**Out:** Any `mfa/` Java code, entities, services, controllers, or tests. Any other OPEN decision (O2–O5) or Open Question (Q2–Q6). Any schema change (V1 is immutable, L1).

## Business Rules

- **R22** — TOTP secret generation must "encrypt it" before persisting; this task determines the mechanism, not the endpoint behavior.

## Locked Decisions

- **L6** — TOTP algorithm/format constrains what is being encrypted (a 20-byte RFC 6238 seed), not how.
- **L13** — Secrets discipline: no key material committed or hardcoded outside `local` profile; ESO is the only injection path.
- **D-010** (`auth-decisions.md`, cited by name in O1/Q1) — no AWS SDK code in the service; every option must satisfy this or explicitly document a narrow, deliberate relaxation.

## Dependencies

- Config placeholder `themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}` (`design.md` §4c) — may need renaming/re-scoping depending on the option selected (flagged, not decided, here).
- `mfa_enrollments.secret_encrypted BYTEA` column (V1, immutable) — the eventual storage target; its inline comment ("AES-GCM, KMS-enveloped data key") predates this task and may become stale depending on the outcome.
- External Secrets Operator — the injection mechanism for whatever key/reference material the chosen option needs.

## Inputs

- The three O1 candidate options and their described trade-offs (`design.md` §4b O1).
- The author's decision (obtained via the Phase 4 human-approval gate).

## Outputs

- One recorded decision, with rationale, in three places (see Files to Modify).

## State Changes

None. No database, no runtime state — this task only changes specification/documentation artifacts.

## Files to Create

None.

## Files to Modify

- `spec/auth-service/design.md` — §4b O1 resolved (moved to §4a LOCKED or annotated resolved-in-place).
- `spec/auth-service/package.md` — §11 Q1 marked resolved (struck-through + "Resolved (date)", per the existing Q6 pattern).
- `services/auth/docs/architecture/auth-decisions.md` — new entry, next sequential `D-01N`, in the file's established Context/Selected/Trade-offs/Reference-influence shape.

**Note (carried from Phase 0/1, unresolved):** the standing phase guardrail "never modify the specification files under `spec/`" conflicts with modifying `design.md`/`package.md` as listed above. This brief lists them because the task statement requires it; whether this guardrail yields to the task statement is the human decision this brief flags for Phase 4 — not assumed here.

## Files NOT to Modify

- Anything under `services/auth/src/main/java/com/themistra/auth/mfa/` (including `package-info.java`).
- `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` (LOCKED, L1) — even though its comment may become stale relative to the chosen option, it is not edited by this task.
- Any other OPEN decision (O2–O5) or Open Question (Q2–Q6) in `design.md`/`package.md`.

## Acceptance Criteria

- **AC1.** Author decision recorded, selecting one of the O1 options (or a documented variant) with justifying trade-offs.
- **AC2.** `design.md` reflects the resolution (pending Phase 4's ruling on the guardrail question).
- **AC3.** `package.md` §11 Q1 marked resolved, consistent with the Q6 precedent.
- **AC4.** `auth-decisions.md` gains a new `D-01N` entry.
- **AC5.** No file under `mfa/` is created or modified.
- **AC6.** If the resolution relaxes D-010, that relaxation is explicit and scoped, not implied.

## Required Tests

None — no code is authorized; `package.md` §8 has no named test for this task.

## Constraints

- **Security:** whichever option is selected must keep TOTP seeds encrypted at rest and never expose key material in code, logs, or committed config (L13).
- **Module boundaries:** N/A — no code.
- **Transactions / thread-safety / null-handling:** N/A — no code.
- **Process:** the decision must be genuinely made by the author (human), not inferred or defaulted by the model — the task statement is explicit ("Get an author decision").

## Open Questions

- **Q1 itself** — which O1 option (a/b/c) or variant the author selects. Blocker; resolved only at the Phase 4 human-approval gate.
- **Guardrail conflict** — whether "never modify `spec/`" yields to this task's explicit requirement to edit `design.md`/`package.md`. Blocker for Files-to-Modify finalization; must be settled at Phase 4.
- **V1 comment staleness** — non-blocking, but should be acknowledged in the `auth-decisions.md` entry if the chosen option diverges from "KMS-enveloped data key."
