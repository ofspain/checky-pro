# crypto · T19 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T19 — Screening client |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Review of the Phase 2 Task Implementation Brief (TIB). Findings are adversarial only; no redesign or implementation is proposed.

---

## 1. Fail-closed stub returns `ERROR`, not `BLOCKED`, creating an ambiguity for task 21

- **Issue:** `FailClosedScreeningClient` returns `ERROR` for every input. While this satisfies "never `CLEARED`," it does not satisfy R21's specific wording: a sanctioned/OFAC hit SHALL return `outcome: BLOCKED`. A future `AttestationService` that simply switches on `ScreeningOutcome` will therefore never emit the R21-mandated `BLOCKED` response until a real vendor is wired. Worse, `ERROR` could be interpreted by task 21 as a transient/vendor-unreachable state, potentially triggering a retry rather than a hard refusal.
- **Severity:** High
- **Evidence:** TIB lines 27-31; `spec/crypto-service/requirements.md` §R21; `spec/crypto-service/design.md` L12.
- **Recommended brief amendment:** Either (a) change `FailClosedScreeningClient` to return `BLOCKED` for every input and document that it cannot distinguish sanctioned from unreachable, so `BLOCKED` is the safe fail-closed value, or (b) keep `ERROR` but add a Locked Decision stating that `ERROR` from any `ScreeningClient` implementation is treated as a hard fail-closed refusal (no signature, no retry) by task 21's `AttestationService`. Option (a) aligns better with R21's explicit response shape.

## 2. The `ScreeningClient` interface cannot enforce its "persist one row per call" contract

- **Issue:** The interface Javadoc states that every implementation must persist exactly one `ScreeningResult` row before returning, but the interface has no mechanism to enforce this. A future real-vendor implementation could forget the persistence step, breaking the audit-trail requirement (L12) silently.
- **Severity:** Medium
- **Evidence:** TIB lines 20-26; `spec/crypto-service/agents.md` L12 (screening gates attestation, fail-closed).
- **Recommended brief amendment:** Move persistence out of the interface's honor-system contract and into a decorator or template wrapper that always persists after the implementation returns an outcome. If that is out of scope, add an AC that the module boundary test must verify that every implementation class in `screening/` imports `ScreeningResultRepository` and that `ScreeningClientTest`-style tests must be added for every future implementation.

## 3. No `reason` field or return value, despite R21's `{ outcome, reason }` response shape

- **Issue:** `ScreeningClient.screen(...)` returns only a `ScreeningOutcome`. R21's `/attest` response includes a `reason` when the outcome is `BLOCKED`. The `ScreeningResult` entity also has no `reason` column. Task 21 will therefore have no structured source for the `reason` string unless it hardcodes one.
- **Severity:** Medium
- **Evidence:** TIB lines 20-22, 32-38; `spec/crypto-service/requirements.md` §R21; `spec/crypto-service/design.md` §4c `POST /internal/v1/attest` response shape.
- **Recommended brief amendment:** Either add a `reason` field to the `ScreeningResult` table/entity (new migration, though the brief says no new columns) and a `String reason()` accessor on the interface, or explicitly document that the stub returns a fixed `reason` string and task 21 will source `reason` from a hardcoded mapping. Given V1 is frozen, the latter is simpler but should be written down.

## 4. Undefined behavior when `ScreeningResult` persistence itself fails

- **Issue:** The stub saves a row and then returns `ERROR`. If the repository save throws (DB unavailable), the exception propagates. It is unclear whether task 21 should treat an exception from `screen(...)` as fail-closed or as an internal error. The audit-trail row would also be missing in this case.
- **Severity:** Medium
- **Evidence:** TIB lines 27-31; `spec/crypto-service/agents.md` L12 (screening fails closed).
- **Recommended brief amendment:** Add a Locked Decision: "Any exception thrown by `ScreeningClient.screen(...)` (including a failed `ScreeningResult` persistence) is treated as fail-closed by the caller: no signature is produced." Alternatively, wrap save failures inside `screen(...)` and return `ERROR` if persistence fails, but then document that the audit trail may be absent in that path.

## 5. `FailClosedScreeningClient` accepts any address string without validation

- **Issue:** The stub (and future implementations) accept whatever `String` is passed as `address`. Garbage or malformed addresses will be persisted and screened. While `token.AddressValidator` owns format validation, `ScreeningClient` has no call site into it and no defensive check for obviously invalid inputs.
- **Severity:** Low
- **Evidence:** TIB lines 67-68; `spec/crypto-service/agents.md` L8 (address validation is mandatory).
- **Recommended brief amendment:** Document that `ScreeningClient` performs no address-format validation; callers (task 21's `AttestationService`) must validate the address via `token.AddressValidator` before calling `screen(...)`. Add a test that verifies malformed addresses are persisted as-is (i.e., the component does not reject them).

## 6. No explicit test locks in the three-value `ScreeningOutcome` enum against the DDL

- **Issue:** AC1 requires `ScreeningOutcome` to have exactly `CLEARED`, `BLOCKED`, `ERROR` matching the DDL CHECK constraint. The brief's required tests do not explicitly enumerate or assert the enum values; a future refactoring could rename or add a value and pass the existing tests.
- **Severity:** Low
- **Evidence:** TIB AC1 lines 134-135; Required Tests lines 154-164.
- **Recommended brief amendment:** Add a required test (e.g., `ScreeningOutcomeTest`) that asserts `ScreeningOutcome.values()` contains exactly those three constants and that their `name()` values match the DDL CHECK constraint strings.

## 7. The stub's hardcoded `provider = fail-closed-stub` is not documented as a public constant

- **Issue:** `FailClosedScreeningClient` hardcodes the provider string `fail-closed-stub`. This value will appear in `screening_results.provider` and could be matched on by future queries or dashboards. It is currently a private implementation detail.
- **Severity:** Low
- **Evidence:** TIB lines 29-30.
- **Recommended brief amendment:** Expose the provider string as a public constant (e.g., `FailClosedScreeningClient.PROVIDER_NAME`) and test that the persisted `ScreeningResult.provider()` equals it. This prevents silent renames that would break downstream analytics.

## 8. No logging in the stub, making it invisible in production

- **Issue:** `FailClosedScreeningClient` performs no network I/O but also emits no log. In production, every attestation attempt will generate an `ERROR` screening outcome with no trace beyond the DB row. Operations will have no immediate signal that screening is operating in stub mode.
- **Severity:** Low
- **Evidence:** TIB lines 27-31; `spec/crypto-service/agents.md` (observability: structured JSON logs with `trace_id`).
- **Recommended brief amendment:** Add a `warn` log in `FailClosedScreeningClient.screen(...)` stating that the fail-closed stub is active and the address was not screened against a real vendor. Add a test that asserts the log is emitted (or at minimum document the absence as intentional).

---

(End of design challenge review. Findings are for human fold-in during Phase 4.)
