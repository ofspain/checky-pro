# crypto · T21 · Phase 1 — Specification Extraction

## Business Rules

- **R20.** A `POST /internal/v1/attest` call for a transaction that has passed quorum, finality, and
  screening requests a signature from AWS KMS and returns `{signature, kmsKeyId, signedAt,
  outcome: "SIGNED"}`; the key material never leaves KMS.
- **R21.** If the counterparty address for an attest request is a sanctioned/OFAC hit under screening,
  the system returns `{outcome: "BLOCKED", reason}`, places the item in the compliance queue, and
  produces no signature.
- **R23.** If an attest request references a transaction that has not met quorum and finality, the
  system refuses to sign and returns an error — never a signature.

## Locked Decisions

Widened beyond the header's own scoped `L11` — `L10` and `L12` are the two other LOCKED decisions this
task's own task statement directly enacts (`design.md`'s package map itself annotates
`AttestController.java` with `L10`; `agents.md`'s verification checklist pairs L10+L12 for exactly this
gate), so excluding them would leave the task's central behavior undocumented here.

- **L10. Attestation only at proven finality.** `POST /attest` refuses to sign unless the referenced tx
  passed quorum + finality (+ screening, L12). Signing from anything less is impossible by construction.
- **L11. KMS-only signing, single path** (this task's header-scoped decision — already fully implemented
  by T20's `KmsSigner`; this task's own obligation is to call it, not to re-implement or bypass it, and
  to keep the sign path itself free of host-only assumptions, matching T20's own Nitro-Enclave-portability
  discipline).
- **L12. Screening gates attestation, fail-closed.** Before signing, the counterparty address is
  screened; an OFAC/sanctioned hit → `BLOCKED`, compliance queue, no signature. If the screening API is
  unreachable, attest fails closed (no signature) unless the author overrides via Q3 (`package.md` §11 —
  unanswered, no override exists anywhere in this codebase, confirmed in T19's own Phase 0; fail-closed
  remains fully in force).

## Files involved

**Existing, to read/extend:**
- `quorum/QuorumDecision.java`, `quorum/QuorumDecisionRepository.java` (package-private, one query
  method: `findByChainAndTxHashAndFactType`), `quorum/QuorumDecisionService.java` (write-only public API
  today — no read method exists; this task needs one).
- `observation/FactType.java` (`EXISTENCE, AMOUNT, TOKEN, CONFIRMATIONS, FINALITY`).
- `watch/ChainCursor.java`, `watch/ChainCursorRepository.java` (package-private, only
  `findByWatchId`; no `(chain, txHash)` lookup exists — this task needs one), `watch/Watch.java`.
- `screening/ScreeningClient.java` (T19, complete) — `screen(chain, address, txHash)` →
  `ScreeningOutcome`.
- `attest/KmsSigner.java`, `attest/SignatureResult.java` (T20, complete) — `sign(byte[] digestSha256)` →
  `SignatureResult` (package-private, explicitly anticipates this task's `AttestationService` as its only
  consumer).
- `attest/KmsSignerArchitectureTest.java` — the existing ArchUnit rule this task's new files must not
  violate (they may depend on `KmsSigner`, since they live in the same `attest` package; they must never
  depend on the KMS SDK directly).
- `common/ResourceServerConfig.java` (already covers `/internal/v1/**`, no change needed),
  `common/ApiExceptionHandler.java` (generic/validation errors only, never feature-module-aware),
  `common/PublicEndpoints.java` (attest must NOT be added here).
- `watch/WatchController.java`, a matching `WatchExceptionHandler`-style class (pattern precedent for
  this task's own `AttestController` + its own exception-handling advice).
- `resources/db/migration/V1__chain_baseline.sql` (`attestations` table, frozen, used as shipped),
  `V2__crypto_app_role_and_grants.sql` (`crypto_app` already granted `INSERT, SELECT` on `attestations`
  — no new migration expected for basic persistence).

**New, expected by `design.md`'s own package map (`attest/`):**
- `attest/AttestController.java` — `POST /internal/v1/attest` (L10).
- `attest/AttestationService.java` — the quorum+finality+screening gate → sign orchestration.
- `attest/Attestation.java` / `attest/AttestationRepository.java` — persistence for every outcome (R20).

## Dependencies

`QuorumDecisionService`/`QuorumDecisionRepository` (needs a new public read seam — Phase 2 decision);
`ChainCursorRepository` (needs a new `(chain, txHash)` query — Phase 2 decision); `ScreeningClient`;
`KmsSigner`; `common.ClockConfig`'s `Clock`; the new `AttestationRepository`; no outbox/event dependency
(no `chain.*` event is defined for attestation outcomes in `design.md`'s `EventTopics` mapping).

## Acceptance Criteria

1. **AC1 (R20/L10).** A valid attest request for a `(chain, txHash)` with every required fact `AGREED`
   and screening `CLEARED` returns `200 {signature, kmsKeyId, signedAt, outcome: "SIGNED"}`, and the
   digest actually signed is the SHA-256 digest the request carried (hex-decoded).
2. **AC2 (R21/L12).** A valid attest request whose counterparty screens `BLOCKED` returns
   `200 {outcome: "BLOCKED", reason}` and produces no signature — regardless of quorum/finality state.
3. **AC3 (R23/L10).** An attest request for a `(chain, txHash)` missing any required `AGREED` fact or
   unmet finality returns `409` (`problem+json`) and produces no signature.
4. **AC4 (L12, fail-closed).** A `screen(...)` call that returns `ERROR` or throws is treated identically
   to a screening failure — no signature is produced (exact HTTP shape is a Phase 2 design decision, per
   Phase 0's own disclosed gap).
5. **AC5 (R20, audit trail).** Every attest request — `SIGNED`, `BLOCKED`, or `REFUSED` — persists exactly
   one `Attestation` row to `chain.attestations` before/as part of returning, with the correct `outcome`,
   `receipt_digest`, `chain`, `tx_hash`, and (for `SIGNED` only) `kms_key_id`/`signed_at`.
6. **AC6 (L11).** `AttestationService` calls `KmsSigner.sign(...)` and nothing else in this task's new
   code ever touches the KMS SDK directly — enforced by the already-existing `KmsSignerArchitectureTest`.
7. **AC7 (security, R22 unmodified).** `POST /internal/v1/attest` requires `internal.crypto:write` scope
   — already enforced by the existing `ResourceServerConfig` path rule, verified rather than reconfigured.

## Tests required

- **Named tests (`package.md` §8):**
  - `shouldReturnKmsSignatureFromAttestForValidDigest` → R20.
  - `shouldReturnBlockedFromAttestOnSanctionedCounterparty` → R21 (T19 built the fail-closed floor this
    test's full realization depends on; this task owns the test in full for the first time).
  - `shouldRejectAttestWhenQuorumOrFinalityNotMet` → R23.
- **Boundary/unit tests implied by scope:**
  - Every required fact type `AGREED` individually toggled to not-`AGREED`/absent → `409`, not `200`
    (exact fact-type set is a Phase 2 decision, per Phase 0's disclosed gap).
  - Screening `ERROR`/exception → fail-closed (AC4).
  - `Attestation` persisted for all three outcome paths, including the `409`/`REFUSED` path if that path
    itself is deemed to warrant a persisted row (R20 says "persist every outcome," and `attestations`'
    own CHECK constraint already anticipates `REFUSED` as a valid outcome value — so yes, a `409` refusal
    should still persist a `REFUSED` row, not skip persistence).
  - `AttestController` requires `internal.crypto:write` (integration test, mirrors an existing
    `ResourceServerConfigIntegrationTest`-style precedent if one exists for `/internal/v1/watches`).
  - `KmsSignerArchitectureTest`'s existing rule still passes with the new files added (regression check,
    not a new test).

## Open Questions

Both genuine, disclosed in Phase 0, requiring explicit Phase 2 design proposals (not spec-mandated
answers) rather than blocking this task outright:

- **Which fact types must be `AGREED` before signing is not enumerated anywhere in the spec.**
  `requirements.md`/`design.md`/`tasks.md` all say "AGREED quorum" generically. Not a hard blocker — a
  Phase 2 proposal (informed by `Watcher`'s own fact-evaluation set: EXISTENCE, AMOUNT, TOKEN,
  CONFIRMATIONS, FINALITY) will be submitted for Phase 3/4 review rather than assumed silently.
- **How the counterparty address for screening is determined is not specified** — no address field
  exists on the attest request; the only plausible source is `ChainCursor`'s stored
  `fromAddress`/`toAddress` for the matching watch, but which of the two is "the counterparty" is
  unstated. Not a hard blocker — a Phase 2 proposal will be submitted for review.
