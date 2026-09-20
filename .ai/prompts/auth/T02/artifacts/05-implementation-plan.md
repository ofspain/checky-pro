# auth · T02 · Phase 5 — Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN). No code — this task has none. This
plan fixes the exact content Phase 6 writes into each authorized file, tracing every item to the
frozen brief's Files sections and AC6.

## Technical decision fixed in this phase (AC6)

**Ciphertext envelope layout** (for `mfa_enrollments.secret_encrypted BYTEA`):

| Bytes | Field | Notes |
|---|---|---|
| `[0]` | format version | `0x01` = `KMS_ENVELOPE_AESGCM256` (real KMS path). `0x00` = `LOCAL_DEV_AESGCM256` (local-profile fallback, see below). Reserved so a future scheme change never needs a data migration to disambiguate old rows. |
| `[1..2]` | wrapped-data-key length `N`, big-endian uint16 | `N = 0` for version `0x00` (no wrapped key — see below). |
| `[3..3+N)` | wrapped data key | KMS `CiphertextBlob` from `GenerateDataKey`, for version `0x01`. Absent (`N=0`) for version `0x00`. |
| next 12 bytes | AES-GCM nonce | 96-bit, `SecureRandom`, unique per encryption. |
| remainder | AES-256-GCM ciphertext + 16-byte tag | Standard JCA `Cipher`/GCM output — tag is appended to ciphertext by the platform crypto API, not stored separately. |

**KEK rotation:** handled entirely by AWS KMS's built-in automatic annual rotation on the CMK
(enabled at the infra/CDK level — out of this task's files, since infra CDK isn't part of
`services/auth`). KMS transparently retains prior key material and decrypts old `CiphertextBlob`s
without the application tracking key versions. The format-version byte exists only for a future
*scheme* change (e.g. away from option B entirely), not per-rotation bookkeeping. No app-level
rotation logic is needed or planned.

**Config keys:** the existing `design.md` §4c placeholder is retained as-is —
`themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}` — because it already maps directly to the KMS
`KeyId` parameter both `GenerateDataKey` and `Decrypt` accept (ARN or alias). Confirms and closes the
Phase 4/Finding-4 correction: no rename needed. Binding: a validated `@ConfigurationProperties`
record; startup fails fast if blank outside the `local` profile (L13's existing pattern, applied here
rather than invented new).

**Local-dev story:** when the active profile is `local` and `seed-kek-arn` is blank, the future
`MfaSeedEncryption` (task #16) is specified to bypass KMS entirely and use a fixed, clearly-labeled
local-only AES-256 constant, producing version-`0x00` envelopes. In `dev`/`staging`/`prod`, the
config-binding startup guard refuses a blank `seed-kek-arn`, so only version-`0x01` (real KMS)
envelopes are ever produced there. This is a specification for task #16 to implement — T02 fixes the
decision, not the code.

## Files to Create

- **`docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md`** — following the `0001`/`0002`
  format (`# ADR-0003: <title>` · `- **Status:** accepted · 2026-07-22` · `## Decision` · `## Context`
  · `## Consequences`).
  - **Decision:** A single, narrowly-scoped AWS KMS client call (`GenerateDataKey` at enrollment,
    `Decrypt` at read time) is permitted inside `com.themistra.auth.mfa.MfaSeedEncryption` only. No
    other AWS SDK use is authorized anywhere else in the service; D-010's general prohibition stands
    everywhere else.
  - **Context:** cites Q1/O1, D-010, and this task (T02), summarizing why local-only and
    Crypto-Service-delegation were not selected (per Phase 4's dispositions).
  - **Consequences:** IRSA role for `auth-service` gains `kms:GenerateDataKey` + `kms:Decrypt` scoped
    to the single TOTP-seed CMK ARN only (no wildcard); the envelope layout table above; KMS
    automatic key rotation is an infra/CDK follow-up, not part of this ADR's code scope.

## Files to Modify

- **`services/auth/docs/architecture/auth-decisions.md`** — append `D-015` (next after D-014), in the
  established shape:
  - **Context:** Q1 blocker, target-design.md's original AES-GCM/KMS-envelope proposal vs. D-010.
  - **Selected:** Option (B) — narrow KMS envelope call, per ADR-0003.
  - **Trade-offs:** stronger custody than a local-only key (option A) without grafting an unrelated
    Crypto Service into an app-secret role it doesn't own (option C, corrected per Phase 3 Finding 1);
    costs a per-enrollment and per-verification KMS network call plus the narrow D-010 exception.
  - **Reference influence/verdict:** N/A (Q1 has no reference-project precedent).
  - Note that V1's `secret_encrypted` column comment ("AES-GCM, KMS-enveloped data key") is *not*
    stale under this decision (Phase 4 Finding 5).

- **`spec/auth-service/design.md`**
  - §4a: add a new LOCKED decision (next id after L13, i.e. **L14**): "TOTP seed encryption. AES-256-GCM,
    KMS-enveloped data key (KMS `GenerateDataKey`/`Decrypt`, single CMK, scoped IRSA). Envelope byte
    layout fixed in `auth-decisions.md` D-015 / ADR-0003. Implemented by `MfaSeedEncryption` (task
    #16)." Cross-reference so implementers aren't left re-deriving the byte layout from this doc alone.
  - §4b: remove O1 (resolved), replacing with a one-line pointer: "~~O1~~ Resolved — see L14 / D-015 /
    ADR-0003."
  - §4c: confirm the existing `themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}` line unchanged
    (per the Finding-4 correction above) — no edit needed here beyond removing the now-resolved
    "(implementation depends on O1)" comment on that line.

- **`spec/auth-service/package.md`**
  - §11: strike Q1, append `**Resolved (2026-07-22):** Option (B), narrow KMS envelope call. See
    `design.md` L14, `auth-decisions.md` D-015, `docs/adr/0003-...md`.` — matching the existing Q6
    precedent exactly.

- **`spec/auth-service/agents.md`**
  - Security section, the line "no AWS SDK secret-retrieval in application code (D-010)" is narrowed
    to: "no AWS SDK code in application code, **except** a single scoped KMS `GenerateDataKey`/
    `Decrypt` call inside `mfa.MfaSeedEncryption` for TOTP-seed envelope encryption (D-010 exception,
    see ADR-0003)." No other wording in `agents.md` changes.

## Public methods (signatures)

N/A — no code in this task. The closest equivalent contract this task fixes is the ciphertext
envelope layout table above, which is what task #16's `MfaSeedEncryption` must implement against.

## Private methods

N/A — no code in this task.

## Entities used

N/A — no code in this task. `mfa_enrollments.secret_encrypted` (existing, V1, immutable) is the
storage target the envelope layout is designed to fit into; no entity class exists yet (task #17).

## Repositories used

N/A — no code in this task.

## Services used

N/A — no code in this task.

## Unit/integration tests required

None — matches the frozen brief's Required Tests section. The non-binding forward note for task
#16/#22's future security-regression test (Finding 8) is already carried in the planned D-015 entry
implicitly via the ADR reference; no test is written or planned here.

## Execution order

1. Draft `docs/adr/0003-...md` first (the other three files reference it).
2. Append `D-015` to `auth-decisions.md` (references the ADR).
3. Edit `spec/auth-service/design.md` (§4a new L14, §4b O1 resolved-pointer, §4c comment cleanup) —
   references D-015/ADR-0003.
4. Edit `spec/auth-service/package.md` §11 (Q1 resolved) — references the same.
5. Edit `spec/auth-service/agents.md` Security section — narrows the D-010 line.
6. Self-review (Phase 7): re-read all five files together for cross-reference consistency (ADR number,
   D-015 id, L14 id all match everywhere they're cited).
