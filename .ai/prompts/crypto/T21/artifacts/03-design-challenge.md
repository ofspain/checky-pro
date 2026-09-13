<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# crypto · T21 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | crypto-service |
| **Task** | T21 — Attest endpoint |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | artifacts/02-task-implementation-brief.md |
| **Produces** | artifacts/03-design-challenge.md |

---

## 1. AC2 claims BLOCKED is returned regardless of quorum/finality state, but the gate order makes that impossible

- **Issue:** The brief's gate sequence is (1) quorum/finality, (2) cursor lookup, (3) screening. AC2
  nevertheless says a BLOCKED outcome is returned "regardless of quorum/finality state." If any
  quorum/finality fact is not AGREED, the request is refused at step 1 and screening is never called,
  so a sanctioned counterparty cannot be detected. The two statements contradict each other.
- **Severity:** High
- **Evidence:** Phase 2 brief Scope - AttestationService.attest(...) step 1-3; AC2.
- **Recommended brief amendment:** Either (a) move screening before the quorum/finality gates so a
  sanctions hit is always surfaced, or (b) remove "regardless of quorum/finality state" from AC2 and
  state explicitly that BLOCKED is only possible after quorum + finality are met. Option (b) is the
  safer security posture (do not even call the screening vendor until the tx is proven), but it must
  be an explicit LOCKED decision, not an unstated assumption. Also reconcile with R21, which does not
  itself contain the "regardless" phrase.

## 2. R21's compliance queue is not implemented

- **Issue:** requirements.md R21 requires a sanctioned hit to "place the item in the compliance
  queue." The brief explicitly scopes the compliance-queue mechanism out and substitutes the
  persisted Attestation(BLOCKED) row plus the existing ScreeningResult(BLOCKED) row as the audit
  trail. This is a deviation from a scoped requirement, not just an implementation detail.
- **Severity:** High
- **Evidence:** spec/crypto-service/requirements.md R21; Phase 2 brief Scope Out section.
- **Recommended brief amendment:** Either add a compliance_queue table/event as part of this task,
  or record an explicit LOCKED decision / Open Question that R21's queue requirement is deferred and
  that the two persisted rows constitute the interim audit trail. If deferred, update R21 or add an
  Open Question so the deviation is visible to downstream reviewers and auditors.

## 3. findChainCursor(chain, txHash) returning Optional<ChainCursor> is ambiguous

- **Issue:** ChainCursor is keyed by watch_id, not by (chain, tx_hash). The schema places no unique
  constraint on (chain, tx_hash), and one transaction can legitimately involve multiple watched
  platform addresses (for example, a deposit sweep, an exchange hot wallet, or multiple invoices paid
  in one tx). The proposed findByChainAndTxHash method therefore can return multiple rows. The brief
  wraps it in Optional<ChainCursor> (singular), which hides the multiplicity and forces an arbitrary
  choice of which counterparty to screen.
- **Severity:** High
- **Evidence:** services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java schema
  mapping; V1__chain_baseline.sql chain_cursors DDL has no (chain, tx_hash) uniqueness.
- **Recommended brief amendment:** Decide and document the intended behavior for multi-watch
  transactions. Options:
  - Screen every distinct fromAddress among the matching cursors and fail closed if any hits.
  - Reject the attestation with REFUSED if more than one cursor matches.
  - Add a unique (chain, tx_hash) constraint (and index) and document that the platform does not
    support multi-watch transactions.
  Whichever is chosen, the repository return type must change from Optional<ChainCursor> to
  List<ChainCursor> or similar.

## 4. New ChainCursorRepository.findByChainAndTxHash lacks a database index

- **Issue:** The brief adds findByChainAndTxHash(chain, txHash) to ChainCursorRepository but forbids
  modifying V1-V9 migrations. The baseline DDL only indexes chain_cursors by watch_id (implicit PK).
  A query by (chain, tx_hash) would perform a sequential scan on a table that grows with every
  registered watch.
- **Severity:** Medium
- **Evidence:** V1__chain_baseline.sql chain_cursors DDL; Phase 2 brief Files NOT to Modify.
- **Recommended brief amendment:** Add a new migration V10__chain_cursors_chain_tx_hash_idx.sql
  creating CREATE INDEX idx_chain_cursors_chain_tx_hash ON chain.chain_cursors(chain, tx_hash).
  This is a forward-only additive migration and does not modify existing migrations. Alternatively,
  if the repository method is removed in favor of a different lookup strategy, drop this finding.

## 5. Race between EXISTENCE AGREED and the cursor snapshot

- **Issue:** Step 1 of AttestationService.attest(...) checks that EXISTENCE is AGREED via
  QuorumDecisionService.isAgreed. The ChainCursor snapshot (txHash, fromAddress, toAddress) is
  written by Watcher/finality handling in a separate transaction (T17). It is possible for the quorum
  decision to be persisted but the cursor snapshot not yet committed/visible. In that case step 2
  finds no cursor and returns REFUSED, even though all gates are logically met.
- **Severity:** Medium
- **Evidence:** ChainCursor.recordSeenTransaction(...) Javadoc states it is populated by T17's
  seen-transaction handling; QuorumDecisionService.evaluate(...) Javadoc states it is not
  @Transactional and each save is its own transaction.
- **Recommended brief amendment:** Either (a) accept that /attest is intentionally best-effort and
  clients must retry on 409, documenting this in AC3/AC8, or (b) require the cursor snapshot and the
  EXISTENCE decision to be written in the same transaction / use the cursor snapshot as the source of
  truth for EXISTENCE rather than the quorum_decisions table. Option (b) is a larger change and may
  conflict with T17's design.

## 6. AttestOutcome JPA mapping is unspecified

- **Issue:** The brief says AttestOutcome maps to the chk_attest_outcome CHECK constraint values and
  that "no case transformation is needed," but it never specifies how JPA should persist the enum.
  Spring Data JPA defaults enums to ORDINAL unless annotated with @Enumerated(EnumType.STRING) or a
  converter. WatchStatus in the same service uses @Enumerated(EnumType.STRING) (verified). If
  AttestOutcome is not annotated, the DB will receive 0, 1, 2, violating the CHECK constraint.
- **Severity:** High
- **Evidence:** services/crypto/src/main/java/com/themistra/crypto/watch/Watch.java:66-68; Phase 2
  brief AttestOutcome description.
- **Recommended brief amendment:** Explicitly require Attestation.outcome to be annotated with
  @Enumerated(EnumType.STRING) (or an equivalent converter) and reference the WatchStatus pattern.

## 7. AttestResponse JSON shape is not fully specified

- **Issue:** AttestResponse is a single record containing all possible fields. The static factories
  leave the irrelevant fields null (for example, blocked(...) leaves signature/kmsKeyId/signedAt
  null). Without @JsonInclude(JsonInclude.Include.NON_NULL), Jackson will serialize those nulls,
  producing a 200 SIGNED body with "reason": null and a 200 BLOCKED body with "signature": null.
  The wire contract in design.md §4c shows only the relevant fields for each shape.
- **Severity:** Medium
- **Evidence:** spec/crypto-service/design.md §4c Internal API wire shapes; Phase 2 brief
  AttestResponse description.
- **Recommended brief amendment:** Require AttestResponse to be annotated with
  @JsonInclude(JsonInclude.Include.NON_NULL) (or split into sealed/interface response types) and add
  AC/test asserting the exact JSON shape for SIGNED and BLOCKED.

## 8. Attestation.createdAt source is unspecified

- **Issue:** The brief says Attestation has a createdAt field and a static create(...) factory, but
  it does not say how createdAt is populated. Consistency with other entities (Watch.register,
  ScreeningResult.create) suggests passing an Instant (from the injected Clock) into create(...).
- **Severity:** Low
- **Evidence:** Watch.register(..., Instant now) and ScreeningResult.create(..., Instant screenedAt)
  patterns; Phase 2 brief Attestation description.
- **Recommended brief amendment:** Specify the Attestation.create(...) signature, including that
  createdAt is supplied by AttestationService from the injected Clock (mirrors Watch.register).

## 9. Missing @Valid @RequestBody on controller parameter

- **Issue:** The brief defines bean-validation annotations on AttestRequest but does not explicitly
  require @Valid (and @RequestBody) on the controller method parameter. Without @Valid, the @Pattern
  and @NotBlank constraints are not evaluated by Spring, and malformed requests fall into the generic
  exception handler or fail later.
- **Severity:** Medium
- **Evidence:** Phase 2 brief AttestController description; AttestRequest validation annotations.
- **Recommended brief amendment:** Explicitly require AttestController.attest(@Valid @RequestBody
  AttestRequest request).

## 10. KMS failure results in 500, not documented

- **Issue:** KmsSigner.sign(...) propagates all exceptions uncaught (T20). In /attest, a KMS timeout,
  credential failure, or service exception will therefore surface as a 500 Internal Server Error, not
  a 409 Attestation refused. The brief does not acknowledge this, and the ACs do not distinguish
  infrastructure failures from gate failures.
- **Severity:** Medium
- **Evidence:** services/crypto/src/main/java/com/themistra/crypto/attest/KmsSigner.java Javadoc;
  spec/crypto-service/requirements.md R20.
- **Recommended brief amendment:** Add a constraint/AC note that KMS/infrastructure failures are not
  swallowed and result in 500, with the Attestation(SIGNED) row persisted only after a successful KMS
  call. Alternatively, if a 500 is unacceptable for the endpoint, decide whether to map KMS exceptions
  to REFUSED (with no signature) and persist an Attestation(REFUSED) row.

## 11. chain and txHash validation route malformed values to 409 REFUSED

- **Issue:** AttestRequest validates receiptDigestSha256 with @Pattern so malformed digests become
  400. However, chain and txHash are only @NotBlank. An unsupported chain value or a malformed txHash
  will pass validation, then fail the quorum/cursor lookups and be returned as 409 Attestation
  refused. This mixes 400 (client syntax error) and 409 (precondition not met) semantics.
- **Severity:** Low
- **Evidence:** Phase 2 brief AttestRequest description; AC3/AC8.
- **Recommended brief amendment:** Either (a) restrict chain to @Pattern(regexp = "^(ETHEREUM|TRON)$")
  and txHash to a chain-appropriate hex pattern, routing malformed values to 400, or (b) document
  explicitly that unknown/malformed chain/txHash are treated as 409 REFUSED by design. Do not leave
  the behavior implicit.

## 12. Watch status is not checked before attestation

- **Issue:** A transaction may meet quorum and finality for a watch that has since been unregistered
  or expired. The brief does not state whether /attest should still sign in that case. Signing for an
  expired/unregistered watch could produce a signature the Payment Service no longer expects.
- **Severity:** Low
- **Evidence:** Watch.status enum and WatchService.unregister; Phase 2 brief gate sequence.
- **Recommended brief amendment:** Add an explicit decision: either require the watch to be REGISTERED
  (or at least not UNREGISTERED) as an additional gate, or document that attestation is intentionally
  independent of watch lifecycle once finality is reached.

## 13. No idempotency / duplicate-signature prevention

- **Issue:** Calling POST /internal/v1/attest twice with the same (chain, txHash, receiptDigest) will
  perform two KMS Sign calls and insert two Attestation rows. R20 does not require idempotency, but
  for a signing endpoint this is a notable omission and could lead to duplicate signatures for the
  same receipt.
- **Severity:** Low
- **Evidence:** spec/crypto-service/requirements.md R20; V1__chain_baseline.sql attestations table
  has no unique constraint on (chain, tx_hash, receipt_digest).
- **Recommended brief amendment:** Document whether /attest is intentionally not idempotent, or add a
  unique constraint and a 409/200 idempotency behavior (return the existing signature row on replay).
  If deferred, record it as an Open Question.

## 14. Assumption that the counterparty is always fromAddress is not locked

- **Issue:** The brief proposes screening ChainCursor.fromAddress() because "the toAddress is the
  platform's own already-known, already-approved invoice/watch address." This assumes the platform
  always receives funds. If the platform ever initiates a refund or outbound payment, the counterparty
  would be toAddress. The assumption is reasonable for launch but should be explicit.
- **Severity:** Low
- **Evidence:** Phase 2 brief Scope - AttestationService.attest(...) step 2; ChainCursor
  fromAddress/toAddress fields.
- **Recommended brief amendment:** Add the counterparty-address rule as a LOCKED decision for launch
  ("screen the transaction's fromAddress"), with an Open Question noting that future outbound flows
  may require a different rule. Also handle the case where fromAddress is null (cursor snapshot not
  yet written) as a REFUSED outcome, not an NPE.

## 15. AttestationRefusedException message content is unspecified

- **Issue:** The brief says the exception handler sets detail = exception message, but it does not say
  what the message should contain. A generic message makes debugging hard; a detailed message may leak
  internal state (for example, which specific fact is HELD).
- **Severity:** Low
- **Evidence:** Phase 2 brief AttestExceptionHandler and AttestationRefusedException descriptions.
- **Recommended brief amendment:** Define the message format and whether it may include fact-type or
  failure-category detail. Ensure it conforms to agents.md "no internal detail" in error responses.
