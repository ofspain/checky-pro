# ADR-0003: Narrow KMS exception for TOTP-seed encryption

- **Status:** accepted · 2026-07-22 · narrows D-010 for one specific, scoped case

## Decision

A single, narrowly-scoped AWS KMS client call is permitted inside
`com.themistra.auth.mfa.MfaSeedEncryption` (auth-service, task #16) and nowhere else in the service:
`GenerateDataKey` at TOTP enrollment time, `Decrypt` at read time. No other AWS SDK use is authorized
anywhere else in `services/auth`; D-010's general prohibition on AWS SDK code in the service continues
to apply to everything outside this one class.

The KMS calls implement AES-256-GCM envelope encryption of TOTP seeds. Ciphertext is stored as a
single versioned binary envelope in `mfa_enrollments.secret_encrypted BYTEA`:

| Bytes | Field | Notes |
|---|---|---|
| `[0]` | format version | `0x01` = `KMS_ENVELOPE_AESGCM256`. `0x00` = `LOCAL_DEV_AESGCM256` (local-profile only, no KMS call). |
| `[1..2]` | wrapped-data-key length `N`, big-endian uint16 | `N = 0` for version `0x00`. |
| `[3..3+N)` | wrapped data key | KMS `CiphertextBlob` from `GenerateDataKey` (version `0x01` only). |
| next 12 bytes | AES-GCM nonce | 96-bit, `SecureRandom`, unique per encryption. |
| remainder | AES-256-GCM ciphertext + 16-byte tag | Tag appended to ciphertext by the platform JCA GCM implementation. |

## Context

`target-design.md` specifies AES-GCM encryption of TOTP seeds with a KMS-enveloped data key, but
D-010 forbids AWS SDK code in the service (the reference project's disabled, secret-printing
hand-rolled Secrets Manager client became a platform-wide control). This left an open blocker
(`spec/auth-service/package.md` §11 Q1 / `design.md` §4b O1) for the MFA implementation task.

Two alternatives were considered and rejected during T02's Phase 3/4 review:
- **Local-only AES-GCM with an ESO-injected symmetric key (option A).** Fully respects D-010 as
  written, but is weaker custody than KMS envelope encryption — the plaintext key material lives in
  the pod's environment for the process lifetime rather than being unwrapped per-operation.
- **Delegate to the Crypto Service (option C).** The Crypto Service is real (`spec/crypto-service/`,
  `ARCHITECTURE.md` §3.4) but its charter is blockchain attestation / receipt signing — no contract
  exists for general application-secret encryption, and building one would graft an unrelated
  responsibility onto a service that doesn't own it today.

The author selected option B: accept a deliberately narrow, named exception to D-010 rather than
either of the above.

## Consequences

- The `auth-service` IRSA role gains `kms:GenerateDataKey` and `kms:Decrypt`, scoped to the single
  TOTP-seed CMK ARN only — no wildcard resource, no other KMS actions.
- KMS's own automatic annual key rotation (enabled on the CMK at the infra/CDK level, outside this
  ADR's code scope) handles KEK rotation transparently; the application never tracks key versions.
  The format-version byte above exists only for a future *scheme* change, not per-rotation bookkeeping.
- `mfa_enrollments.secret_encrypted`'s existing V1 column comment ("AES-GCM, KMS-enveloped data key")
  turns out to already describe this outcome accurately — no migration or comment correction needed.
- Recorded in `services/auth/docs/architecture/auth-decisions.md` D-025 and
  `spec/auth-service/design.md` L14.
