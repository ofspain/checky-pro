# crypto · T12 · Phase 11 — Test Review Findings

Reviewed: `AddressValidatorTest.java` and the Phase 9 `AddressValidator.java` it exercises.

---

### 1. AC7 wrong-length Tron case is not fully covered

- **Gap:** `rejectsABase58CheckValidStringWithTheWrongPrefixByte` covers a Base58Check-valid 21-byte payload with the wrong prefix byte (`0x00`), but there is no test for a Base58Check-valid string whose decoded payload length is something other than 21 bytes.
- **Why it matters:** AC7 requires rejecting "valid Base58Check [but] wrong-length or wrong-prefix-byte" strings. The two failure modes (length != 21 vs. prefix != `0x41`) are separate branches in `isValidTronAddress`; only the prefix branch is currently exercised.
- **Suggested test:** Add `rejectsABase58CheckValidStringWithTheWrongDecodedLength` that constructs (or hardcodes) a Base58Check string decoding to, e.g., 20 or 22 bytes, and assert `isValidTronAddress` returns `false`.

---

### 2. EVM address that is too long is not tested

- **Gap:** `rejectsAnEvmAddressOfTheWrongLength` only shortens the valid vector by one character (too short). There is no test for an address with `0x` + 41 hex characters (too long).
- **Why it matters:** The regex `^0x[0-9a-fA-F]{40}$` rejects both, but a regression that accidentally relaxed the upper bound (e.g., `{40,}`) would not be caught.
- **Suggested test:** Add `rejectsAnEvmAddressThatIsTooLong` with `CHECKSUMMED + "0"` (or any extra hex char) and assert `false`.

---

### 3. EVM address missing the `0x` prefix is not tested

- **Gap:** None of the EVM tests use a 40-hex-character string without the `0x` prefix.
- **Why it matters:** AC2 explicitly names "missing ... `0x` prefix" as a structurally malformed case. The anchored regex should reject it, but the absence of a concrete test leaves a small regression gap.
- **Suggested test:** Add `rejectsAnEvmAddressMissingTheZeroXPrefix` with `CHECKSUMMED.substring(2)` and assert `false`.

---

### 4. Empty-string EVM input is not tested

- **Gap:** `rejectsAnEmptyTronAddress` locks in the Tron short-input fix, but there is no corresponding `rejectsAnEmptyEvmAddress` test.
- **Why it matters:** Empty strings are a common boundary input; symmetry with the Tron tests makes the EVM coverage complete and documents that the regex rejects them cleanly.
- **Suggested test:** Add `rejectsAnEmptyEvmAddress` asserting `validator.isValidEvmAddress("")` is `false`.

---

### 5. Exact-boundary Tron length inputs are not tested

- **Gap:** `rejectsATronAddressJustBelowTheMinimumLength` uses 24 characters, and `rejectsAnOversizedTronAddress` uses 65 characters. The exact boundaries at `MIN_TRON_ADDRESS_LENGTH` (25) and `MAX_TRON_ADDRESS_LENGTH` (64) are not exercised.
- **Why it matters:** A fencepost error in the length check (e.g., `<=` vs. `<`) would only be caught by testing the exact boundary values.
- **Suggested test:** Add `rejectsATronAddressAtTheMinimumLength` (25 `'A'` characters) and `rejectsATronAddressAtTheMaximumLength` (64 `'A'` characters), both asserting `false`.

---

### 6. No test uses visually confusable non-hex characters

- **Gap:** `rejectsAnEvmAddressWithANonHexCharacter` uses `G`, which is clearly non-hex. It does not test `O` (capital letter O), `I` (capital i), or `l` (lowercase L), which are common human transcription errors and are also outside the hex alphabet.
- **Why it matters:** These characters are more realistic failure modes than `G`; a test with them better represents real-world typo/spoofing attempts.
- **Suggested test:** Add `rejectsAnEvmAddressWithAVisuallyConfusableNonHexCharacter` using an address ending in `O`, `I`, or `l`.

---

### 7. The `RuntimeException` catch path is only implicitly tested

- **Gap:** The Phase 9 broadened catch (`catch (RuntimeException e)`) is reached by the empty/short Tron inputs only because the `MIN_TRON_ADDRESS_LENGTH` guard prevents them from ever reaching `Base58Check`. There is no test that forces `Base58Check` itself to throw a non-`IllegalArgumentException` and proves it is swallowed.
- **Why it matters:** If the catch were narrowed back to `IllegalArgumentException` and the min-length guard were accidentally removed or reduced, the existing tests would still pass because the guard currently hides the library bug.
- **Suggested test:** Use a mocking library or reflection to make `Base58Check.base58ToBytes` throw, e.g., `ArrayIndexOutOfBoundsException`, and assert `isValidTronAddress` returns `false`. If that is impractical, at least document that the broad catch is defense-in-depth and that the min-length guard is the primary fix.
