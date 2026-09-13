STATUS: FROZEN

# crypto · T21 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

14 of 15 findings accepted (one, #3, verified directly against the actual `chain_cursors` DDL before
acceptance — `V1__chain_baseline.sql` confirms no unique constraint on `(chain, tx_hash)`); 1 rejected.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | AC2 said BLOCKED happens "regardless of quorum/finality state," contradicting the brief's own quorum-first gate order | **ACCEPTED** | AC2 reworded: BLOCKED is only reachable *after* quorum + finality pass. New Locked Decision: screening is never attempted until all required facts are `AGREED` — the safer posture (don't even call the screening vendor for an unproven tx), matching the gate order already designed. |
| 2 | R21's "compliance queue" requirement is deferred without visible sign-off | **ACCEPTED** | Elevated from Scope/Out prose to an explicit, human-approved Open Question (see below) — the persisted `Attestation(BLOCKED)` + existing `ScreeningResult(BLOCKED)` rows are the interim audit trail; building an actual queue is not named by any task in `tasks.md` and stays out of this task's scope, now visibly disclosed rather than buried. |
| 3 | `findChainCursor` returning `Optional<ChainCursor>` hides real multiplicity — `chain_cursors` has no `(chain, tx_hash)` uniqueness, so multiple watches can share one transaction | **ACCEPTED (verified)** | Changed to `WatchService.findChainCursors(chain, txHash) -> List<ChainCursor>`. `AttestationService` screens every distinct non-null `fromAddress` among the matching cursors; **any** `BLOCKED` result blocks the whole attestation (fail-closed, conservative). Empty list, or every cursor having a `null fromAddress` → `REFUSED`. |
| 4 | The new `(chain, tx_hash)` query has no supporting index | **ACCEPTED** | New additive migration `V10__crypto_chain_cursors_chain_tx_hash_idx.sql` — `CREATE INDEX idx_chain_cursors_chain_tx_hash ON chain.chain_cursors(chain, tx_hash);`. Does not modify `V1`-`V9`. |
| 5 | A race exists between the `EXISTENCE` quorum decision committing and the `ChainCursor` snapshot becoming visible (separate transactions, T17) | **ACCEPTED (documented, not redesigned)** | Disclosed, accepted limitation: `/attest` is best-effort under this race; a client that sees `409` immediately after finality is reached should retry. Redesigning the cursor-snapshot/quorum-decision write path to share a transaction is out of this task's scope and would revisit T17's own settled design. |
| 6 | `AttestOutcome`'s JPA mapping was ambiguous — a bare enum defaults to `ORDINAL`, violating the CHECK constraint | **ACCEPTED** | Explicit: `AttestOutcome` gets a package-private nested `DbConverter` (`@Convert`), mirroring `screening.ScreeningOutcome`'s exact shape — not `@Enumerated(STRING)` — for consistency with the more recent T19 precedent. |
| 7 | `AttestResponse`'s unused fields would serialize as JSON `null`, polluting both response shapes | **ACCEPTED** | `AttestResponse` gets `@JsonInclude(JsonInclude.Include.NON_NULL)`. |
| 8 | `Attestation.createdAt`'s source was unspecified | **ACCEPTED** | Supplied by `AttestationService` from the injected `Clock`, mirroring `Watch.register`/`ScreeningResult.create`. |
| 9 | Missing explicit `@Valid @RequestBody` on the controller parameter | **ACCEPTED** | `AttestController.attest(@Valid @RequestBody AttestRequest request)`. |
| 10 | A KMS infrastructure failure (not a gate failure) was undocumented — would surface as `500`, not `409` | **ACCEPTED, documented as intentional** | A `500` is the *correct* signal for a genuine KMS/infra fault — distinct from a `409` business-rule refusal. No `Attestation` row is persisted for this case (none of the three CHECK-constrained outcomes accurately describes an incomplete/infra-failed request). |
| 11 | `chain`/`txHash` had no format validation, so malformed values silently became `409` instead of `400` | **ACCEPTED for `chain`, documented-only for `txHash`** | `chain` gets `@Pattern(regexp = "^(ETHEREUM|TRON)$")` → `400` for an unsupported chain. `txHash` stays `@NotBlank` only — chain-specific hash-format validation is `token.AddressValidator`-adjacent scope, disproportionate here; a malformed-but-non-blank `txHash` is explicitly, intentionally `409 REFUSED` (no matching cursor/quorum decision will ever be found for it). |
| 12 | Watch lifecycle/status (`REGISTERED` vs. `UNREGISTERED`/expired) is not checked before signing | **REJECTED** | A fourth gate the task statement never names. Disclosed as an intentional non-goal: attestation is independent of watch lifecycle once quorum + finality + screening pass, matching the task statement's own explicit, exhaustive gate list. |
| 13 | No idempotency / duplicate-signature prevention across repeated identical requests | **ACCEPTED, documented, not implemented** | Recorded as a disclosed, deferred limitation (Open Question below) — no requirement in this spec package asks for idempotency here; adding a unique constraint + replay-detection is real, unrequested scope growth. |
| 14 | The `fromAddress`-is-the-counterparty assumption wasn't locked, and a `null fromAddress` case wasn't handled | **ACCEPTED** | Locked for launch as a named decision (below), with an explicit Open Question noting a future outbound-payment flow would need `toAddress` instead. A cursor with a `null fromAddress` contributes nothing to the screening set (treated the same as if it weren't found) rather than throwing. |
| 15 | The refused-attestation exception message's content/leakage was unspecified | **ACCEPTED** | Fixed, generic message format: `"Attestation preconditions not met for {chain}:{txHash}"` — never names which specific fact failed, never includes quorum/HELD internals, per `agents.md`'s "no internal detail" rule. |

## Task

Implement `POST /internal/v1/attest`: gate on `AGREED` quorum (EXISTENCE, AMOUNT, TOKEN, FINALITY) + met
finality, **then** `CLEARED` screening, then sign via `KmsSigner`; `BLOCKED` on a sanctioned
counterparty; `409` when any quorum/finality gate is unmet; every reachable outcome persisted to
`chain.attestations`.

## Purpose

Wire together the three independently-built gates (quorum/finality, screening, KMS signing) behind the
platform's single most consequential endpoint.

## Scope

**In:**
- `attest.AttestOutcome` — enum `{SIGNED, BLOCKED, REFUSED}` with a package-private `DbConverter`
  (Finding #6), matching `chk_attest_outcome` exactly.
- `attest.Attestation` — JPA entity mapping `chain.attestations` as shipped by `V1`: `chain`, `txHash`,
  `receiptDigest`, `outcome` (via the converter), `kmsKeyId`/`signedAt` (nullable, `SIGNED`-only),
  `createdAt` (from the injected `Clock`, Finding #8). No setters, public static `create(...)`.
- `attest.AttestationRepository` — package-private `JpaRepository<Attestation, Long>`.
- `attest.AttestRequest` — `record AttestRequest(@NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$")
  String receiptDigestSha256, @NotBlank @Pattern(regexp = "^(ETHEREUM|TRON)$") String chain,
  @NotBlank String txHash)` (Finding #11).
- `attest.AttestResponse` — `record AttestResponse(String signature, String kmsKeyId, Instant signedAt,
  String outcome, String reason)`, `@JsonInclude(JsonInclude.Include.NON_NULL)` (Finding #7),
  `signed(...)`/`blocked(String reason)` static factories.
- `attest.AttestationRefusedException` (`RuntimeException`) — thrown for every gate failure that is
  **not** an active sanctions hit. Message is always the fixed, generic format
  `"Attestation preconditions not met for " + chain + ":" + txHash` (Finding #15) — never names which
  fact failed. `AttestationService` persists `Attestation(outcome=REFUSED)` before this exception
  propagates. **Not** thrown for a KMS/infrastructure failure (Finding #10) — that propagates as-is,
  surfacing as `500`, with no `Attestation` row persisted for that attempt.
- `attest.AttestExceptionHandler` — `@RestControllerAdvice @Order(HIGHEST_PRECEDENCE)`, mirrors
  `watch.WatchExceptionHandler`: `AttestationRefusedException` → `409 ProblemDetail`
  (title "Attestation refused", detail = the exception's own fixed message).
- `attest.AttestController` — `POST /internal/v1/attest`, `attest(@Valid @RequestBody AttestRequest
  request)` (Finding #9), no security annotations (already covered by `ResourceServerConfig`).
- `attest.AttestationService.attest(AttestRequest request)` → `AttestResponse`:
  1. For each of `EXISTENCE`, `AMOUNT`, `TOKEN`, `FINALITY` (fixed list, `CONFIRMATIONS` deliberately
     excluded — proposed at Phase 2, unchallenged by Phase 3), call
     `QuorumDecisionService.isAgreed(chain, txHash, factType)`; any `false` →
     `AttestationRefusedException` (persisting `REFUSED` first).
  2. **Only after step 1 fully passes** (Finding #1's gate-order lock): call
     `WatchService.findChainCursors(chain, txHash)` → `List<ChainCursor>`. Collect the distinct,
     non-null `fromAddress` values. Empty (no cursors, or every cursor's `fromAddress` is `null`,
     Finding #14) → `AttestationRefusedException`.
  3. For each distinct `fromAddress`, call `ScreeningClient.screen(chain, fromAddress, txHash)`. Any
     `BLOCKED` → persist `Attestation(outcome=BLOCKED)`, return `AttestResponse.blocked(reason)`
     immediately (fail-closed on the first hit, no need to screen the rest). Any `ERROR`, or any thrown
     exception, from *any* screening call → `AttestationRefusedException` (Finding #10's disposition
     does not apply here — this is L12's own fail-closed contract, not a KMS/infra fault). All `CLEARED`
     → proceed.
  4. Hex-decode `receiptDigestSha256`, call `KmsSigner.sign(digestBytes)` — **any exception from this
     call propagates uncaught, unconverted** (Finding #10) — no `Attestation` row persisted for this
     attempt, surfaces as `500`.
  5. Persist `Attestation(outcome=SIGNED, kmsKeyId, signedAt)`, return `AttestResponse.signed(...)`.
  - No outer `@Transactional`.
- **New public seams:**
  - `quorum.QuorumDecisionService.isAgreed(String chain, String txHash, FactType factType) -> boolean`.
  - `watch.WatchService.findChainCursors(String chain, String txHash) -> List<ChainCursor>` (backed by a
    new package-private `ChainCursorRepository.findByChainAndTxHash(chain, txHash) -> List<ChainCursor>`).
- `resources/db/migration/V10__crypto_chain_cursors_chain_tx_hash_idx.sql` (Finding #4).
- `KmsSignerArchitectureTest` regression check (no rule change needed).

**Out:**
- An actual compliance-queue table/mechanism (R21's literal wording) — deferred, see Open Questions.
- Idempotency / duplicate-request detection — deferred, see Open Questions.
- Watch-lifecycle/status gating (Finding #12, rejected).
- `VerificationKeysController` (task 22). Any new `chain.*` event. Modifying `KmsSigner`,
  `ScreeningClient`, `FailClosedScreeningClient`, `QuorumDecisionService.evaluate`, `Watcher`, or any
  `V1`-`V9` migration.

## Business Rules

- **R20.** A valid attest request signs via KMS and returns `{signature, kmsKeyId, signedAt,
  outcome: "SIGNED"}`.
- **R21.** A sanctioned-counterparty attest request (reachable only after quorum + finality pass, Finding
  #1) returns `{outcome: "BLOCKED", reason}`, no signature.
- **R23.** An attest request for a transaction that hasn't met quorum and finality returns `409`, never
  a signature.

## Locked Decisions

- **L10.** `/attest` refuses to sign unless quorum + finality (+ screening) passed. Screening is
  reachable **only after** quorum + finality both pass (Finding #1, new for this task) — the vendor is
  never called for an unproven transaction.
- **L11.** KMS-only signing stays single-path — `AttestationService` calls `KmsSigner.sign(...)` only.
- **L12.** Screening gates attestation, fail-closed — `ERROR`/exception from any `screen(...)` call
  refuses to sign (`REFUSED`, not `BLOCKED` — only an actual confirmed hit is `BLOCKED`).
- **L-T21a (new).** The screened counterparty is the transaction's `fromAddress` (the external paying
  party), never `toAddress` (the platform's own already-known watch address) — locked for launch's
  inbound-payment-only flow (Finding #14). A future outbound/refund flow would need a different rule.
- **L-T21b (new).** A KMS/infrastructure failure during signing is a `500`, never converted to `REFUSED`
  — these are semantically distinct situations (Finding #10).

## Dependencies

`quorum.QuorumDecisionService` (new `isAgreed`), `observation.FactType`, `watch.WatchService` (new
`findChainCursors`), `watch.ChainCursor`, `screening.ScreeningClient`, `screening.ScreeningOutcome`,
`attest.KmsSigner`, `attest.SignatureResult`, `common.ClockConfig`'s `Clock`, the new
`AttestationRepository`.

## Inputs

`AttestRequest{receiptDigestSha256 (64 hex chars), chain (ETHEREUM|TRON), txHash}`.

## Outputs

`AttestResponse` (`200`, `SIGNED` or `BLOCKED`) · `409 problem+json` (`REFUSED`) · `500` (KMS/infra
fault, undocumented shape beyond the generic handler, Finding #10).

## State Changes

- `INSERT` into `chain.attestations` for every `SIGNED`/`BLOCKED`/`REFUSED` outcome — **not** for a
  `500`/KMS-infra-fault attempt.
- New index-only migration `V10` on `chain_cursors` — no data change.

## Files to Create

- `attest/AttestOutcome.java`, `attest/Attestation.java`, `attest/AttestationRepository.java`,
  `attest/AttestRequest.java`, `attest/AttestResponse.java`, `attest/AttestationRefusedException.java`,
  `attest/AttestExceptionHandler.java`, `attest/AttestationService.java`, `attest/AttestController.java`.
- `resources/db/migration/V10__crypto_chain_cursors_chain_tx_hash_idx.sql`.

## Files to Modify

- `quorum/QuorumDecisionService.java` — add `isAgreed(chain, txHash, factType)`.
- `watch/WatchService.java` — add `findChainCursors(chain, txHash)`.
- `watch/ChainCursorRepository.java` — add `findByChainAndTxHash(chain, txHash) -> List<ChainCursor>`.

## Files NOT to Modify

- `attest/KmsSigner.java`, `attest/SignatureResult.java`, `attest/KmsSignerArchitectureTest.java`.
- `screening/**` (T19, frozen). `quorum/QuorumDecisionService.evaluate(...)`, `QuorumDecision.java`,
  `QuorumDecisionRepository.java`. `watch/Watcher.java`, `WatchController.java`, `Watch.java`.
- `V1`-`V9` migrations. `common/ResourceServerConfig.java`, `PublicEndpoints.java`,
  `ApiExceptionHandler.java`. Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R20/L10).** All four required facts `AGREED`, all screened `fromAddress`es `CLEARED` → `200
  SIGNED`, digest sent to KMS matches the hex-decoded request digest exactly.
- **AC2 (R21/L12/L-T21a — revised, Finding #1).** `BLOCKED` is reachable **only after** quorum + finality
  pass (never before) — a screening `BLOCKED` on a request that also fails quorum/finality still returns
  `409`, not `200 BLOCKED`, because screening is never even attempted in that case.
- **AC3 (R23/L10).** Any of EXISTENCE/AMOUNT/TOKEN/FINALITY not `AGREED` → `409`, no cursor lookup, no
  screening call, no `KmsSigner.sign` call.
- **AC4 (L12, fail-closed).** Any screening `ERROR` or thrown exception (among possibly several
  `fromAddress`es) → `409` (`REFUSED`), never `200 BLOCKED` and never a signature.
- **AC5 (R20, audit trail).** Exactly one `Attestation` row for `SIGNED`/`BLOCKED`/`REFUSED`; **zero**
  rows for a KMS/infra-fault `500` (Finding #10/L-T21b).
- **AC6 (L11).** `KmsSignerArchitectureTest` still passes unmodified.
- **AC7 (module boundary).** `QuorumDecisionRepository`/`ChainCursorRepository` stay package-private.
- **AC8 (null/multiplicity handling, Finding #3/#14).** Zero matching `ChainCursor`s, or all matching
  cursors having a `null fromAddress` → `409` (`REFUSED`), never an NPE. Multiple cursors with different
  `fromAddress`es → every distinct one is screened; any single `BLOCKED` blocks the whole request.
- **AC9 (Finding #11).** An unsupported `chain` value → `400`; a malformed-but-non-blank `txHash` →
  `409 REFUSED` (documented, not `400`).
- **AC10 (Finding #7).** `AttestResponse`'s JSON never includes a `null`-valued field.
- **AC11 (Finding #15).** `AttestationRefusedException`'s message never names which specific fact/gate
  failed.

## Required Tests

- `shouldReturnKmsSignatureFromAttestForValidDigest` (named, AC1).
- `shouldReturnBlockedFromAttestOnSanctionedCounterparty` (named, AC2) — including a case proving
  screening is skipped entirely (never called) when quorum/finality already fail.
- `shouldRejectAttestWhenQuorumOrFinalityNotMet` (named, AC3) — parameterized across each of the four
  required fact types.
- Screening `ERROR`/thrown-exception fail-closed test (AC4).
- Multi-cursor screening test: 2+ cursors with distinct `fromAddress`es, one `BLOCKED` among them → whole
  request `BLOCKED` (AC8).
- Zero-cursor and all-null-`fromAddress` tests → `409`, not NPE (AC8).
- One `Attestation`-row-per-outcome test for `SIGNED`/`BLOCKED`/`REFUSED`; a KMS-failure test asserting
  **zero** rows persisted (AC5).
- `AttestRequest` validation: malformed digest/unsupported chain → `400`; malformed txHash → `409`, not
  `400` (AC9).
- `AttestResponse` JSON-shape test: no `null` fields in either the `SIGNED` or `BLOCKED` body (AC10).
- Exception-message content test: never contains a fact-type name or "HELD"/quorum internals (AC11).
- `KmsSignerArchitectureTest` regression run (AC6).
- `internal.crypto:write` scope-enforcement integration test.

## Constraints

- **Performance:** at most `N` screening calls per request (`N` = number of distinct `fromAddress`es
  among matching cursors, typically 1) + one KMS call; no polling, no added retries.
- **Security:** no `Logger` field required, but if logging is added, never at INFO/WARN with the raw
  digest/signature; refusal messages are always generic (Finding #15/AC11).
- **Thread-safety:** `AttestationService` stateless singleton.
- **Transaction:** no outer `@Transactional`; each `Attestation` `save()` is its own transaction; the
  screening/KMS network calls never hold a DB connection open.
- **Module boundaries:** `QuorumDecisionRepository`/`ChainCursorRepository` stay package-private; cross-
  module reads go only through `QuorumDecisionService`/`WatchService`.
- **Null/multiplicity handling:** see AC8.

## Open Questions

Two disclosed, deferred limitations, both explicitly sign-off'd at this gate (not silently assumed):

- **R21's "compliance queue" is not built in this task** (Finding #2). The persisted `Attestation
  (BLOCKED)` + `ScreeningResult(BLOCKED)` rows are the interim audit trail. No task in `tasks.md` names
  building an actual queue; revisit if/when one is scheduled.
- **`/attest` has no idempotency guarantee** (Finding #13) — an identical repeated request signs and
  persists again. Not required by any requirement in this spec package; revisit if duplicate-signature
  risk becomes operationally material.
