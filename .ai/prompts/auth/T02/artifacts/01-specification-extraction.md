# auth · T02 · Phase 1 — Specification Extraction

Consumes `artifacts/00-repository-understanding.md`. Scope: task 2 only ("Resolve Q1").

## Business Rules

- **R22.** WHEN an authenticated user without a confirmed TOTP enrollment calls `POST /accounts/me/mfa/totp`, THEN the system SHALL generate a random TOTP secret, **encrypt it**, persist it as unconfirmed, and return an `otpauth://` provisioning URI. This task resolves *how* "encrypt it" is implemented; it does not implement the endpoint.

## Locked Decisions

- **L6.** TOTP algorithm is RFC 6238, 30s step, 6 digits, HMAC-SHA1; recovery codes are SHA-256-hashed. Constrains the *shape* of the secret being encrypted, not the encryption mechanism itself — the two are independent.
- **L13.** Secrets discipline: no secret/credential/key material committed to the repo; ESO injects values; hardcoded defaults exist only for local dev and are refused outside `local` profile. Directly constrains every Q1 option — whichever is chosen, the key/KEK material itself must arrive via ESO, never be hardcoded or committed.

Also directly load-bearing though not in the task header's scoped set (cited by name in the task statement and O1 itself, so within this task's clear boundary per the Phase 1 instruction to widen only where clearly required):
- **D-010** (`auth-decisions.md`) — no AWS SDK code in the service; this is the hard constraint every Q1 option must satisfy or explicitly request relief from.

## Files involved

**Existing, read-only (context, not modified by this task):**
- `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` — `mfa_enrollments.secret_encrypted BYTEA` column, with a comment ("AES-GCM, KMS-enveloped data key") that predates and overlaps this task's question.
- `services/auth/src/main/java/com/themistra/auth/mfa/package-info.java` — states the module's intent; no other code in `mfa/` exists.
- `services/auth/docs/architecture/target-design.md` §16 — lists "TOTP-seed KEK reference" as one of the ESO-injected secrets.

**Files the task expects to be produced/updated (the task's actual deliverable — see Phase 0's flagged guardrail conflict):**
- `spec/auth-service/design.md` §4b (O1) — move the resolved approach out of OPEN and either into a new §4a LOCKED entry or an explicit resolution note against O1.
- `spec/auth-service/package.md` §11 (Q1) — mark resolved, following the existing struck-through + "Resolved (date)" convention used for Q6.
- `services/auth/docs/architecture/auth-decisions.md` — new decision entry (next available `D-01N`) recording the chosen option, in the file's established Context/Selected/Trade-offs/Reference-influence shape.

**Explicitly NOT touched by this task:** any file under `services/auth/src/main/java/com/themistra/auth/mfa/` beyond the existing `package-info.java` — the task statement is "before writing `mfa/` code."

## Dependencies

- **Config key (already declared, `design.md` §4c VERBATIM):** `themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}` — the placeholder this task's decision will give operational meaning to (or which may need renaming/re-scoping depending on the option chosen; that's a question for Phase 2/3, not asserted here).
- **Entity:** `mfa_enrollments.secret_encrypted` (V1, immutable) — the storage target any chosen approach must write into.
- **External Secrets Operator** — the injection mechanism for whatever key/reference material is needed, per L13 and target-design.md §16.
- **No contracts are implicated.** The four contracts listed in this task's generated header (`auth.yaml`, `token-claims.md`, `email-requested.v1`, `security-audit.v1`) are the generator's blanket per-service list (documented in `AI_CONTEXT_ANALYSIS.md`), not a claim that this task touches them — a decision about seed-encryption mechanics has no API surface, event payload, or token-claims impact.

## Acceptance Criteria

Mapped to R22 (the only requirement this task constrains, without implementing):

- **AC1.** An author decision is recorded selecting one of the O1 options (or a variant), with the trade-offs that justified it.
- **AC2.** `spec/auth-service/design.md` §4b/§4a reflects the resolved approach (subject to the Phase 4 resolution of the guardrail-vs-deliverable conflict flagged in Phase 0).
- **AC3.** `spec/auth-service/package.md` §11 Q1 is marked resolved, consistent with the Q6 precedent.
- **AC4.** `services/auth/docs/architecture/auth-decisions.md` gains a new `D-01N` entry for the decision.
- **AC5.** No file under `services/auth/src/main/java/com/themistra/auth/mfa/` is created or modified (task statement: decision precedes code).
- **AC6.** The resolution does not conflict with D-010 (no AWS SDK in application code) unless the resolution explicitly and knowingly relaxes D-010 for a narrow, justified case (Q1 option (b) contemplates exactly this) — in which case that relaxation itself must be recorded, not silently implied.

## Tests required

None. `package.md` §8 has no named test for this task (confirmed in the header), and no code is authorized to be written, so no boundary tests apply either.

## Open Questions

- **Q1 itself** (`package.md` §11) — the actual author decision this task exists to obtain: (a) inject a symmetric data key via External Secrets and encrypt locally, (b) relax D-010 for a narrow KMS envelope-encryption call, or (c) route encryption through the Crypto Service. This is the genuine blocker the task is named for; it is not resolved by this phase, only scoped. Carried to Phase 2/3 for options analysis and Phase 4 for the human decision.
- **Guardrail conflict** (carried from Phase 0, not re-litigated here): the standing "never modify `spec/`" guardrail vs. the task's literal requirement to update `design.md`/`package.md`. Needs explicit human resolution at Phase 4, not an assumption in either direction.
- **V1 column comment ambiguity** (carried from Phase 0): whether `secret_encrypted`'s existing "KMS-enveloped data key" comment in the immutable V1 migration is binding or merely descriptive of the pre-Q1 assumption. If the chosen option is anything other than a KMS envelope call, this comment becomes stale/inaccurate but cannot be edited (L1) — worth surfacing as a residual documentation-only inconsistency, not a blocker, at Phase 2/3.
