# crypto · T21 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/crypto` is the Spring Boot 3.5.4 / Java 21 module implementing Themistra's crypto-payment
verification and attestation platform, package-by-feature under `com.themistra.crypto`. It owns the
`chain` Postgres schema (Flyway `V1`-`V9`, immutable/additive-only). Every emitted domain fact goes
through the transactional outbox; internal endpoints require `internal.crypto:write` scope, enforced
centrally by `common.ResourceServerConfig`'s path-matcher rule on `/internal/v1/**` — a new endpoint
under that path needs **no new security configuration**. Errors are RFC 9457 `ProblemDetail`, handled by
a layered `@RestControllerAdvice` scheme: `common.ApiExceptionHandler` (`@Order(LOWEST_PRECEDENCE)`,
generic/validation errors, never imports a feature module) plus each module's own dedicated advice
(`@Order(HIGHEST_PRECEDENCE)`) for its own domain exceptions — `watch.WatchController`'s pairing with a
`WatchExceptionHandler` is the direct precedent for this task's own `AttestController`.

T19 (screening) and T20 (KMS signer) already built two of this task's three gate dependencies as
complete, independently-tested, currently-unconsumed units. This task is the first to actually wire them
together, plus a third gate (quorum + finality, built in T06-T14) that has no existing public read API.

## 2. Existing code this task touches

- **`quorum` module** — `QuorumDecision` (entity: `chain`, `txHash`, `factType`, `outcome`
  (`QuorumOutcome`: `AGREED`, `HELD`, `UNKNOWN_TOKEN`), `agreeingCount`/`providerCount`, `decidedAt`).
  `QuorumDecisionRepository` is **package-private**, with exactly one query method,
  `findByChainAndTxHashAndFactType(chain, txHash, factType)`. `QuorumDecisionService` only exposes a
  write path (`evaluate(...)`, called by the watcher) — **no public read method exists today**. This
  task needs read access to "is `(chain, txHash, factType)` `AGREED`?" from a different package
  (`attest`), so either `QuorumDecisionService` gains a new public query method, or an equivalent public
  seam is added — a Phase 2 design decision, not yet made.
- **`finality` module** — pure/stateless per-chain policy objects (`FinalityPolicy`,
  `EthereumFinalityPolicy`, `TronFinalityPolicy`), no persistence of their own. **"Finality met" is not
  a separate mechanism** — it is recorded as an ordinary `QuorumDecision` row with
  `factType = FactType.FINALITY`, `outcome = AGREED`, written by `Watcher.pollFinalityFor` once a local
  majority already agrees (T17). `ChainCursor.lastFinalizedBlock` is a derived, forward-only secondary
  signal set only after that `QuorumDecision` is persisted — the `QuorumDecision(FINALITY, AGREED)` row
  is the authoritative source, not the cursor field. Checking finality for this task means the exact
  same repository call as checking any other fact type's quorum, just with `FactType.FINALITY`.
- **`watch` module** — `Watch` (`watchId`, `invoiceUuid`, `chain`, `address`, `tokenContractAddress`,
  `expectedAmount`, `status`, ...) and `ChainCursor` (`watchId`, `chain`, `txHash`, `amount`,
  `fromAddress`, `toAddress`, `lastFinalizedBlock`, ...). **No existing repository method looks up either
  entity by `(chain, txHash)` alone** — `WatchRepository`/`ChainCursorRepository` (both package-private)
  only support lookup by `watchId`. The `/attest` request carries `{chain, txHash}`, not `watchId` — this
  task needs a new `(chain, txHash)` → counterparty-address lookup (screening needs an address, per
  `ScreeningClient.screen(chain, address, txHash)`), which most naturally comes from `ChainCursor`'s
  already-stored `fromAddress`/`toAddress` for that watch. A new query method and/or widened visibility
  on `ChainCursorRepository` is required — a Phase 2 design decision.
- **`screening` module` (T19, complete, unconsumed)** —
  `ScreeningClient.screen(String chain, String address, String txHash)` → `ScreeningOutcome`
  (`CLEARED`/`BLOCKED`/`ERROR`). Only `CLEARED` may lead to a signature; `BLOCKED`/`ERROR` and any thrown
  exception are all fail-closed for the caller (documented contract, not enforced by the interface
  itself). `FailClosedScreeningClient` is the only implementation (no real vendor yet, Q2 unanswered).
- **`attest` module (T20, complete, unconsumed)** — `KmsSigner.sign(byte[] digestSha256)` →
  `SignatureResult(signatureBase64, kmsKeyId, signedAt)`. `SignatureResult` is **package-private**, and
  its own Javadoc explicitly names `AttestationService` (this task) as its only legitimate consumer,
  confirming the new files belong inside `com.themistra.crypto.attest` itself, not a new package.
  `KmsSignerArchitectureTest`'s ArchUnit rule (verified, negative-proof-tested) means `AttestationService`
  can call `KmsSigner.sign(...)` freely (same package) but must never itself touch the KMS SDK directly.
- **`attestations` table** — already shipped by `V1`, already granted `INSERT, SELECT` to `crypto_app` by
  `V2` (append-only, no `UPDATE`/`DELETE`, matching every other audit-trail table in this service). No
  entity/repository exists yet — `Attestation.java`/`AttestationRepository.java` are this task's own new
  files, per `design.md`'s package map. Columns: `chain`, `tx_hash`, `receipt_digest` (`CHAR(64)`, i.e.
  the hex digest, not raw bytes), `outcome` (`SIGNED|BLOCKED|REFUSED`), `kms_key_id` (nullable),
  `signed_at` (nullable), `created_at`.
- **Does not exist yet, this task's new files** (per `design.md`'s own package map):
  `attest/AttestController.java`, `attest/AttestationService.java`, `attest/Attestation.java`,
  `attest/AttestationRepository.java`. `VerificationKeysController.java` (the well-known endpoint) is
  task 22, not this one.

## 3. Established patterns to follow

- **Internal endpoint shape** (`watch.WatchController`): a thin `@RestController` with **no security
  annotations of its own** (scope enforcement is centralized in `ResourceServerConfig`, already covers
  `/internal/v1/**`); delegates immediately to a `Service` class; maps the service's return value to a
  `ResponseEntity`. DTOs are hand-written records (no `contracts/api/crypto-internal.yaml` exists
  anywhere in this repo yet — confirmed absent — so this task's request/response records are hand-written
  too, following the same disclosed-gap precedent every prior task in this package has already noted).
- **Error handling**: a domain-specific `@RestControllerAdvice` at `@Order(HIGHEST_PRECEDENCE)` (mirrors
  `WatchExceptionHandler`) for this task's own exceptions (e.g. the R23 409-on-unmet-quorum/finality
  case); `common.ApiExceptionHandler` (`@Order(LOWEST_PRECEDENCE)`) handles generic/validation failures
  and must never be modified to import from a feature module.
- **Entity/repository conventions**: no setters, `protected`/package-private no-arg constructor for JPA,
  public static `create(...)` factory with `Objects.requireNonNull` on non-nullable fields, package-private
  repository interface with only the finder methods actually needed (established uniformly across
  `TokenAllowlist`, `Observation`, `ScreeningResult`, `QuorumDecision`).
- **Cross-module read access**: today, every module's repository is package-private with a narrow,
  purpose-built query set. This task is the first to need read access into `quorum` and `watch` from a
  different package (`attest`) — no existing precedent for "package X reads package Y's repository"
  exists yet in this codebase; a new public method on the *service* layer (not repository) is the more
  consistent shape, matching how `KmsSigner`/`ScreeningClient` are themselves the module's public seam,
  not their backing repositories.
- **Money/decimal discipline**: `agents.md` — money is `BigDecimal`/`NUMERIC`, decimal strings on the
  wire, never JSON numbers. Not directly relevant to the attest request/response shape itself (no
  monetary field in `{receiptDigestSha256, chain, txHash}` or the response), but relevant if the
  compliance-queue placement (R21) or the `BLOCKED` reason ever surfaces an amount.
- **Fail-closed discipline**: L12 (screening) and this task's own R23 (quorum/finality) both require a
  fail-closed default — `AttestationService` must never sign unless every gate explicitly agrees.

## 4. Testing conventions

- Unit tests: plain JUnit, fixed `Clock`, mocked collaborators (`QuorumDecisionService`-or-equivalent,
  `ScreeningClient`, `KmsSigner`, the new `AttestationRepository`).
- Integration tests: Testcontainers Postgres for `AttestationRepository`'s real-role persistence
  round-trip (mirrors `ScreeningResultRepositoryIntegrationTest`'s established pattern exactly — same
  append-only, `INSERT/SELECT`-only grant shape).
- ArchUnit: `KmsSignerArchitectureTest` already enforces that `KmsSigner`/the KMS SDK stay unreachable
  from outside `attest` (and from any non-`KmsSigner*`-named class even within `attest`) — this task's
  new files (`AttestationService`, `AttestController`) will need to be considered against that rule's own
  exception shape (they live in `attest` but are NOT named `KmsSigner*`, and the rule as tightened in T20
  Phase 9 requires a `KmsSigner*`-prefixed name **and** residence in `attest` to depend on the KMS SDK —
  `AttestationService` must depend on `KmsSigner` the class, never the KMS SDK directly, which the
  existing rule already permits/requires correctly).
- No real vendor/RPC/AWS call in unit tests; Testcontainers/LocalStack only for genuine infrastructure
  (Postgres here — no AWS service is newly touched by this task beyond what `KmsSigner` already covers).

## 5. Known gaps / unknowns

- **Which fact types must be `AGREED` before signing is not explicitly enumerated anywhere in the spec.**
  `requirements.md`/`design.md`/`tasks.md` all say "AGREED quorum" or "quorum + finality" generically,
  never naming EXISTENCE/AMOUNT/TOKEN/CONFIRMATIONS/FINALITY individually in this context. I do not know
  for certain whether `CONFIRMATIONS` must also be `AGREED`, or whether only EXISTENCE+AMOUNT+TOKEN+
  FINALITY matter for signing correctness. This is a genuine, real interpretive gap — a Phase 2 design
  decision to make explicit and propose for Phase 3/4 review, not something to silently assume.
- **How the attest request's counterparty address for screening is determined is not specified.** The
  request carries only `{receiptDigestSha256, chain, txHash}` — no address field. `ScreeningClient.screen`
  needs an address. The only plausible source in the existing schema is `ChainCursor.toAddress()` (or
  `fromAddress()`) for the watch matching `(chain, txHash)` — but the spec never states which of the two
  addresses (sender or recipient) is "the counterparty" for OFAC screening purposes. I do not know this;
  a Phase 2 design decision requiring explicit proposal and review, not assumption.
- **No existing method resolves `(chain, txHash)` to a `Watch`/`ChainCursor` at all.** A new repository
  query is required; exact placement (widen `ChainCursorRepository`'s visibility, or add a new public
  method to `WatchService`) is undecided.
- **No public read API exists on the `quorum` module.** Adding one is required; whether it lives on
  `QuorumDecisionService` (most consistent with this module's existing "service is the public seam"
  convention) or elsewhere is undecided.
- **Exact behavior when `ScreeningClient.screen(...)` throws** (per its own documented contract, this
  must be treated as fail-closed, identical to `ERROR`) — translating that into an HTTP response shape
  (a 409? a 5xx problem+json? folded into `REFUSED`?) is not specified by `design.md`'s wire shapes
  (which only show `SIGNED`/`BLOCKED`/409-for-quorum-unmet) — a Phase 2 design decision.
- **Whether a `screen(...)` call happens on every attest request or only once per transaction** (i.e., is
  screening idempotent/cacheable via the already-persisted `ScreeningResult` table, or does every
  `/attest` call re-screen) is not specified — a Phase 2 design decision.
