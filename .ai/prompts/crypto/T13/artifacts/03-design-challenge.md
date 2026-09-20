# crypto · T13 · Phase 3 — Design Challenge Findings

Consumed: `artifacts/02-task-implementation-brief.md`
References: `spec/crypto-service/agents.md`, `spec/crypto-service/package.md` §8, `spec/crypto-service/requirements.md` (R17), `spec/crypto-service/design.md` (L9), `spec/crypto-service/tasks.md` (T13), existing `services/crypto/src/main/java/com/themistra/crypto/token/AddressValidator.java`.

---

### 1. EVM's common `0x` prefix makes the 4-character threshold extremely noisy for Ethereum

- **Severity:** High
- **Evidence:** The TIB defines a match as "at least 4 identical leading characters" with no address-format normalization. Every EVM address begins with `0x`, so two random EVM addresses already share 2 characters. A 4-character prefix match therefore requires only 2 additional matching hex digits — a 1 in 256 chance per pair, not the 1 in ~65,536 a 4-hex-digit match would imply. The example `0x1234...abcd` suggests the threshold is meant to mimic wallet truncation, but the algorithm as specified is literal and would include the `0x`.
- **Recommended brief amendment:** Either (a) explicitly strip the chain-specific prefix (`0x` for EVM, `T` for Tron) before comparing, or (b) make the threshold chain-aware (e.g., require 6 leading hex digits for EVM while keeping 4 for Tron), or (c) document the expected false-positive rate and accept it as a deliberate "favor false positives" choice. In all cases, add a test with two EVM addresses that share only `0x` plus 2 hex digits.

---

### 2. Case-sensitive literal comparison will misfire on EVM addresses represented in different casings

- **Severity:** Medium
- **Evidence:** AC5 mandates case-sensitive, unnormalized comparison. A legitimate, previously-seen counterparty address stored as lowercase and reappearing as a checksummed mixed-case string will be flagged as poisoning (false positive). Conversely, an attacker could craft a look-alike that matches a previous address when both are lowercased but not in their original casings, slipping through if the caller did not normalize.
- **Recommended brief amendment:** Document that callers must normalize both the candidate and the history collection to a single canonical casing before invoking `detectPoisoning`, and that the detector intentionally does not normalize. Add a test asserting the lowercase-vs-checksummed same-address case is flagged (to lock in the current behavior) with a comment explaining it is caller error, not detector error.

---

### 3. No behavior specified for candidate or history addresses shorter than 4 characters

- **Severity:** Medium
- **Evidence:** The Required Tests do not include addresses shorter than the 4-character threshold. A naive prefix/suffix substring implementation would throw `StringIndexOutOfBoundsException` for a candidate or previously-seen address of length 0–3. The brief only says `null`/empty collection and `null` candidate return empty safely.
- **Recommended brief amendment:** State explicitly that any address shorter than 4 characters can never trigger a match and is handled safely (no exception). Add tests for a 3-character candidate against a normal history, and for a normal candidate against a history containing a 3-character address.

---

### 4. L9's "propagate onto observations/events" is deferred without an explicit dependency link

- **Severity:** Medium
- **Evidence:** L9 says "flag it on the observation so it propagates downstream." The TIB correctly defers this because `Watch` and the observation flag column do not yet exist, but the brief does not create a concrete dependency or follow-up item ensuring a future task actually wires the detector into the observation/event pipeline.
- **Recommended brief amendment:** Add an explicit Open Question or dependency note naming the future task (most plausibly the watcher layer, task 16) that must call `detectPoisoning` and attach the returned flag to `chain.observations` and emitted events, and note that L9 is not fully satisfied until that wiring exists.

---

### 5. Exact 3-character and 4-character boundary cases are not tested

- **Severity:** Low
- **Evidence:** AC1 says "at least 4 identical leading characters" triggers a flag. The Required Tests include prefix-only and suffix-only matches but do not pin the boundary at exactly 3 vs. 4 characters.
- **Recommended brief amendment:** Add two tests: one where the candidate and a previous address share exactly 3 leading (or trailing) characters and no flag is returned, and one where they share exactly 4 and a flag is returned.

---

### 6. Null elements inside the previously-seen collection are not addressed

- **Severity:** Low
- **Evidence:** The brief says a `null` collection returns empty, but it does not specify what happens if the collection itself is non-null but contains one or more `null` elements. A naive iteration that calls `.startsWith()`/`.endsWith()` on a null element would throw `NullPointerException`.
- **Recommended brief amendment:** Specify that `null` elements inside the collection are ignored (skipped), and add a test with `List.of(realAddress, null)` to lock in the safe behavior.

---

### 7. Returning an arbitrary match from multiple candidates may obscure the closest look-alike

- **Severity:** Low
- **Evidence:** AC6 says "any one of them may be returned" if multiple previously-seen addresses resemble the candidate. This is sufficient for a boolean flag, but if the returned address is ever shown to an operator or user, an arbitrary choice could be the weaker match (e.g., 4-character prefix match chosen over an 8-character suffix match).
- **Recommended brief amendment:** Document that the returned address is intentionally arbitrary and that consumers must treat the result as a boolean "poisoning suspected" signal, not as a ranked or deterministic "best match."

---

### 8. No integration guidance with `AddressValidator`

- **Severity:** Low
- **Evidence:** The detector compares raw strings with no address-format validation. A malformed candidate that happens to share 4 characters with a real previous address will be flagged, even though it is not a valid address. The TIB scopes this as a pure comparison primitive, but it does not tell future callers whether they should validate the candidate first.
- **Recommended brief amendment:** Add a forward-looking note that callers are expected to run the candidate through `AddressValidator` before `detectPoisoning` if they want to avoid flagging structurally invalid strings, and that `AddressPoisoningDetector` itself performs no validation.

---

### 9. The 4-character threshold rationale is not tied to a named constant or future configurability

- **Severity:** Low
- **Evidence:** The TIB explains why 4 was chosen but treats it as a magic number in the algorithm description. There is no named constant (e.g., `MATCH_LENGTH_THRESHOLD`) and no mention of whether future tasks might make this configurable per chain or per deployment.
- **Recommended brief amendment:** Define the threshold as a named constant in the class and add a comment referencing the wallet-truncation rationale; optionally note that a future task may externalize it if operational experience demands a different value.
