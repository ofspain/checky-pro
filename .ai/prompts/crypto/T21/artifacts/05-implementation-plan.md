# crypto · T21 · Phase 5 — Implementation Plan

Every file traces to the frozen brief's (`artifacts/04-frozen-task-brief.md`) Files to Create/Modify
sections. Method signatures below are informed by direct reading of `QuorumDecisionService.java`,
`WatchService.java`, `ChainCursorRepository.java`, `ChainCursor.java`, and `RegisterWatchRequest.java` —
the `chain` pattern below (`"ETHEREUM|TRON"`, no anchors) matches `RegisterWatchRequest`'s own established
regex exactly, not a re-invented one.

## Files to create

1. `resources/db/migration/V10__crypto_chain_cursors_chain_tx_hash_idx.sql`
2. `attest/AttestOutcome.java`
3. `attest/Attestation.java`
4. `attest/AttestationRepository.java`
5. `attest/AttestRequest.java`
6. `attest/AttestResponse.java`
7. `attest/AttestationRefusedException.java`
8. `attest/AttestExceptionHandler.java`
9. `attest/AttestationService.java`
10. `attest/AttestController.java`

## Files to modify

1. `quorum/QuorumDecisionService.java` — add `isAgreed(...)`.
2. `watch/ChainCursorRepository.java` — add `findByChainAndTxHash(...)`.
3. `watch/WatchService.java` — add `findChainCursors(...)`.

## Migration content

```sql
-- T21: supports AttestationService's new (chain, tx_hash) lookup (ChainCursorRepository
-- .findByChainAndTxHash) - chain_cursors has no index beyond its identity PK today (V1), so this
-- query would otherwise sequential-scan a table that grows with every registered watch.
CREATE INDEX idx_chain_cursors_chain_tx_hash ON chain.chain_cursors(chain, tx_hash);
```
No grant statement needed — this is a read-path index only, orthogonal to `V6`/`V7`'s existing
`crypto_app` grants on `chain_cursors`.

## Public methods (signatures)

- `AttestOutcome` — `enum AttestOutcome { SIGNED, BLOCKED, REFUSED }` + package-private nested
  `DbConverter implements AttributeConverter<AttestOutcome, String>` (verified: `chk_attest_outcome`'s
  literal values are already uppercase — `convertToDatabaseColumn` returns `.name()` as-is, no case
  transform, mirroring `ScreeningOutcome.DbConverter` exactly).
- `Attestation`:
  - `static Attestation create(String chain, String txHash, String receiptDigest, AttestOutcome outcome, String kmsKeyId, Instant signedAt, Instant createdAt)`
    (`kmsKeyId`/`signedAt` nullable — only non-null for `SIGNED`).
  - `Long id()`, `String chain()`, `String txHash()`, `String receiptDigest()`, `AttestOutcome outcome()`,
    `String kmsKeyId()`, `Instant signedAt()`, `Instant createdAt()`.
- `AttestationRepository` — `interface AttestationRepository extends JpaRepository<Attestation, Long>`
  (package-private; no finder methods needed — this task only ever inserts).
- `AttestRequest` — `record AttestRequest(@NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String receiptDigestSha256, @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain, @NotBlank String txHash)`.
- `AttestResponse` — `@JsonInclude(JsonInclude.Include.NON_NULL) record AttestResponse(String signature, String kmsKeyId, Instant signedAt, String outcome, String reason)` with:
  - `static AttestResponse signed(String signature, String kmsKeyId, Instant signedAt)` → `outcome = "SIGNED"`, `reason = null`.
  - `static AttestResponse blocked(String reason)` → `outcome = "BLOCKED"`, all signing fields `null`.
- `AttestationRefusedException extends RuntimeException`:
  - `AttestationRefusedException(String chain, String txHash)` → `super("Attestation preconditions not met for " + chain + ":" + txHash)` (Finding #15's fixed, generic format — the constructor takes `chain`/`txHash` only, never a fact-type or failure-reason parameter, so there is no code path that could ever construct a leakier message).
- `AttestExceptionHandler`:
  - `@ExceptionHandler(AttestationRefusedException.class) ProblemDetail onRefused(AttestationRefusedException e)` → `409`, title "Attestation refused", `detail = e.getMessage()`.
- `AttestController`:
  - `AttestController(AttestationService attestationService)`.
  - `@PostMapping ResponseEntity<AttestResponse> attest(@Valid @RequestBody AttestRequest request)` → `ResponseEntity.ok(attestationService.attest(request))`.
- `AttestationService`:
  - `AttestationService(QuorumDecisionService quorumDecisionService, WatchService watchService, ScreeningClient screeningClient, KmsSigner kmsSigner, AttestationRepository attestationRepository, Clock clock)`.
  - `public AttestResponse attest(AttestRequest request)` — see Execution shape below.
- `QuorumDecisionService` (modified) — add:
  - `public boolean isAgreed(String chain, String txHash, FactType factType)` → `repository.findByChainAndTxHashAndFactType(chain, txHash, factType).map(d -> d.outcome() == QuorumOutcome.AGREED).orElse(false)`.
- `ChainCursorRepository` (modified, package-private, unchanged visibility) — add:
  - `List<ChainCursor> findByChainAndTxHash(String chain, String txHash)`.
- `WatchService` (modified) — add:
  - `public List<ChainCursor> findChainCursors(String chain, String txHash)` → `chainCursorRepository.findByChainAndTxHash(chain, txHash)` (thin pass-through; the module's public seam, per this codebase's established "service reads through its own repository" convention — no `@Transactional` needed, a single read).

## Private methods

- `AttestationService`:
  - `private static final List<FactType> REQUIRED_FACTS = List.of(FactType.EXISTENCE, FactType.AMOUNT, FactType.TOKEN, FactType.FINALITY);`
  - `private void requireAllFactsAgreed(String chain, String txHash)` — loops `REQUIRED_FACTS`, throws `AttestationRefusedException` on the first `false`.
  - `private Set<String> distinctFromAddresses(List<ChainCursor> cursors)` — `cursors.stream().map(ChainCursor::fromAddress).filter(Objects::nonNull).collect(toCollection(LinkedHashSet::new))` (deterministic iteration order for tests; `LinkedHashSet` not `HashSet`).
  - `private void screenOrThrow(String chain, String txHash, Set<String> fromAddresses)` — iterates, calls `ScreeningClient.screen`, catches `RuntimeException` from the call itself and re-throws as `AttestationRefusedException` (persisting `REFUSED` first); returns the `reason` string immediately on the first `BLOCKED` (persists `BLOCKED`, then the caller returns `AttestResponse.blocked(reason)`); no early return needed for `CLEARED` (loop continues).
  - `private void persist(String chain, String txHash, String receiptDigest, AttestOutcome outcome, String kmsKeyId, Instant signedAt)` — builds and saves one `Attestation` row via `attestationRepository.save(Attestation.create(...))`, `createdAt = clock.instant()`.

## Execution shape of `AttestationService.attest(...)`

```
requireAllFactsAgreed(chain, txHash);                          // AttestationRefusedException on any gap

List<ChainCursor> cursors = watchService.findChainCursors(chain, txHash);
Set<String> fromAddresses = distinctFromAddresses(cursors);
if (fromAddresses.isEmpty()) {
    persist(..., AttestOutcome.REFUSED, null, null);
    throw new AttestationRefusedException(chain, txHash);
}

for (String fromAddress : fromAddresses) {
    ScreeningOutcome outcome;
    try {
        outcome = screeningClient.screen(chain, fromAddress, txHash);
    } catch (RuntimeException e) {
        persist(..., AttestOutcome.REFUSED, null, null);
        throw new AttestationRefusedException(chain, txHash);
    }
    if (outcome == ScreeningOutcome.BLOCKED) {
        persist(..., AttestOutcome.BLOCKED, null, null);
        return AttestResponse.blocked("counterparty address is sanctioned");
    }
    if (outcome == ScreeningOutcome.ERROR) {
        persist(..., AttestOutcome.REFUSED, null, null);
        throw new AttestationRefusedException(chain, txHash);
    }
}

byte[] digest = HexFormat.of().parseHex(request.receiptDigestSha256());
SignatureResult result = kmsSigner.sign(digest);                // uncaught on failure - no persist, 500

persist(..., AttestOutcome.SIGNED, result.kmsKeyId(), result.signedAt());
return AttestResponse.signed(result.signatureBase64(), result.kmsKeyId(), result.signedAt());
```
`HexFormat.of().parseHex(...)` is `java.util` (JDK 17+, no new dependency) — no existing usage in this
codebase yet to mirror, but it is the direct, standard-library counterpart to `Base64.getEncoder()`
already used by `KmsSigner`/`ScreeningResult`, so no third-party hex library is introduced.

## Entities used

`Attestation` (new), `ChainCursor` (existing, read-only via `WatchService`), `QuorumDecision` (existing,
read-only via `QuorumDecisionService`, never touched directly).

## Repositories used

`AttestationRepository` (new), `ChainCursorRepository` (existing, modified), `QuorumDecisionRepository`
(existing, unmodified — read still routed through `QuorumDecisionService`).

## Services used

`QuorumDecisionService`, `WatchService`, `ScreeningClient`, `KmsSigner`, `common.ClockConfig`'s `Clock`.

## Unit/integration tests required (per frozen brief's Required Tests — file placement)

- `attest/AttestOutcomeTest.java` — 3-value set + converter round-trip (mirrors `ScreeningOutcomeTest`).
- `attest/AttestationTest.java` — factory null-checks, nullable `kmsKeyId`/`signedAt` accepted.
- `attest/AttestationRepositoryIntegrationTest.java` — Testcontainers, real `crypto_app` role, append-only
  grant proof (mirrors `ScreeningResultRepositoryIntegrationTest`).
- `attest/AttestationServiceTest.java` — the bulk of the frozen brief's Required Tests: AC1-AC5, AC8,
  AC11 (mocked collaborators).
- `attest/AttestControllerTest.java` or a `@WebMvcTest`-style slice — AC9 (validation → 400), AC10 (JSON
  shape), and delegation to `AttestationService`.
- `attest/AttestExceptionHandlerTest.java` (or folded into the controller test) — 409 mapping.
- `quorum/QuorumDecisionServiceTest.java` (extended) — `isAgreed` true/false/absent cases.
- `watch/WatchServiceTest.java` (extended) — `findChainCursors` delegation.
- `watch/ChainCursorRepositoryIntegrationTest`-equivalent or a new focused integration test — the new
  `findByChainAndTxHash` query, including the multi-row case (AC8).
- `attest/KmsSignerArchitectureTest` — re-run only, no new test (AC6 regression).
- An `internal.crypto:write` scope-enforcement integration test for `/internal/v1/attest` (mirrors
  whatever the existing `/internal/v1/watches` equivalent test does, if one exists — confirmed at Phase 6
  by reading the actual test, not assumed here).

## Execution order

1. **Schema:** `V10` index migration (no dependency on anything else).
2. **Enum + converter:** `AttestOutcome`.
3. **Entity:** `Attestation` (depends on `AttestOutcome`).
4. **Repository:** `AttestationRepository` (depends on `Attestation`).
5. **DTOs:** `AttestRequest`, `AttestResponse` (no dependencies on the above).
6. **Exception + handler:** `AttestationRefusedException`, `AttestExceptionHandler`.
7. **Cross-module seams:** `QuorumDecisionService.isAgreed`, `ChainCursorRepository.findByChainAndTxHash`,
   `WatchService.findChainCursors` (each independent of the others; all needed before step 8).
8. **Service:** `AttestationService` (depends on everything above plus `ScreeningClient`/`KmsSigner`,
   already existing).
9. **Controller:** `AttestController` (depends on `AttestationService`).
10. **Tests**, in the same dependency order, `AttestOutcomeTest`/`AttestationTest` first, `AttestationServiceTest`
    before `AttestControllerTest`, `KmsSignerArchitectureTest` re-run last (mirrors this codebase's own
    "boundary/architecture test last" convention).

## Open Questions

No blockers (unchanged from the frozen brief; both disclosed limitations remain deferred, not blocking).
