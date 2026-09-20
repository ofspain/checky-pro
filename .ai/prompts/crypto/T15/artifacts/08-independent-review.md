# crypto · T15 · Phase 8 — Independent Code Review Findings

Reviewed: all files in `services/crypto/src/main/java/com/themistra/crypto/watch/`,
`services/crypto/src/main/resources/db/migration/V6__crypto_app_watches_grant.sql`,
`services/auth/src/main/java/com/themistra/auth/common/ApiExceptionHandler.java`,
`artifacts/07-self-review.md`, and `artifacts/04-frozen-task-brief.md`.

No correctness, transaction-atomicity, or module-boundary defects found in the core register/unregister
logic. The atomic conditional `UPDATE` for unregister is race-safe, the `AddressValidator`/Clock wiring
matches the frozen brief, and the grant migration follows the established T10/T11 precedent. The
findings below are unhandled framework-level error paths, latent mapping gaps, and validation bounds.

---

### 1. Framework-level validation errors are not mapped to RFC 9457 problem+json

**Issue:** `WatchExceptionHandler` only handles `WatchNotFoundException` and
`InvalidWatchRequestException`. There is no global `ApiExceptionHandler` in `services/crypto` mirroring
`services/auth`'s `ApiExceptionHandler`. As a result, `@Valid` failures on `RegisterWatchRequest`
(`MethodArgumentNotValidException`), a malformed JSON body (`HttpMessageNotReadableException`), an
invalid UUID in the `DELETE` path (`MethodArgumentTypeMismatchException`), or any unexpected exception
fall through to Spring Boot's default handling. That default may not produce the `application/problem+json`
body required by `agents.md` Security rule.

**Evidence:** `watch/WatchExceptionHandler.java:18-31`; comparison to
`services/auth/src/main/java/com/themistra/auth/common/ApiExceptionHandler.java:44-79`;
`agents.md:48` (errors are RFC 9457 `application/problem+json`).

**Recommendation:** Add a crypto-global `ApiExceptionHandler` ordered `@Order(Ordered.LOWEST_PRECEDENCE)`
that maps `MethodArgumentNotValidException`, `ConstraintViolationException`,
`HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, and `Exception` to
`ProblemDetail`, mirroring auth's proven shape. Verify in `WatchControllerTest` that a missing field,
an unparseable `expiresAt`, an invalid path UUID, and a syntactically invalid JSON body all return
`application/problem+json` with no stack trace.

**Confidence:** High.

---

### 2. `expectedAmount` regex has no upper bound on digit count

**Issue:** `EXPECTED_AMOUNT_PATTERN = "^[1-9][0-9]*$"` accepts an arbitrarily long positive integer. The
DDL stores `expected_amount NUMERIC(78, 0)`, so a value with more than 78 significant digits passes the
application-level validation and only fails at the database, turning what should be a `400` into a `500`.

**Evidence:** `watch/WatchService.java:21`; `V1__chain_baseline.sql:14`
(`expected_amount NUMERIC(78, 0) NOT NULL`).

**Recommendation:** Cap the regex at the DDL precision: `"^[1-9][0-9]{0,77}$"`. Add a test asserting
that a 79-digit `expectedAmount` returns `400` and that the 78-digit boundary is accepted.

**Confidence:** High.

---

### 3. `ChainCursor` entity mapping does not enforce `watch_id` as non-null

**Issue:** `ChainCursor.watchId` is mapped with `@Column(name = "watch_id")` but no `nullable = false`.
The DDL likewise omits `NOT NULL` on `chain_cursors.watch_id`, so the only guarantee that the cursor row
is tied to a watch is the application factory. A bug or a future raw insert could create an orphan cursor.

**Evidence:** `watch/ChainCursor.java:42-43`; `V1__chain_baseline.sql:66`.

**Recommendation:** Since the DDL is frozen, add `nullable = false` to the `@Column` annotation so that
JPA's `ddl-auto=validate` mapping check reflects the brief's required 1:1 relationship and rejects any
attempt to persist a cursor with a null `watchId`.

**Confidence:** Medium.

---

### 4. No database-level unique constraint enforces the 1:1 Watch↔ChainCursor relationship

**Issue:** The frozen brief requires exactly one `ChainCursor` row per `Watch` (AC2), but neither the DDL
nor the JPA entity declares a unique constraint on `chain_cursors.watch_id`. The current service code
inserts exactly one cursor per registration, but nothing in the schema prevents a future caller or bug
from inserting a second cursor for the same watch.

**Evidence:** `watch/ChainCursor.java:42-43`; `V1__chain_baseline.sql:63-70`; frozen brief AC2.

**Recommendation:** Because `V1__chain_baseline.sql` is VERBATIM/frozen, the constraint cannot be added
here. Document the risk in the module's README or Javadoc, and add a source-level guard/test that
`WatchService.register` calls `chainCursorRepository.save` exactly once per successful registration. In a
future schema revision (a new Flyway migration, not a change to V1), add a unique constraint on
`chain_cursors(watch_id)`.

**Confidence:** Low.

---

### 5. `@Modifying` query does not clear the persistence context

**Issue:** `WatchRepository.markUnregisteredIfRegistered` is a bulk JPQL `UPDATE` without
`clearAutomatically = true`. No current code path re-reads the same `Watch` in the same transaction, but
a future caller that does so would see a stale, pre-update cached entity.

**Evidence:** `watch/WatchRepository.java:24-28`.

**Recommendation:** Add `clearAutomatically = true` to the `@Modifying` annotation. This is a one-line,
zero-behavioral-change fix for current code that closes the latent stale-read trap.

**Confidence:** Medium.

---

### 6. `WatchExceptionHandler` is package-private, deviating from the auth precedent

**Issue:** `WatchExceptionHandler` is declared as a package-private class, while the one established
precedent for a per-module `@RestControllerAdvice` in this repository
(`services/auth/.../apikey/ApiKeyExceptionHandler.java`) is `public`. Spring's classpath scanning does
not require `public`, but the deviation is itself undocumented and was flagged in the self-review as
unverified.

**Evidence:** `watch/WatchExceptionHandler.java:16`; `ApiKeyExceptionHandler.java:21`.

**Recommendation:** Make the class `public` to match the established precedent and remove any lingering
uncertainty about Spring component discovery at zero cost.

**Confidence:** Low.

---

### 7. Mixed-case EVM addresses may mismatch the lowercase token-allowlist store

**Issue:** `AddressValidator.isValidEvmAddress` requires a correctly checksummed (mixed-case) address and
does not normalize to lowercase. `WatchService.register` stores the address and `tokenContractAddress`
as provided. T11's `TokenAllowlist` stores EVM contract addresses lowercase and matches exact-string
with no case-folding, so a future watcher that looks up a checksummed `tokenContractAddress` against the
allowlist could incorrectly report `UNKNOWN_TOKEN`.

**Evidence:** `token/AddressValidator.java:19-25` and `:56-63`; frozen brief Dependencies/Out (`:59-60`,
`:96-100`) defers `TokenValidator`/allowlist checking to a future task.

**Recommendation:** This is explicitly out of scope for T15, but the brief should add a forward-looking
note (e.g., in `ChainCursor` or `Watch` Javadoc) that a future consumer must lowercase EVM addresses
after validation and before allowlist lookup, or that the stored address should be normalized at that
future boundary.

**Confidence:** Low.

---

### 8. Invalid `DELETE` path UUID is not handled as a 400

**Issue:** `DELETE /internal/v1/watches/{watchId}` binds `watchId` as `@PathVariable UUID watchId`. A
non-UUID value such as `"not-a-uuid"` throws `MethodArgumentTypeMismatchException`, which is not handled
by `WatchExceptionHandler`. This is a specific instance of Finding 1 but is called out because the brief
explicitly documents only two `DELETE` outcomes (`204` and `404`) and omits the malformed-path-variable
`400` case.

**Evidence:** `watch/WatchController.java:36`; `watch/WatchExceptionHandler.java:18-31`.

**Recommendation:** Either add a handler for `MethodArgumentTypeMismatchException` in the global error
handler (Finding 1) or, as a narrower fix, annotate the path variable with a regex pattern and a custom
error mapping. A `WatchControllerTest` should assert that `DELETE /internal/v1/watches/invalid-uuid`
returns `400 application/problem+json`.

**Confidence:** Medium.
