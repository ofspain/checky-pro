# crypto · T15 · Phase 11 — Test Review Findings

Reviewed: `WatchTest.java`, `ChainCursorTest.java`, `WatchServiceTest.java`,
`WatchControllerTest.java`, `WatchRepositoryIntegrationTest.java`,
`WatchModuleBoundaryTest.java`, `artifacts/10-test-generation.md`, and the frozen brief's Required Tests.

The test suite covers both named tests, every AC, and all Phase 3/8 findings. 46/46 pass. The gaps
below are strengthening opportunities rather than missing AC coverage.

---

### 1. Invalid `POST` tests do not verify that no repository writes occur

**Gap:** `WatchServiceTest`'s parameterized `registerRejectsAMalformedExpectedAmount`, the address-
validity tests, and the `expiresAt` tests all assert that an exception is thrown, but none verify that
`watchRepository.save` and `chainCursorRepository.save` are never invoked. A bug that validated first
and wrote later (or wrote before throwing) would leave these tests green.

**Why it matters:** AC5/AC6 explicitly require "no rows written" on validation failure. The current
tests verify the caller-visible rejection but not the persistence side effect.

**Suggested test:** Add `verifyNoInteractions(watchRepository, chainCursorRepository)` to one
representative invalid `expectedAmount`, one invalid address, and one past-`expiresAt` test. Alternatively,
add a dedicated test `registerDoesNotPersistAnythingWhenValidationFails` that triggers a single failure
and asserts no save calls.

---

### 2. Grant tests do not explicitly exercise the `SELECT` privilege

**Gap:** `WatchRepositoryIntegrationTest.cryptoAppCanInsertSelectAndUpdateButNotDeleteOnWatches`
performs an `INSERT` and an `UPDATE`, then asserts `DELETE` is denied. The AC7 grant is `INSERT, SELECT,
UPDATE` on `watches`, but the test never issues a raw `SELECT` as `crypto_app`. The same applies to the
`chain_cursors` grant test (`INSERT, SELECT`).

**Why it matters:** A migration that accidentally omitted `SELECT` would still pass these tests, because
the repository-level SELECTs happen through Spring Data (which connects as `crypto_app`) only in the
separate flow tests, not in the explicit grant assertions.

**Suggested test:** In both grant tests, add an explicit `SELECT 1 FROM chain.watches WHERE watch_id = ...`
(as `crypto_app`) and assert it succeeds. For `chain_cursors`, add a corresponding `SELECT`.

---

### 3. Two controller-level `400` cases do not assert the problem+json body

**Gap:** `postWithAnUnrecognizedChainReturnsBadRequest` and `postWithAnOversizeAddressReturnsBadRequest`
only assert `status().isBadRequest()`. Both are bean-validation failures handled by `ApiExceptionHandler`
and should produce `application/problem+json`.

**Why it matters:** These tests would pass even if Spring's default HTML error page were returned. The
Phase 8/9 work to add `ApiExceptionHandler` exists specifically to guarantee problem+json, so the tests
should lock that in.

**Suggested test:** Add `.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))`
and `.andExpect(jsonPath("$.title").value("Validation failed"))` to both tests.

---

### 4. No controller test for an unparseable `expiresAt` value

**Gap:** `postWithMalformedJsonReturnsProblemJsonMalformedBody` covers syntactically invalid JSON, but
there is no test for a well-formed JSON body where `expiresAt` is a string that Jackson cannot parse as
an `Instant` (e.g., `"tomorrow"` or `"2026-01-01"`). This triggers `HttpMessageNotReadableException` via a
different code path than malformed JSON.

**Why it matters:** `ApiExceptionHandler.onUnreadableBody` is supposed to map both malformed JSON and
unparseable field values to the same `400 application/problem+json` shape. Only one of those paths is
currently exercised.

**Suggested test:** Add `postWithAnUnparseableExpiresAtReturnsProblemJsonMalformedBody` with body
`{ "invoiceUuid": "...", "chain": "ETHEREUM", "address": "...", "tokenContractAddress": "...",
"expectedAmount": "1000000", "expiresAt": "not-an-instant" }` and assert `400` problem+json with title
"Malformed request body".

---

### 5. No controller test for an invalid `tokenContractAddress`

**Gap:** `postMappedToAnInvalidWatchRequestExceptionReturnsProblemJsonBadRequest` mocks the service to
throw `InvalidWatchRequestException`, but it does not use a real invalid `tokenContractAddress` to drive
the failure end-to-end. The service test covers `tokenContractAddress` validation, but the controller-
handler integration for that specific error message is not exercised.

**Why it matters:** The response detail for a bad `tokenContractAddress` differs from a bad `address`;
a future regression in how `InvalidWatchRequestException` messages are propagated would not be caught.

**Suggested test:** Add `postWithAnInvalidTokenContractAddressReturnsProblemJsonBadRequest` with a real
EVM address for `address` and an unchecksummed/lowercase address for `tokenContractAddress`, then assert
`400` problem+json with a detail containing `tokenContractAddress` or the expected validation message.

---

### 6. No test asserts `Watch.createdAt` and `ChainCursor.updatedAt` come from the same Clock instant

**Gap:** `WatchServiceTest` asserts that the cursor has the correct `watchId`, `chain`, `lastBlock`, and
`lastFinalizedBlock`, and that the watch has `status = REGISTERED`, but it does not assert that the
`updatedAt` timestamp on the cursor equals the `createdAt` timestamp on the watch. Both are derived from
`clock.instant()` in `WatchService.register`.

**Why it matters:** A future refactor that called `clock.instant()` twice could produce two different
instants (especially with a real clock), breaking the implicit guarantee that both rows were written in
the same transaction at the same moment.

**Suggested test:** In `registerPersistsAChainCursorWithTheMatchingWatchIdAndChain`, capture the returned
`Watch`, then assert `cursor.updatedAt().equals(watch.createdAt())`.

---

### 7. Module-boundary scan does not catch fully-qualified inline references or static imports

**Gap:** `WatchModuleBoundaryTest` scans only lines that start with `import `. It would miss a
fully-qualified reference such as `com.themistra.crypto.provider.SomeUtil.doWork()` used without an
import, and it would miss a forbidden static import because those lines begin with `import static `.

**Why it matters:** The scan's contract is "nothing in `watch/` depends on forbidden packages", not merely
"no regular import statements". A future addition could sneak in via an inline reference or static import.

**Suggested test:** Extend the scan to read the entire file content and assert no occurrence of any
fully-forbidden package prefix, while still allow-listing the permitted `token.AddressValidator` import.

---

### 8. No service test for an invalid Tron `tokenContractAddress`

**Gap:** `WatchServiceTest` has `registerRejectsAStructurallyInvalidTokenContractAddress` for an EVM
address, but no equivalent for Tron. The address-validation dispatch is chain-specific, so the Tron path
for `tokenContractAddress` is not directly covered.

**Why it matters:** A regression in how the chain value is passed to `AddressValidator` for the second
address field on Tron would be missed.

**Suggested test:** Add `registerRejectsAnInvalidTronTokenContractAddress` with a valid Tron `address` and
an invalid Tron `tokenContractAddress`, asserting `InvalidWatchRequestException`.
