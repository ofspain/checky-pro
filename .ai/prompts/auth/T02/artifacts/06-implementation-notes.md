# auth · T02 · Phase 6 — Implementation Notes

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN) and `artifacts/05-implementation-plan.md`.
Per the human decision recorded in Phase 4 ("I edit spec/ directly," scoped to T02 only), all writes
below were made directly rather than staged for the author to apply.

## What changed

1. **Created `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md`** — new ADR, next
   sequential number after `0002`. Documents the Decision/Context/Consequences exactly as planned in
   Phase 5, including the full ciphertext envelope layout table.

2. **Modified `services/auth/docs/architecture/auth-decisions.md`** — appended a new entry. **One
   deviation from the plan, caught before writing:** Phase 4/5 both assumed the next available id was
   `D-015`, based on an earlier, incomplete read of this file. Before editing, I re-grepped the file
   and found entries already extend to `D-024` (`D-015`–`D-024` cover OAuth2/refresh-token/RBAC/outbox/
   audit/controller-stage decisions from later work, not seen in the earlier partial read). The new
   entry is correctly numbered **`D-025`**, not `D-015`. Every other file references `D-025`
   consistently — this was caught and fixed before any cross-reference was written, not after.

3. **Modified `spec/auth-service/design.md`** — added `L14` to §4a (new LOCKED decision), resolved
   `O1` in §4b with a struck-through pointer to `L14`/`D-025`/ADR-0003, and cleaned up the `§4c`
   config comment (removed the stale "(implementation depends on O1)" note, replaced with a comment
   describing the KMS `KeyId` usage and pointing at `L14`/`D-025`). The config key itself,
   `themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}`, is **unchanged** — confirmed in Phase 5 that
   it already maps directly to KMS's `KeyId` parameter, so the Phase 2 TIB's speculation that it "may
   need renaming" did not materialize.

4. **Modified `spec/auth-service/package.md`** — struck through Q1 in §11, added a "Resolved
   (2026-07-22)" note pointing at `L14`/`D-025`/ADR-0003, matching the existing Q6 precedent exactly
   (same strikethrough + bold-resolved-date pattern).

5. **Modified `spec/auth-service/agents.md`** — narrowed the Security-section line from a blanket "no
   AWS SDK secret-retrieval in application code (D-010)" to name the specific exception: KMS
   `GenerateDataKey`/`Decrypt` inside `mfa.MfaSeedEncryption` only, with a pointer to ADR-0003/L14/
   D-025. No other wording in the file changed.

## Mapping to the frozen brief's Acceptance Criteria

- **AC1** (D-01N entry) — done as `D-025` (renumbered per the correction above), full
  Context/Selected/Trade-offs/Impact/Reference-influence shape.
- **AC2** (new ADR) — done; scope (KMS calls, confined class), what remains forbidden, and why are
  all present in ADR-0003.
- **AC3** (`design.md` updated) — done: new `L14`, `O1` resolved-pointer, `§4c` comment corrected.
- **AC4** (`package.md` Q1 resolved) — done, Q6-pattern strikethrough.
- **AC5** (`agents.md` D-010 line narrowed) — done, blanket statement replaced with named exception.
- **AC6** (full technical decision: ciphertext layout, nonce/tag storage, rotation, config keys,
  local-dev story) — done, all fixed concretely in ADR-0003 (layout table) and `D-025` (rotation via
  KMS's own annual rotation; config key retained unchanged; local-dev story specified as a
  version-`0x00` fallback for task #16 to implement).
- **AC7** (no file under `mfa/` touched) — confirmed; `git status` shows no changes under
  `services/auth/src/main/java/com/themistra/auth/mfa/`.

## Deviations from the plan

- **`D-015` → `D-025` renumbering** (above) — the only substantive deviation; a factual correction
  caught before writing, not a scope change. Every cross-reference across all five files uses `D-025`
  consistently (verified by grep after all edits).
- Everything else matches Phase 5's plan exactly: same files, same section targets, same envelope
  layout, same rotation/config/local-dev decisions.

## Verification performed

- `grep -rn "D-025\|L14\.\|ADR-0003\|0003-narrow"` across all five touched files (plus the new ADR)
  confirms every cross-reference resolves to the same, correct identifiers — no stale `D-015`
  reference left anywhere.
- Confirmed no file under `services/auth/src/main/java/com/themistra/auth/mfa/` was created or
  modified (`git status --short` scoped to that path is empty).
- Confirmed `V1__auth_baseline_schema.sql` was not touched.
- Re-read `design.md` L1–L14 and `§4b`/`§4c` in full after editing to confirm no unrelated text was
  altered beyond the planned O1/§4c lines.

## Known follow-ups (not this task's job, logged with owners per Phase 4)

- Task #16: fix TOTP seed entropy/byte length; implement `TotpGenerator` and `MfaSeedEncryption`
  against the ADR-0003 envelope layout.
- Task #18: fix recovery-code length/encoding.
- Task #13 / #20: decide whether failed MFA attempts feed the brute-force lockout counter.
- Infra/CDK (outside `services/auth`): enable KMS automatic annual rotation on the TOTP-seed CMK;
  scope the `auth-service` IRSA role to `kms:GenerateDataKey`/`kms:Decrypt` on that one CMK ARN.
