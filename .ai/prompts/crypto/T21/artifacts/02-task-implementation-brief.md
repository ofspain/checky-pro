# crypto · T21 · Phase 2 — Task Implementation Brief

## Task

Implement `POST /internal/v1/attest`: `AttestController` + `AttestationService` gate signing on
`AGREED` quorum (EXISTENCE, AMOUNT, TOKEN, FINALITY) + `CLEARED` screening, then call `KmsSigner`;
`BLOCKED` on a sanctioned counterparty; `409` when any gate is unmet; every outcome persisted to
`chain.attestations`.

## Purpose

Wire together the three independently-built gates (quorum/finality from T06-T18, screening from T19,
KMS signing from T20) behind the platform's single most consequential endpoint — the one that actually
produces a Themistra attestation. Concentrates R20/R21/R23/L10/L12 into one auditable orchestration.

## Scope

**In:**
- `attest.AttestOutcome` — enum `{SIGNED, BLOCKED, REFUSED}`, matching `chain.attestations.outcome`'s
  own `chk_attest_outcome` CHECK constraint exactly (verified: `V1__chain_baseline.sql`'s literal values
  are already uppercase, so — like `screening.ScreeningOutcome`'s own converter, unlike
  `observation.FactType`'s — no case transformation needed in its DB converter).
- `attest.Attestation` — JPA entity mapping `chain.attestations` exactly as shipped by `V1`: `chain`,
  `txHash`, `receiptDigest` (the hex string, `CHAR(64)`), `outcome`, `kmsKeyId` (nullable — only set for
  `SIGNED`), `signedAt` (nullable — only set for `SIGNED`), `createdAt`. No setters, public static
  `create(...)`.
- `attest.AttestationRepository` — package-private `JpaRepository<Attestation, Long>`.
- `attest.AttestRequest` — hand-written record (no `contracts/api/crypto-internal.yaml` exists anywhere
  in this repo, confirmed absent, matching every prior task's disclosed precedent):
  `record AttestRequest(@NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String receiptDigestSha256,
  @NotBlank String chain, @NotBlank String txHash)`. The `@Pattern` constraint routes a malformed hex
  digest through the existing `MethodArgumentNotValidException` → 400 path (`ApiExceptionHandler`,
  unmodified) instead of a hex-decode `IllegalArgumentException` falling into its generic 500 catch-all.
- `attest.AttestResponse` — hand-written record covering both `200` shapes:
  `record AttestResponse(String signature, String kmsKeyId, Instant signedAt, String outcome,
  String reason)`, with `signed(...)`/`blocked(String reason)` static factories leaving the other
  fields `null`. `REFUSED` never produces an `AttestResponse` instance — it is a thrown exception mapped
  to `409 problem+json` instead, per `design.md`'s own wire shape (no `200 {outcome: "REFUSED"}` shape
  exists there).
- `attest.AttestationRefusedException` (`RuntimeException`) — thrown for every gate failure that is
  **not** an active sanctions hit: any required fact not `AGREED`/absent, finality not met, screening
  returning `ERROR`, or a `screen(...)` call throwing (L12's fail-closed contract already requires
  treating a thrown exception identically to `ERROR` — this task's own catch is therefore a **single**
  `catch (RuntimeException)` around the `screen(...)` call, converting anything into this same exception,
  not a family of distinct catches). `AttestationService` persists an `Attestation(outcome=REFUSED)` row
  before letting this exception propagate.
- `attest.AttestExceptionHandler` — `@RestControllerAdvice @Order(HIGHEST_PRECEDENCE)`, mirrors
  `watch.WatchExceptionHandler`'s exact shape: `AttestationRefusedException` → `409` `ProblemDetail`
  (title "Attestation refused", detail = exception message).
- `attest.AttestController` — `POST /internal/v1/attest`, no security annotations of its own (already
  covered by `ResourceServerConfig`'s `/internal/v1/**` rule).
- `attest.AttestationService.attest(AttestRequest request)` → `AttestResponse`:
  1. For each of `EXISTENCE`, `AMOUNT`, `TOKEN`, `FINALITY` (a fixed list — **`CONFIRMATIONS` is
     deliberately excluded**, proposed here for Phase 3/4 review since the spec never enumerates the
     required set explicitly: `CONFIRMATIONS` is a progress count superseded by `FINALITY` once reached,
     not itself a payment-correctness fact the way EXISTENCE/AMOUNT/TOKEN are), call the new
     `QuorumDecisionService.isAgreed(chain, txHash, factType)` seam; any `false` →
     `AttestationRefusedException`.
  2. Look up the watch's counterparty address via the new `WatchService.findChainCursor(chain, txHash)`
     seam; if absent → `AttestationRefusedException`. **Screen `ChainCursor.fromAddress()`** — proposed
     here for Phase 3/4 review, since the spec never states which side is "the counterparty": the
     `toAddress` is the platform's own already-known, already-approved invoice/watch address; the
     `fromAddress` is the external, unknown paying party — the actual AML counterparty.
  3. Call `ScreeningClient.screen(chain, fromAddress, txHash)`. `BLOCKED` → persist
     `Attestation(outcome=BLOCKED)`, return `AttestResponse.blocked(reason)` (a fixed, generic reason
     string — the `ScreeningResult` audit row, not this response, carries the vendor detail once a real
     vendor exists). `ERROR`, or any thrown exception → `AttestationRefusedException` (see above).
     `CLEARED` → proceed.
  4. Hex-decode `receiptDigestSha256` (`HexFormat.of().parseHex(...)` — 64 hex chars ⇒ 32 bytes, already
     guaranteed by `AttestRequest`'s own `@Pattern`), call `KmsSigner.sign(digestBytes)`.
  5. Persist `Attestation(outcome=SIGNED, kmsKeyId, signedAt)`, return `AttestResponse.signed(...)`.
  - No outer `@Transactional`: each repository `save()` is already its own transaction (T19/T20
    precedent); the KMS/screening network calls must never hold a DB connection open across them.
- **New public seams on existing modules** (no existing precedent for cross-module repository reads —
  both follow the established "service is the module's public API, repository stays package-private"
  convention):
  - `quorum.QuorumDecisionService.isAgreed(String chain, String txHash, FactType factType) -> boolean`.
  - `watch.WatchService.findChainCursor(String chain, String txHash) -> Optional<ChainCursor>` (backed
    by a new package-private `ChainCursorRepository.findByChainAndTxHash(chain, txHash)`).
- `KmsSignerArchitectureTest` regression check: `AttestationService`/`AttestController` depend on
  `KmsSigner` (same package, already permitted) and never touch the KMS SDK directly — no rule change
  needed, verified by re-running the existing test.

**Out:**
- The compliance-queue mechanism itself (R21 says "place the item in the compliance queue") — no queue
  table/mechanism exists anywhere in this spec package; the persisted `Attestation(BLOCKED)` row plus the
  existing `ScreeningResult(BLOCKED)` row (T19) together form the audit trail a future queue could be
  built from. Building the queue itself is not named by any task in `tasks.md`.
- `VerificationKeysController` / the well-known verification-keys endpoint (task 22).
- Any new `chain.*` event — no event schema covers attestation outcomes in `design.md`'s `EventTopics`
  mapping.
- Modifying `KmsSigner`, `ScreeningClient`, `FailClosedScreeningClient`, `QuorumDecisionService.evaluate`,
  `Watcher`, or any `V1`-`V9` migration.
- Re-litigating `KmsSignerArchitectureTest`'s rule shape — this task's new files are already compliant
  with it as written.

## Business Rules

- **R20.** A valid attest request signs via KMS and returns `{signature, kmsKeyId, signedAt,
  outcome: "SIGNED"}`.
- **R21.** A sanctioned-counterparty attest request returns `{outcome: "BLOCKED", reason}`, no signature.
- **R23.** An attest request for a transaction that hasn't met quorum and finality returns `409`, never
  a signature.

## Locked Decisions

- **L10.** `/attest` refuses to sign unless quorum + finality (+ screening, L12) passed — impossible by
  construction (enacted by `AttestationService`'s ordered gate sequence, not a runtime toggle).
- **L11.** KMS-only signing stays single-path — `AttestationService` calls `KmsSigner.sign(...)` only;
  no other new code in this task touches the KMS SDK.
- **L12.** Screening gates attestation, fail-closed — `ERROR`/exception from `screen(...)` refuses to
  sign, identically to a `BLOCKED`-adjacent failure (modeled here as `REFUSED`, not `BLOCKED`, since only
  an actual confirmed sanctions hit is `BLOCKED` per R21's own wording).

## Dependencies

`quorum.QuorumDecisionService` (new `isAgreed` method), `observation.FactType`,
`watch.WatchService` (new `findChainCursor` method), `watch.ChainCursor`, `screening.ScreeningClient`,
`screening.ScreeningOutcome`, `attest.KmsSigner`, `attest.SignatureResult`, `common.ClockConfig`'s
`Clock`, the new `AttestationRepository`.

## Inputs

`AttestRequest{receiptDigestSha256 (64 hex chars), chain, txHash}`.

## Outputs

`AttestResponse` (200, `SIGNED` or `BLOCKED` shape) or a `409 problem+json` (`REFUSED`, via
`AttestationRefusedException`).

## State Changes

- `INSERT` into `chain.attestations` (already granted, T02/V2) — exactly one row per request, every
  outcome.
- No other table touched by this task's new code.

## Files to Create

- `attest/AttestOutcome.java`
- `attest/Attestation.java`
- `attest/AttestationRepository.java`
- `attest/AttestRequest.java`
- `attest/AttestResponse.java`
- `attest/AttestationRefusedException.java`
- `attest/AttestExceptionHandler.java`
- `attest/AttestationService.java`
- `attest/AttestController.java`

## Files to Modify

- `quorum/QuorumDecisionService.java` — add `isAgreed(chain, txHash, factType)`.
- `watch/WatchService.java` — add `findChainCursor(chain, txHash)`.
- `watch/ChainCursorRepository.java` — add `findByChainAndTxHash(chain, txHash)` (package-private,
  unchanged visibility).

## Files NOT to Modify

- `attest/KmsSigner.java`, `attest/SignatureResult.java`, `attest/KmsSignerArchitectureTest.java`.
- `screening/**` (T19, frozen).
- `quorum/QuorumDecisionService.evaluate(...)`, `quorum/QuorumDecision.java`, `quorum/QuorumDecisionRepository.java`.
- `watch/Watcher.java`, `watch/WatchController.java`, `watch/Watch.java`.
- `V1`-`V9` migrations.
- `common/ResourceServerConfig.java`, `common/PublicEndpoints.java`, `common/ApiExceptionHandler.java`.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R20/L10).** All four required facts `AGREED`, screening `CLEARED` → `200 SIGNED`, digest
  actually sent to KMS matches the hex-decoded request digest exactly.
- **AC2 (R21/L12).** Screening `BLOCKED` → `200 BLOCKED` with a reason, no `KmsSigner.sign` call, no
  `Attestation(SIGNED)` row — regardless of quorum/finality state.
- **AC3 (R23/L10).** Any of EXISTENCE/AMOUNT/TOKEN/FINALITY not `AGREED` → `409`, no `KmsSigner.sign`
  call, no screening call (gate order: quorum/finality before screening).
- **AC4 (L12, fail-closed).** Screening `ERROR` or a thrown exception → `409` (`REFUSED`), not `200
  BLOCKED` and not a signature.
- **AC5 (R20, audit trail).** Exactly one `Attestation` row persisted per request, for all three outcomes,
  with the correct `outcome`/`receiptDigest`/`chain`/`txHash`, and `kmsKeyId`/`signedAt` only on `SIGNED`.
- **AC6 (L11).** `KmsSignerArchitectureTest` still passes unmodified with the new files present.
- **AC7 (module boundary).** `QuorumDecisionRepository`/`ChainCursorRepository` stay package-private;
  cross-module reads go only through `QuorumDecisionService`/`WatchService`.
- **AC8 (null handling).** A `(chain, txHash)` with no matching `ChainCursor` at all → `409` (`REFUSED`),
  not a `NullPointerException` or 500.

## Required Tests

- `shouldReturnKmsSignatureFromAttestForValidDigest` (named, R20/AC1).
- `shouldReturnBlockedFromAttestOnSanctionedCounterparty` (named, R21/AC2) — this task's first full,
  end-to-end ownership of this test (T19 only proved the fail-closed floor).
- `shouldRejectAttestWhenQuorumOrFinalityNotMet` (named, R23/AC3) — parameterized across each of the
  four required fact types individually missing/not-`AGREED`.
- Screening `ERROR`/thrown-exception fail-closed test (AC4).
- One `Attestation` row per outcome, field-correctness test for each of the three outcomes (AC5).
- `KmsSignerArchitectureTest` regression run (AC6).
- Missing-`ChainCursor` → `409`, not a crash (AC8).
- `AttestRequest` validation: malformed hex digest → `400`, not `500` (via `@Pattern`).
- Integration test: `POST /internal/v1/attest` requires `internal.crypto:write` scope (mirrors an
  existing `/internal/v1/watches` scope-enforcement test, if a directly analogous one exists — otherwise
  a new one following `ResourceServerConfig`'s own established test style).

## Constraints

- **Performance:** one synchronous screening call + one synchronous KMS call per request; no polling, no
  retries added by this task beyond each collaborator's own defaults.
- **Security:** no new secret; digest/signature never logged (mirrors `KmsSigner`'s own no-`Logger`-field
  discipline — `AttestationService` should avoid logging the raw digest or signature at INFO/WARN, though
  a `Logger` field itself is acceptable here since this class doesn't touch the KMS SDK, unlike
  `KmsSigner`; if logging is added, only log at a debug/audit level scoped to `(chain, txHash, outcome)`).
- **Thread-safety:** `AttestationService` is a stateless singleton; no new shared mutable state.
- **Transaction:** no outer `@Transactional` around the whole `attest(...)` method — the KMS/screening
  network calls must never hold a DB connection open; each `Attestation` `save()` is its own transaction.
- **Module boundaries:** `QuorumDecisionRepository`/`ChainCursorRepository` remain package-private;
  `AttestationService` reaches them only via `QuorumDecisionService`/`WatchService`.
- **Null handling:** a missing `ChainCursor` for `(chain, txHash)` is a `REFUSED`/409 outcome, never a
  `NullPointerException` (AC8).

## Open Questions

No blockers — both Phase 1 open questions (required fact-type set; which address is "the counterparty")
are resolved above as explicit, disclosed proposals for Phase 3/4 review, not silently assumed.
