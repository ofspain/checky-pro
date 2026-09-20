STATUS: FROZEN

# auth · T02 · Phase 4 — Frozen Task Brief

Approved by: femi (human approval gate, this session, via three explicit decisions — see Phase 3
dispositions #1, #9, and the Decision Depth ruling folded throughout). Consumes
`artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md`. This is the
terminal specification for T02 — downstream phases (5–13) implement against this document only and
may not renegotiate it.

## Task

**Resolve Q1 (TOTP encryption).** Get an author decision on seed encryption, update this spec and
`auth-decisions.md` with the chosen approach before writing `mfa/` code.

## Human decisions (this session)

1. **Q1 option selected: (B) — narrow KMS envelope-encryption call.** A minimal, scoped AWS KMS
   client call generates and unwraps a per-seed data key; the TOTP seed itself is encrypted locally
   with that data key (AES-256-GCM). This directly relaxes D-010 ("no AWS SDK code in the service"),
   which the author accepted knowing (per Phase 3 Finding 2) that selection requires a new ADR and an
   update to L13/`agents.md`, not just a decision-log entry — both are now in scope (see Files to
   Create/Modify below).
2. **Decision depth: full.** This task's recorded decision does not stop at "which option" — it also
   fixes the ciphertext byte layout, nonce/tag storage, KEK-rotation handling, final config keys, and
   the local-dev key story (Phase 3 Findings 3, 4, 6). The concrete values are worked out in Phase 5
   (Implementation Plan) against this brief's Acceptance Criteria; Phase 4 fixes *that* they are
   required outputs, not their exact bytes.
3. **Guardrail conflict: resolved in favor of the task statement, scoped to T02 only.** The standing
   "never modify `spec/`" guardrail yields here — the author explicitly authorized direct edits to
   `spec/auth-service/design.md`, `package.md`, and `agents.md` as this task's actual deliverable.
   This is a one-task override, not a change to the pipeline's default guardrail: T03 onward reverts
   to "never modify `spec/`" unless a future task states otherwise the same way T02 does.

## Purpose

Close a named blocker (`package.md` §11 Q1): `target-design.md` specifies AES-GCM with a
KMS-enveloped data key, but D-010 forbids AWS SDK code in the service. The author has now decided:
accept a narrowly-scoped exception to D-010 rather than either inventing a local-only scheme that
weakens custody or grafting the unrelated Crypto Service into an app-secret-encryption role it
doesn't own today (Phase 3 Finding 1, corrected — Crypto Service is real but its charter is
blockchain attestation, not this). This unblocks task #16 (`TotpGenerator` + `MfaSeedEncryption`)
with no follow-up spec questions.

## Phase 3 findings — dispositions

| # | Finding | Severity | Disposition | Reason |
|---|---|---|---|---|
| 1 | Option (c) "Crypto Service" framed as illusory/unstated dependency | High | **CORRECTED, then MOOT** | Independently verified false as stated: `spec/crypto-service/` is a real, documented service (`ARCHITECTURE.md` §3.4), and `tasks.md` task 16 already anticipates a Crypto-Service branch. The narrower, accurate concern — its charter today is attestation/receipt-signing, not app-secret encryption, so no contract exists for this use — is noted for the record but moot: the author selected option (B), not (C). |
| 2 | Option (B) relaxes a LOCKED decision without escalation guardrails | High | **ACCEPTED, now binding** | Author selected (B) knowing this. A new ADR (`docs/adr/0003-...md`) and an update to L13/`spec/auth-service/agents.md` are now required deliverables, added to Files to Create/Modify below. |
| 3 | No ciphertext structure / key-rotation requirement imposed | High | **ACCEPTED — full decision depth** | Folded into Acceptance Criteria; concrete layout fixed in Phase 5. |
| 4 | Decision doesn't fix the config surface | Medium | **ACCEPTED — full decision depth, with a correction** | Folded into Acceptance Criteria. Correction to the TIB's own hedge: the existing placeholder `themistra.auth.mfa.seed-kek-arn` maps directly to KMS `KeyId` for option (B) — it likely does **not** need renaming, contrary to what Phase 2 speculated. Phase 5 confirms this. |
| 5 | V1 inline-comment staleness acknowledged but not mitigated | Low | **ACCEPTED — and now largely moot** | Option (B) is "AES-GCM, KMS-enveloped data key" — almost exactly what the V1 comment on `secret_encrypted` already says. Choosing (B) means that comment turns out to be *accurate*, not stale. Still add the precise description to the new decision entry for anyone who reads V1's comment without this context. |
| 6 | No local-development KEK story required | Low | **ACCEPTED — full decision depth** | Folded into Acceptance Criteria; concrete approach fixed in Phase 5. |
| 7 | R22 framed too narrowly (secret generation only, not full endpoint scope) | Low | **ACCEPTED** | Wording corrected in this brief's Business Rules section below. |
| 8 | No forward hint for future verification tests | Low | **ACCEPTED** | Non-binding note added to Required Tests below, for the new decision entry to carry forward. |
| 9 | Guardrail conflict flagged but not escalated to a concrete workflow | Low (process) | **RESOLVED** | Human decision this session (see "Human decisions" #3 above): task-scoped override, later phases revert to the default guardrail. |
| 10 | No fallback if author is unavailable | Low | **MOOT** | Author was available and decided directly this session; no fallback mechanism needed for T02. |
| 11 | Seed entropy (byte length) undefined in spec | Medium | **ACCEPTED — deferred, not this task** | Kimi's own categorization: inherited spec gap, not T02's job. Deferred to task #16 (`TotpGenerator`), logged under Open Questions with that owner. |
| 12 | Recovery-code format (length/encoding) undefined in spec | Medium | **ACCEPTED — deferred, not this task** | Deferred to task #18 (MFA service — "recovery-code generation/verification"), logged under Open Questions with that owner. |
| 13 | MFA failures not connected to brute-force lockout counter | High | **ACCEPTED — deferred, not this task** | Deferred to task #13 (login failure/success tracking) and/or task #20 (SAS MFA step integration), whichever implements the MFA challenge path; logged under Open Questions with both as candidate owners. |

## Scope

**In:**
- Recording the author's decision (option B, full technical depth per above) as a new sequential
  entry in `services/auth/docs/architecture/auth-decisions.md` (next available `D-01N`).
- A new ADR in `docs/adr/` (next sequential number) documenting the narrow D-010 exception, per
  Finding 2.
- Updating `spec/auth-service/design.md` §4b (O1 resolved, moved into §4a as a new LOCKED decision)
  and §4c (VERBATIM config-key confirmation/update once Phase 5 fixes the exact keys).
- Updating `spec/auth-service/package.md` §11 (Q1 marked resolved, struck-through + "Resolved
  (date)," per the existing Q6 precedent).
- Updating `spec/auth-service/agents.md`'s Security section (the "no AWS SDK secret-retrieval in
  application code (D-010)" line) to reflect the narrow, named exception — not a blanket removal.

**Out:**
- Any file under `services/auth/src/main/java/com/themistra/auth/mfa/` (including
  `package-info.java`) — task statement is explicit: decision precedes code.
- `V1__auth_baseline_schema.sql` — LOCKED (L1), not edited even though it now turns out to already be
  consistent with the chosen option (Finding 5).
- Seed entropy, recovery-code format, and MFA-failure/lockout-counter integration — deferred per
  Findings 11–13 with owners assigned above, not decided or specified further here.
- Any other OPEN decision (O2–O5) or Open Question (Q2–Q6) in `design.md`/`package.md`.

## Business Rules

- **R22** (corrected framing, Finding 7). `POST /accounts/me/mfa/totp` must generate, encrypt,
  persist, and expose a TOTP seed via provisioning URI. This task determines only the encryption
  mechanism (now: option B); endpoint behavior itself remains task #16/#19's scope.

## Locked Decisions

- **L6.** TOTP algorithm/format — unaffected by this task; constrains only what is encrypted.
- **L13.** Secrets discipline — now narrowly amended (not removed) to permit the specific KMS calls
  option (B) requires; the amendment text itself is one of this task's outputs.
- **D-010** (`auth-decisions.md`) — narrowly amended for the same reason; the ADR (Finding 2) is the
  formal record of that amendment.

## Dependencies

- `spec/auth-service/design.md` §4c placeholder `themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}`
  — retained per the Finding 4 correction, confirmed/finalized in Phase 5.
- `mfa_enrollments.secret_encrypted BYTEA` (V1, immutable) — storage target; column shape (`BYTEA`)
  already accommodates an envelope-encrypted blob, no migration needed for this task.
- `docs/adr/0001-record-architecture-decisions.md`, `0002-maven-for-java-builds.md` — format/numbering
  precedent for the new ADR (next: `0003`).
- `services/auth/docs/architecture/auth-decisions.md` D-001–D-014 — format precedent for the new
  `D-01N` entry.

## Inputs

- The three O1 options and their trade-offs (`design.md` §4b O1), as corrected by Phase 3 Finding 1.
- The two human decisions recorded above (option B; full decision depth; guardrail override).

## Outputs

- New `docs/adr/0003-<slug>.md`.
- New `D-01N` entry in `auth-decisions.md`.
- Updated `spec/auth-service/design.md` (§4a new LOCKED entry, §4b O1 resolved, §4c config-key
  confirmation).
- Updated `spec/auth-service/package.md` §11 (Q1 resolved).
- Updated `spec/auth-service/agents.md` (Security section, D-010 line narrowed).

## State Changes

None. No database, no runtime state — this task changes specification/documentation/decision-record
artifacts only.

## Files to Create

- `docs/adr/0003-<slug-tbd-in-phase-5>.md`

## Files to Modify

- `spec/auth-service/design.md`
- `spec/auth-service/package.md`
- `spec/auth-service/agents.md`
- `services/auth/docs/architecture/auth-decisions.md`

(Expanded from the Phase 2 TIB's three-file list, per Finding 2's ADR/agents.md requirement.)

## Files NOT to Modify

- Anything under `services/auth/src/main/java/com/themistra/auth/mfa/`.
- `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` (LOCKED, L1).
- Any other section of `design.md`/`package.md` beyond O1/§4a/§4c and Q1 respectively.
- `docs/adr/0001-*.md`, `0002-*.md` (existing ADRs, untouched).

## Acceptance Criteria

- **AC1.** `auth-decisions.md` gains a `D-01N` entry (Context/Selected/Trade-offs/Reference-influence
  shape) recording option (B) with full rationale, referencing the new ADR.
- **AC2.** A new ADR exists documenting the narrow D-010 exception: scope (KMS `GenerateDataKey` +
  `Decrypt` only, inside `MfaSeedEncryption`), what remains forbidden (no other AWS SDK use
  elsewhere in the service), and why (Phase 3 Finding 2's escalation requirement).
- **AC3.** `design.md` §4a gains a new LOCKED decision for the TOTP-seed encryption mechanism; §4b O1
  is removed/marked resolved with a pointer to it; §4c's `seed-kek-arn` config line is confirmed or
  corrected.
- **AC4.** `package.md` §11 Q1 is struck through with a "Resolved (date)" note, per the Q6 precedent.
- **AC5.** `agents.md`'s Security section narrows the D-010 line to name the specific exception
  instead of a blanket "no AWS SDK" statement.
- **AC6.** The recorded decision specifies: ciphertext byte layout, nonce/tag storage, KEK-rotation
  handling (via KMS's own key-rotation semantics, confirmed explicitly rather than assumed), the
  final `@ConfigurationProperties` key(s), and the local-dev (`local` profile) key story — all fixed
  concretely in Phase 5, not left as placeholders.
- **AC7.** No file under `mfa/` is created or modified.

## Required Tests

None — no code is authorized in T02. Non-binding note for the record (Finding 8): the eventual task
#16/#22 implementation should include a security-regression test asserting `secret_encrypted` never
contains the raw seed, that decrypt round-trips correctly, and that a wrong/rotated KEK fails in the
expected way — carried into the new decision entry as guidance for that later task, not required here.

## Constraints

- **Security:** the amendment to D-010/L13 must be narrow and named — a specific client, specific
  KMS API calls, specific class (`MfaSeedEncryption`) — not a general re-opening of "AWS SDK use is
  now allowed."
- **Process:** every spec/decision-record edit in this task must be traceable to one of the human
  decisions recorded above; no additional unrelated wording changes to `design.md`/`package.md`/
  `agents.md` beyond what AC1–AC5 require.
- **Module boundaries / transactions / null-handling:** N/A — no code.

## Open Questions

**No blockers remain for T02 itself.** Three items are explicitly deferred, each with an owner,
none of them actionable in this task:

- **Deferred to task #16** (`TotpGenerator`): fix TOTP seed entropy/byte length (Finding 11).
- **Deferred to task #18** (MFA service): fix recovery-code length/encoding (Finding 12).
- **Deferred to task #13 and/or #20** (login failure tracking / SAS MFA step integration): decide
  whether failed TOTP/recovery-code attempts contribute to the brute-force lockout counter, or use a
  separate MFA-specific rate limit (Finding 13 — flagged High by Phase 3, but not this task's scope
  to resolve).

The four contracts in this task's generated header remain confirmed non-dependencies of T02 (same
generator-artifact pattern documented in `AI_CONTEXT_ANALYSIS.md`) — a seed-encryption mechanism
decision has no API, event, or token-claims surface.
