# auth · T02 · Phase 13 — PR / Commit Preparation

Consumes `artifacts/12-specification-verification.md` — **PASS**. Proceeding per gate. No code
changed in this phase; this artifact documents the commit/PR only.

## Trailer deviation (flagged, not silent)

Same generator-artifact class documented in `AI_CONTEXT_ANALYSIS.md` and flagged again in T01's Phase
13: the template's literal trailer is `Co-Authored-By: Claude Opus 4.8 (1M context)
<noreply@anthropic.com>`, a fixed string regardless of which model actually ran the task. This
session's work was performed by Claude Sonnet 5, so the trailer below uses that accurate identity.

## Commit title

```
auth: resolve Q1 — narrow KMS exception for TOTP-seed encryption (T02)
```

## Commit message

```
auth: resolve Q1 — narrow KMS exception for TOTP-seed encryption (T02)

Resolve the open Q1/O1 blocker (TOTP seed encryption vs. D-010's "no
AWS SDK code" rule) with an author-approved decision: a single,
narrowly-scoped KMS GenerateDataKey/Decrypt call confined to the
future com.themistra.auth.mfa.MfaSeedEncryption (task #16), and
nowhere else in the service. D-010's general prohibition stands
everywhere outside that one class.

Add docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md,
recording the decision, the versioned ciphertext envelope layout
(format byte, wrapped-data-key, nonce, AES-256-GCM ciphertext+tag),
the KMS GenerateDataKey key spec (AES_256, 32-byte data key), the
local-dev fallback (fixed local-only key, local profile only, no KMS
call), and an explicit threat-model note scoping DB-write-tampering
(no AAD) as out of scope given this service's non-custodial, web2
security posture.

Add auth-decisions.md D-025, the formal decision-log entry
(Context/Alternatives/Selected/Trade-offs/Impact), correctly
numbered after the existing D-024 (an initial Phase 4/5 assumption of
D-015 was caught and corrected before writing - auth-decisions.md
had already grown past D-014 from unrelated later work).

Update spec/auth-service/design.md: new LOCKED decision L14 records
the mechanism and points at the ADR; O1 is marked resolved in place,
keeping its bold label per the existing Q6 strikethrough convention;
the seed-kek-arn config comment is corrected (no O1 dependency, no
rename needed - it already maps to KMS's KeyId parameter).

Update spec/auth-service/package.md: Q1 marked resolved per the
existing Q6 precedent (label stays, stale question text struck,
resolution appended).

Update spec/auth-service/agents.md: the Security section's blanket
"no AWS SDK" line is narrowed to name this one scoped exception
rather than being silently loosened or left inconsistent with the
new LOCKED decision.

No services/auth/src/main/java/com/themistra/auth/mfa/ code was
written (task statement: decision precedes code). No V1-V4 migration
was touched. O2-O5 and Q2-Q6 remain byte-identical to pre-T02,
verified via git diff, not just asserted.

Full 14-phase workflow run (spec-driven audit trail in
.ai/prompts/auth/T02/artifacts/), including a Phase 3/8/11
adversarial review pass (one Phase 3 finding corrected on
verification: the Crypto Service is real, contrary to the review's
claim, but moot since option B was selected over option C) and two
Human Approval gates (Phase 4: option selection, decision depth, and
guardrail-override authorization; Phase 9: individual disposition of
all eight Phase 7/8 findings).

Task: spec/auth-service/tasks.md #2. Requirements: R22.
Locked decisions: L6, L13 (amended), L14 (new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Across this task's full commit history on this branch** (`46b786b`..`744835b` plus this phase's
remaining two files):

- `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md` — new.
- `services/auth/docs/architecture/auth-decisions.md` — modified: `D-025` appended.
- `spec/auth-service/design.md` — modified: `L14` added, `O1` resolved, `§4c` comment corrected.
- `spec/auth-service/package.md` — modified: `Q1` resolved.
- `spec/auth-service/agents.md` — modified: Security section D-010 line narrowed.

**Spec-driven workflow scaffolding (generated, already committed earlier on this branch):**
- `.ai/prompts/auth/T02/00-*.md` … `13-pr-preparation.md`, `README.md` — the 14 phase prompts.

**Audit trail (this task's produced artifacts, incrementally committed across `46b786b`..`744835b`):**
- `.ai/prompts/auth/T02/artifacts/00-repository-understanding.md` through
  `09-review-resolution.md` — already committed.
- `.ai/prompts/auth/T02/artifacts/10-test-generation.md` — updated this session (Phase 11's three
  manifest-gap suggestions folded in), **not yet committed**.
- `.ai/prompts/auth/T02/artifacts/12-specification-verification.md` — written this session, **not
  yet committed**.
- `.ai/prompts/auth/T02/artifacts/13-pr-preparation.md` (this file) — **not yet committed**.

**Explicitly NOT part of this task's changes:** the root-level `prompt` scratch file (untracked,
predates and is unrelated to T02).

## Summary

T02 closes a named specification blocker (Q1/O1) with an author-approved, fully documented decision:
a narrow, scoped KMS envelope-encryption exception to D-010, confined to one future class. No
application code was written or touched — the task's entire deliverable is specification and
decision-record text, verified against every acceptance criterion in the frozen brief. Three
presentation/consistency issues (self-review, confirmed by independent review) and four technical
completeness gaps (independent review only) were each individually dispositioned by the author and
applied, not batch-approved.

## Testing performed

No automated tests — `package.md` §8 has no named test for this task, and no runtime code exists to
test (frozen-brief decision, confirmed correct by both self-review and independent review). Verification was operational/file-based instead:
- `grep -rn "D-025\|L14\.\|ADR-0003\|0003-narrow"` across all touched files — every cross-reference
  resolves consistently; no stale `D-015` reference (an initial numbering assumption, corrected before
  writing) survives anywhere.
- `git diff ab71e50 HEAD -- .../V1__auth_baseline_schema.sql` — empty; V1 untouched.
- `git status --short -- .../mfa/` — empty; no `mfa/` code created or modified.
- `git diff ab71e50 HEAD -- spec/auth-service/design.md spec/auth-service/package.md` — confirmed
  only `L14`/`O1`/`§4c` (design.md) and `Q1` (package.md) changed; `O2`–`O5`/`Q2`–`Q6` byte-identical
  to pre-T02.
- Manual re-read of `docs/adr/0003-...md` in full after Phase 9's additions, confirming internal
  consistency (no contradictions between the original Decision/Context/Consequences and the four
  items added during review resolution).

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 2 — "Resolve Q1 (TOTP encryption)."
- **Requirements:** R22 (encryption mechanism only; endpoint behavior remains task #16/#19's scope).
- **LOCKED decisions:** L6 (unaffected), L13 (narrowly amended by explicit human decision, not
  violated — see Phase 12's principal-engineer assessment), L14 (new, this task's own output).

## Branch / merge note

This work is on `spec/service-specs-and-ai-framework`, not `main`; per `agents.md`, `main` remains
deployable throughout. This task's diff is entirely specification/decision-record text — no
application code, no schema, no build configuration — so it carries no deployment risk of its own;
its only downstream effect is constraining how task #16 must be implemented.
