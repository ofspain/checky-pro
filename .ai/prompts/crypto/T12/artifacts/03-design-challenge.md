# crypto · T12 · Phase 3 — Design Challenge Findings

Consumed: `artifacts/02-task-implementation-brief.md`
References: `spec/crypto-service/agents.md`, `spec/crypto-service/package.md` §8, `spec/crypto-service/requirements.md` (R15/R16), `spec/crypto-service/design.md` (L8), `spec/crypto-service/tasks.md` (T12), existing `services/crypto` code (`TokenValidator.java`, `TronAdapter.java`).

---

### 1. Strict rejection of all-lowercase/all-uppercase EVM addresses may be stricter than EIP-55 and user-hostile

- **Severity:** Medium
- **Evidence:** EIP-55 (ERC-55) defines a checksum encoding for mixed-case addresses but explicitly states that all-lowercase addresses are valid Ethereum addresses that simply omit the checksum. R15 says "enforce EIP-55 checksum validity," which is commonly read as "if mixed case, the checksum must be correct" rather than "every address must be mixed-case-checksummed." The TIB resolves this by rejecting all lowercase/uppercase addresses, which is a product-affecting decision not stated in the spec.
- **Recommended brief amendment:** Make the strict interpretation an explicit LOCKED decision (e.g., "L8 is interpreted as mandatory EIP-55 checksum: any EVM address that is not mixed-case-checksummed is rejected"), and document the operational consequence that legitimate user-provided lowercase addresses must be re-checksummed by the client or UI before submission.

---

### 2. Integration ordering with T11's exact-casing allowlist lookup is not documented

- **Severity:** Medium
- **Evidence:** T11 Amendment #4 stores EVM placeholder addresses in `application.properties` as lowercase and `TokenValidator` performs exact-string matching with no case-folding. T12's `AddressValidator` requires a correctly checksummed (mixed-case) EVM address to return `true`. A future caller that validates an address and then looks it up in the allowlist must therefore lowercase the address between the two calls, or every valid checksummed EVM address will fail the allowlist lookup.
- **Recommended brief amendment:** Add an Open Question or explicit note stating that when `AddressValidator` is eventually wired in front of `TokenValidator`, EVM addresses must be normalized to lowercase after EIP-55 validation and before allowlist lookup, and that `AddressValidator` itself does not perform this normalization.

---

### 3. No required test for a mixed-case EVM address with an intentionally wrong checksum

- **Severity:** Medium
- **Evidence:** AC1 covers a correctly checksummed address returning `true`; AC3 covers all-lowercase/all-uppercase returning `false`. However, the most important negative case for EIP-55 — a structurally valid, mixed-case address whose checksum bits are wrong (e.g., a single case bit flipped in a valid checksummed address) — is not explicitly listed in the Required Tests.
- **Recommended brief amendment:** Add a required test asserting that a mixed-case EVM address with a corrupted checksum returns `false`.

---

### 4. `0X` prefix handling is ambiguous

- **Severity:** Low
- **Evidence:** The TIB states the EVM regex is "`0x` + exactly 40 hex characters (case-insensitive)." It does not clarify whether the `X` in `0X` is accepted (case-insensitive prefix) or rejected (literal lowercase `0x` only). `Keys.toChecksumAddress` likely expects lowercase `0x`; accepting `0X` could lead to inconsistent behavior.
- **Recommended brief amendment:** Specify explicitly that the prefix must be literal lowercase `0x` and that `0XABC...` is rejected, and add a corresponding test case.

---

### 5. No input length bound for Tron Base58Check decoding

- **Severity:** Low
- **Evidence:** `isValidTronAddress` passes the input string directly to `Base58Check.base58ToBytes(address)` with no length pre-check. EVM addresses are implicitly bounded to 42 characters by the regex, but a pathologically long Tron string could cause excessive CPU/memory consumption during Base58 decoding before any structural check.
- **Recommended brief amendment:** Add a maximum length guard (e.g., reject strings longer than 64 characters) before invoking `Base58Check`, with a test asserting that an oversized string returns `false` cleanly.

---

### 6. Module-boundary enforcement for the new class is not mentioned

- **Severity:** Low
- **Evidence:** `AddressValidator` will live in `com.themistra.crypto.token`, so the existing `TokenModuleBoundaryTest` (T11 Amendment #11) will automatically scan it for forbidden imports from `adapter/`, `observation/`, `provider/`, or `quorum/`. The TIB does not mention verifying this, nor does it list any boundary-related acceptance criterion for T12.
- **Recommended brief amendment:** Add a note that `TokenModuleBoundaryTest` must be re-run after adding `AddressValidator` and must continue to pass, or add an AC stating that `AddressValidator` imports nothing from the forbidden sibling packages.

---

### 7. No handling of leading/trailing whitespace is specified

- **Severity:** Low
- **Evidence:** The TIB only mentions `null` returning `false`. It does not state whether `" 0xabc... "` (with surrounding whitespace) should be rejected or trimmed. A strict regex anchored to `^0x...$` will reject it, which is probably correct, but the behavior is unstated.
- **Recommended brief amendment:** Document that inputs are not trimmed and that any leading/trailing whitespace causes rejection, and add a test case with a padded address.

---

### 8. Reusing a local `0x41` constant duplicates TronAdapter's private prefix

- **Severity:** Low
- **Evidence:** The TIB states `AddressValidator` will define its own local `0x41` constant rather than reach into `adapter/tron/TronAdapter.java` (which has `private static final byte ADDRESS_PREFIX = 0x41`). This is correct for L15 module boundaries but creates a second, unlinked copy of a chain-constant that could drift if the launch scope ever added a different Tron network.
- **Recommended brief amendment:** Keep the local constant but add a code comment referencing `TronAdapter`'s identical value so future maintainers know the two must stay in sync, or move the constant to `common/` if a third task needs it.

---

### 9. The boolean-predicate return type defers "rejected at the boundary" to a future caller

- **Severity:** Low
- **Evidence:** L8 says "Invalid addresses are rejected at the boundary." The TIB correctly scopes this task to supplying the predicate, but the acceptance criteria do not verify that a future caller actually rejects invalid addresses. This is consistent with the "no real caller yet" pattern, but it means L8 is not fully satisfied by T12 alone.
- **Recommended brief amendment:** Add an Open Question or dependency note in the brief reminding the author that a subsequent task (watch registration API, task 15, or elsewhere) must wire `AddressValidator` and enforce rejection, otherwise L8 remains unenforced.
