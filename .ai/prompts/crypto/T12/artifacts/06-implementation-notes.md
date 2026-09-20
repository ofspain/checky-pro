# crypto · T12 · Phase 6 — Implementation Notes

## What changed

One new file — exactly as planned (Phase 5), no file the frozen brief did not authorize was touched.

- **`AddressValidator.java`** — `@Component`, two methods: `isValidEvmAddress` (fully-anchored, literal-
  lowercase-`0x`-prefix regex, then `address.equals(Keys.toChecksumAddress(address))` for strict EIP-55)
  and `isValidTronAddress` (a 64-character length guard, then `Base58Check.base58ToBytes` inside a
  `try`/`catch(IllegalArgumentException)`, then a 21-byte/`0x41`-prefix decoded-shape check).

## Mapping to plan and acceptance criteria

| AC | Satisfied by |
|---|---|
| AC1 (correctly checksummed EVM → true) | `isValidEvmAddress`'s equality check against `Keys.toChecksumAddress` |
| AC2 (structurally malformed EVM → false) | `EVM_ADDRESS_PATTERN`'s fully-anchored regex |
| AC3 (all-lowercase/all-uppercase EVM → false) | Same equality check — an unchecksummed address never equals its own checksummed form |
| AC4 (corrupted-checksum mixed-case EVM → false) | Same equality check — a single wrong case bit changes the string, breaking equality |
| AC5 (valid Tron → true) | `isValidTronAddress`'s successful decode + shape check |
| AC6 (illegal character / wrong checksum → false) | The `catch (IllegalArgumentException)` — the single, uniform exception type for both failure modes (Phase 0 finding) |
| AC7 (valid Base58Check, wrong shape → false) | The explicit `decoded.length == 21 && decoded[0] == 0x41` check after a successful decode |
| AC8 (oversized Tron input → false, no `Base58Check` call) | The length guard, checked before the `try` block |
| AC9 (null-safety) | Both methods' leading `null` checks |

## Deviations forced by reality

None. `mvn -pl services/crypto compile` succeeded on the first attempt, with zero warnings. The exact
behavior of both `Keys.toChecksumAddress` and `Base58Check.base58ToBytes` had already been confirmed via
direct bytecode inspection (Phase 0) and direct execution against real test vectors (Phase 5) before
this phase, so no surprise surfaced during coding itself.
