<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# crypto · T23 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T23 — Contracts |
| **Model** | Kimi 2.7 |
| **Consumed** | `artifacts/04-frozen-task-brief.md` · `artifacts/05-implementation-plan.md` · `artifacts/06-implementation-notes.md` · `artifacts/07-self-review.md` · `spec/crypto-service/agents.md` · Phase 6 implementation |
| **Produces** | `artifacts/08-independent-review.md` |

## Summary

The Phase 6 implementation **does not contain the primary deliverables of T23**: `contracts/api/crypto-internal.yaml` and the five `contracts/events/chain/*.v1.schema.json` files are absent from both the working tree and the git index/history. Only the contract tests and the `pom.xml` dependency addition were committed. Consequently, every acceptance criterion that depends on those files (AC1–AC6) is currently unprovable, and the Phase 7 self-review's claim that "all 6 contract files were re-verified" is factually inconsistent with the repository state.

The test code itself is structurally sound and faithfully mirrors the auth precedent, but it cannot execute against files that do not exist.

## Findings

### 1. Primary deliverables missing from the repository (CRITICAL)

- **Issue:** `contracts/api/crypto-internal.yaml` and the five `contracts/events/chain/*.v1.schema.json` files (`tx-seen`, `tx-confirmed`, `tx-finalized`, `tx-reorged`, `provider-degraded`) are not present in the working tree, are not tracked by git, and do not appear in any commit reachable from `HEAD`.
- **Evidence:**
  - `find /Users/oluwafemi.ayeni/personal/checky-pro -type f -name "crypto-internal.yaml"` returns nothing.
  - `find contracts -type f` lists only `contracts/api/auth.yaml` and the three `contracts/events/auth/*.schema.json` files.
  - `git ls-files | grep -E "contracts/(api/crypto-internal|events/chain)"` returns nothing.
  - `git ls-files --others --exclude-standard` shows no untracked contract files.
  - The implementation commit (`79e2a44`) added only test files, `pom.xml`, and Phase 6/7 artifacts; the preceding "finish t23 cs" commit (`396431a`) added only planning artifacts.
- **Recommendation:** Create the six missing contract files exactly per the frozen brief and Phase 5 implementation plan, run the full T23 test suite (`CryptoInternalOpenApiContractTest`, `*PayloadContractTest`, `MoneyFieldsAreDecimalStringsContractTest`), and commit them before this task can be considered complete.
- **Confidence:** Certain.

### 2. Phase 7 self-review is inconsistent with repository state (HIGH)

- **Issue:** `artifacts/07-self-review.md` states "No critical or functional defects were found" and claims the author "spot-checked `provider-degraded.v1.schema.json`'s `chain`/`reason` enums ... once more." That file does not exist, so the spot-check could not have occurred against the committed code.
- **Evidence:** `artifacts/07-self-review.md` lines 5, 40–43; `contracts/events/chain/provider-degraded.v1.schema.json` is absent.
- **Recommendation:** Re-run Phase 7 self-review after the missing contract files are created and the tests actually pass; update or replace the self-review artifact so its claims are verifiable against committed files.
- **Confidence:** High.

### 3. `tx-finalized` verbatim-copy requirement will document fields the real payload never emits (MEDIUM)

- **Issue:** The frozen brief requires `tx-finalized.v1.schema.json` be copied "byte-for-byte" from `design.md` §4c. That schema declares optional properties `confirmations` (integer) and `addressPoisoningFlag` (boolean). The real `TxLifecyclePublisher.FinalizedPayload` record has neither field, so those properties will never appear in any serialized event. The contract test (`FinalizedPayloadContractTest`) only asserts that required fields are present and that serialized fields are declared; it does **not** assert that every declared property is actually emitted. A verbatim copy will therefore pass the test while misrepresenting the real wire shape.
- **Evidence:** `spec/crypto-service/design.md` lines 242–255; `services/crypto/src/main/java/com/themistra/crypto/watch/TxLifecyclePublisher.java` lines 128–130; `services/crypto/src/test/java/com/themistra/crypto/watch/FinalizedPayloadContractTest.java` lines 36–46.
- **Recommendation:** Decide whether the contract is retrospective (document what is actually emitted) or prospective (document the intended design). If retrospective, omit `confirmations` and `addressPoisoningFlag` from the `tx-finalized` schema and update the frozen brief's "verbatim" instruction; if prospective, add the fields to `FinalizedPayload` and the publisher. Either way, make the choice explicit in Phase 9.
- **Confidence:** High.

### 4. `watchId` path-parameter type/format is not verified (LOW)

- **Issue:** `CryptoInternalOpenApiContractTest.watchIdPathParameterIsDeclaredOnTheDeleteOperation` checks only that a parameter named `watchId` exists, is `in: path`, and `required: true`. The frozen brief requires it to declare `type: string, format: uuid`; the test does not inspect `schema.type` or `schema.format`.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java` lines 121–132; frozen brief §Scope ("explicit `watchId` path parameter — `type: string`, `format: uuid`").
- **Recommendation:** Extend the assertion to verify `p.get("schema").get("type").asText().equals("string")` and `p.get("schema").get("format").asText().equals("uuid")`.
- **Confidence:** High.

### 5. Unused `PathVariable` import (LOW, cosmetic)

- **Issue:** `CryptoInternalOpenApiContractTest` imports `org.springframework.web.bind.annotation.PathVariable` but never references it.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java` line 18.
- **Recommendation:** Remove the import.
- **Confidence:** Certain.

### 6. Missing `controllerRoutes()` limitation disclosure (LOW, documentation)

- **Issue:** `CryptoInternalOpenApiContractTest.controllerRoutes()` copies the same reflection logic as `AuthOpenApiContractTest.controllerRoutes()` but omits the Javadoc disclosure about bare `@RequestMapping` handlers silently contributing zero routes. The limitation is dormant today because all three controllers use method-fixing shorthand annotations, but the inherited context is lost.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java` lines 313–331; contrast `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` lines 418–425.
- **Recommendation:** Add the disclosure comment, adapted to name `WatchController`, `AttestController`, and `VerificationKeysController`.
- **Confidence:** High.

## Open Questions

- Why were the six contract files omitted from the implementation commit despite `artifacts/06-implementation-notes.md` asserting they were created and that 18/18 tests passed?
