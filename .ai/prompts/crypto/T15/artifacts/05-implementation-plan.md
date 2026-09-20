# crypto · T15 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN). No code written in this phase.

## Files to create

All trace directly to the frozen brief's "Files to Create" section — no additions.

1. `services/crypto/src/main/resources/db/migration/V6__crypto_app_watches_grant.sql`
2. `services/crypto/src/main/java/com/themistra/crypto/watch/WatchStatus.java`
3. `services/crypto/src/main/java/com/themistra/crypto/watch/Watch.java`
4. `services/crypto/src/main/java/com/themistra/crypto/watch/WatchRepository.java`
5. `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java`
6. `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursorRepository.java`
7. `services/crypto/src/main/java/com/themistra/crypto/watch/WatchNotFoundException.java`
8. `services/crypto/src/main/java/com/themistra/crypto/watch/InvalidWatchRequestException.java`
   (traces to the frozen brief's `WatchExceptionHandler` mapping needs — the brief names the handler
   file explicitly and requires a `400` mapping for semantic validation failures the DTO's own bean
   validation cannot express, e.g. `expiresAt`-in-the-future, `expectedAmount` format, address
   structural validity; this exception is the vehicle for that mapping)
9. `services/crypto/src/main/java/com/themistra/crypto/watch/WatchExceptionHandler.java`
10. `services/crypto/src/main/java/com/themistra/crypto/watch/dto/RegisterWatchRequest.java`
11. `services/crypto/src/main/java/com/themistra/crypto/watch/dto/RegisterWatchResponse.java`
12. `services/crypto/src/main/java/com/themistra/crypto/watch/WatchService.java`
13. `services/crypto/src/main/java/com/themistra/crypto/watch/WatchController.java`

## Files to modify

- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  remove `watches`/`chain_cursors` from `UNGRANTED_TABLES`.

## Public methods (signatures)

**`WatchStatus` (enum):** `REGISTERED`, `UNREGISTERED`, `EXPIRED`.

**`Watch` (`@Entity`, table `watches`):**
```java
public static Watch register(UUID watchId, UUID invoiceUuid, String chain, String address,
        String tokenContractAddress, BigDecimal expectedAmount, Instant expiresAt, Instant now);
public UUID getWatchId();
public UUID getInvoiceUuid();
public String getChain();
public String getAddress();
public String getTokenContractAddress();
public BigDecimal getExpectedAmount();
public WatchStatus getStatus();
public Instant getExpiresAt();
public Instant getCreatedAt();
public Instant getUnregisteredAt();
```
No instance mutator — the `DELETE` transition is a repository-level atomic conditional `UPDATE`
(Phase 3 Finding 4), not a load-then-save entity mutation, so `Watch` needs no `unregister()` method.

**`WatchRepository` (package-private interface `extends JpaRepository<Watch, Long>`):**
```java
Optional<Watch> findByWatchId(UUID watchId);
boolean existsByWatchId(UUID watchId);

@Modifying
@Query("UPDATE Watch w SET w.status = com.themistra.crypto.watch.WatchStatus.UNREGISTERED, "
        + "w.unregisteredAt = :now WHERE w.watchId = :watchId "
        + "AND w.status = com.themistra.crypto.watch.WatchStatus.REGISTERED")
int markUnregisteredIfRegistered(@Param("watchId") UUID watchId, @Param("now") Instant now);
```
(`findByWatchId` is used by `WatchService.register`'s test setup and by the repository-level integration
test; `WatchService.unregister` itself uses `existsByWatchId` for the 404 check.)

**`ChainCursor` (`@Entity`, table `chain_cursors`):**
```java
public static ChainCursor placeholder(String chain, UUID watchId, Instant now); // lastBlock = -1L, lastFinalizedBlock = null
public String getChain();
public UUID getWatchId();
public long getLastBlock();
public Long getLastFinalizedBlock();
public Instant getUpdatedAt();
```

**`ChainCursorRepository` (package-private interface `extends JpaRepository<ChainCursor, Long>`):** no
additional methods — `save` (inherited) is all this task needs.

**`WatchNotFoundException` (package-private, `extends RuntimeException`):**
```java
WatchNotFoundException(UUID watchId); // message: "No watch found for watchId: " + watchId
```

**`InvalidWatchRequestException` (package-private, `extends RuntimeException`):**
```java
InvalidWatchRequestException(String message);
```

**`WatchExceptionHandler` (package-private, `@RestControllerAdvice`, `@Order(Ordered.HIGHEST_PRECEDENCE)`):**
```java
@ExceptionHandler(WatchNotFoundException.class)
ProblemDetail onNotFound(WatchNotFoundException e);       // 404

@ExceptionHandler(InvalidWatchRequestException.class)
ProblemDetail onInvalidRequest(InvalidWatchRequestException e); // 400, detail = e.getMessage() (a
                                                                  // caller-facing validation reason,
                                                                  // never internal/stack-trace detail —
                                                                  // mirrors ResourceServerConfig's own
                                                                  // 401/403 detail-message precedent)
```

**`RegisterWatchRequest` (public record, `dto` subpackage):**
```java
public record RegisterWatchRequest(
        @NotNull UUID invoiceUuid,
        @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
        @NotBlank @Size(max = 128) String address,
        @NotBlank @Size(max = 128) String tokenContractAddress,
        @NotBlank String expectedAmount,
        @NotNull Instant expiresAt) {
}
```

**`RegisterWatchResponse` (public record, `dto` subpackage):**
```java
public record RegisterWatchResponse(UUID watchId, String status) {
}
```

**`WatchService` (`@Service`):**
```java
@Transactional
public Watch register(RegisterWatchRequest request);  // throws InvalidWatchRequestException

@Transactional
public void unregister(UUID watchId);                 // throws WatchNotFoundException
```

**`WatchController` (`@RestController`, `@RequestMapping("/internal/v1/watches")`):**
```java
@PostMapping
public ResponseEntity<RegisterWatchResponse> register(@Valid @RequestBody RegisterWatchRequest request);

@DeleteMapping("/{watchId}")
public ResponseEntity<Void> unregister(@PathVariable UUID watchId);
```

## Private methods

**`WatchService`:**
```java
private BigDecimal parseExpectedAmount(String raw);   // ^[1-9][0-9]*$ only; throws InvalidWatchRequestException otherwise (Finding 2)
private void validateExpiresAt(Instant expiresAt);     // must be after clock.instant(); throws InvalidWatchRequestException otherwise
private void validateAddress(String chain, String address); // dispatches to AddressValidator.isValidEvmAddress/isValidTronAddress by chain (already bean-validated to ETHEREUM|TRON); throws InvalidWatchRequestException on failure (Finding 1)
```
No private helpers needed in `Watch`/`ChainCursor` (single-expression factories) or `WatchController`
(pure delegation to `WatchService`).

## Entities used

- `Watch` (new)
- `ChainCursor` (new)

## Repositories used

- `WatchRepository` (new)
- `ChainCursorRepository` (new)

## Services used

- `com.themistra.crypto.token.AddressValidator` (T12, existing — this task is its first real caller).
- `java.time.Clock` (`common.ClockConfig`, existing).

## Unit/integration tests required

(Test files are not created in this phase — Phase 6's own directive defers all test-writing to Phase
10; listed here only to confirm every planned test traces to the frozen brief's Required Tests.)

**`WatchTest`** (unit) — `register(...)` factory assigns every field correctly, `status = REGISTERED`,
`unregisteredAt = null`.

**`ChainCursorTest`** (unit) — `placeholder(...)` factory assigns `lastBlock = -1L`,
`lastFinalizedBlock = null`, `watchId`/`chain` as given.

**`WatchServiceTest`** (unit, mocked `WatchRepository`/`ChainCursorRepository`/`AddressValidator`, fixed
`Clock`):
- `shouldRegisterWatchAndReturnWatchId` (named test, AC1) — valid request saves both entities, returns
  a `Watch` with a fresh `watchId`.
- `ChainCursor` saved with `watch_id`/`chain` matching the new `Watch` (AC2).
- `shouldUnregisterWatchOnDelete` (named test, AC3) — existing `REGISTERED` watch, `unregister` calls
  the conditional-update repository method.
- `unregister` on an unknown `watchId` throws `WatchNotFoundException` (AC4).
- Two sequential `register` calls (same request) produce two distinct `watchId`s, two `save` calls each
  on both repositories (Finding 3).
- `register` rejects: missing/blank `expectedAmount` format failures (`"1.5"`, `"1e18"`, `"-0"`, `"0"`),
  a past `expiresAt`, and a structurally invalid `address`/`tokenContractAddress` for the given chain
  (mocked `AddressValidator` returning `false`) — each throws `InvalidWatchRequestException` (AC5/AC6).

**`WatchControllerTest`** (`@WebMvcTest(WatchController.class)`, mocked `WatchService`, real bean
validation via the actual `RegisterWatchRequest` type):
- `POST` with a valid body → `200` with the VERBATIM response shape.
- `POST` with each structurally-invalid-per-bean-validation body (missing field, bad `chain` pattern,
  oversize `address`) → `400` (first empirical check of Spring Boot 3.5.4's default `@Valid` failure
  response shape, per the frozen brief's noted verification constraint).
- `DELETE` with a valid `watchId` (mocked service success) → `204`.
- `DELETE` mapped to a mocked `WatchNotFoundException` → `404` via `WatchExceptionHandler`.

**`WatchRepositoryIntegrationTest`** / **`ChainCursorRepositoryIntegrationTest`** (Testcontainers
Postgres, mirrors T10/T11 precedent):
- Real persistence round-trip for both entities.
- `markUnregisteredIfRegistered` actually transitions a `REGISTERED` row and is a no-op (0 rows
  affected) against an `UNREGISTERED` or manually-seeded `EXPIRED` row (Finding 4/6) — AC4.
- The `chk_watch_status` DB constraint still rejects an out-of-range status if bypassed at the SQL level
  (defense-in-depth already provided by the schema itself, confirmed not broken by the entity mapping).

**`ChainBaselineMigrationIntegrationTest`** (modified, not created) — `watches`/`chain_cursors` removed
from `UNGRANTED_TABLES`; `crypto_app`'s actual grants on both tables asserted to match AC7 exactly
(`INSERT, SELECT, UPDATE` / `INSERT, SELECT`).

**`WatchModuleBoundaryTest`** (new, source-scan, mirrors T10/T11/T14 precedent) — `watch/` imports
nothing from `observation`, `provider`, `quorum`, `finality`, `events`, or `adapter`; the one exception
is an exact allow-listed `import com.themistra.crypto.token.AddressValidator;` (AC8).

## Execution order

1. `V6__crypto_app_watches_grant.sql` — schema/grant first (frozen brief's own front-loading directive).
2. `WatchStatus.java` — no dependents yet.
3. `Watch.java` — depends on `WatchStatus`.
4. `WatchRepository.java` — depends on `Watch`.
5. `ChainCursor.java` — no dependency on `Watch`/`WatchStatus`.
6. `ChainCursorRepository.java` — depends on `ChainCursor`.
7. `WatchNotFoundException.java`, `InvalidWatchRequestException.java` — no dependencies.
8. `WatchExceptionHandler.java` — depends on both exception types.
9. `dto/RegisterWatchRequest.java`, `dto/RegisterWatchResponse.java` — no dependency on the entities.
10. `WatchService.java` — depends on both repositories, both entities, both exceptions, both DTOs,
    `AddressValidator`, `Clock`.
11. `WatchController.java` — depends on `WatchService` and both DTOs.
12. `mvn -pl services/crypto compile` — confirm the full set compiles cleanly.
13. (Phase 7 self-review, then Phase 10) test files, in the same dependency order as their production
    counterparts, `ChainBaselineMigrationIntegrationTest`'s modification last (it asserts against the
    finished grant migration), `WatchModuleBoundaryTest` last of all (it scans the finished package).
