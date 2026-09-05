# crypto · T12 · Phase 5 — Implementation Plan

## Files to create

One file, tracing directly to the frozen brief's "Files to Create" list:

1. `services/crypto/src/main/java/com/themistra/crypto/token/AddressValidator.java`

Test file:
2. `services/crypto/src/test/java/com/themistra/crypto/token/AddressValidatorTest.java`

## Files to modify

None.

## Public methods (signatures)

```java
@Component
public class AddressValidator {

    public boolean isValidEvmAddress(String address);

    public boolean isValidTronAddress(String address);
}
```

## Private methods

- `isValidEvmAddress`: no private helper needed — a `Pattern` constant
  (`EVM_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-f]{40}$", Pattern.CASE_INSENSITIVE)` — actually
  **must anchor the prefix to literal lowercase per Amendment #4**, so the pattern is
  `^0x[0-9a-fA-F]{40}$` with NO `CASE_INSENSITIVE` flag, since that flag would also case-fold the
  literal `0x` prefix itself, defeating Amendment #4) plus a direct call to
  `Keys.toChecksumAddress`.
- `isValidTronAddress`: a length guard (`address.length() > MAX_TRON_ADDRESS_LENGTH`) before the
  `try`/`catch(IllegalArgumentException)` around `Base58Check.base58ToBytes`, then a decoded-length
  and first-byte check.

## Entities used

None — no persistence.

## Repositories used

None.

## Services used

- `org.web3j.crypto.Keys` (T06, existing) — static method call, not a Spring bean.
- `org.tron.trident.utils.Base58Check` (T07, existing) — static method call, not a Spring bean.

## Constants

```java
private static final Pattern EVM_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

// Tron mainnet address prefix byte - mirrors TronAdapter.ADDRESS_PREFIX (T07, private, adapter/tron/)
// exactly; kept as a separate local constant per L15 module boundaries, not imported across packages.
private static final byte TRON_MAINNET_PREFIX = 0x41;
private static final int TRON_DECODED_LENGTH = 21;

// Amendment #5 (Kimi Issue 5): defensive bound before Base58Check decoding - real Tron addresses are
// ~34 characters; 64 is a generous margin, not a tight spec-derived value.
private static final int MAX_TRON_ADDRESS_LENGTH = 64;
```

## Verified test vectors

Computed and confirmed by direct execution against the real, already-dependency-pinned libraries
(`org.web3j:crypto:6.0.0`, `io.github.tronprotocol:trident:1.0.0`) in this phase — not hand-typed from
memory, mirroring this session's own established library-verification discipline. Reused verbatim in
Phase 10's test file.

**EVM** (derived from the arbitrary lowercase seed `0x5aeda56215b167893e80b4fe645ba6d5bab767de`):

| Variant | Value | Expected `isValidEvmAddress` |
|---|---|---|
| Correctly checksummed | `0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE` | `true` |
| All-lowercase | `0x5aeda56215b167893e80b4fe645ba6d5bab767de` | `false` (Amendment #1) |
| All-uppercase | `0x5AEDA56215B167893E80B4FE645BA6D5BAB767DE` | `false` (Amendment #1) |
| Corrupted checksum (one case bit flipped: `5A` → `5a` at position 2) | `0x5aEDA56215b167893e80B4fE645BA6d5Bab767DE` | `false` (AC4) |
| Uppercase `0X` prefix | `0X5AEDA56215b167893e80B4fE645BA6d5Bab767DE` | `false` (Amendment #4) |
| Whitespace-padded | `" 0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE"` (leading space) | `false` (Amendment #7) |
| Wrong length (39 hex chars) | `0x5AEDA56215b167893e80B4fE645BA6d5Bab767D` | `false` (AC2) |
| Non-hex character | `0x5AEDA56215b167893e80B4fE645BA6d5Bab767DG` | `false` (AC2) |
| `null` | `null` | `false` (AC9) |

**Tron** (a synthetic 21-byte payload: `0x41` followed by bytes `0x01..0x14`, Base58Check-encoded):

| Variant | Value | Expected `isValidTronAddress` |
|---|---|---|
| Valid | `TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnj` | `true` |
| Illegal character (`0` at position 0, outside the Base58 alphabet) | `0A4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnj` | `false` (AC6) |
| Wrong checksum (last character changed) | `TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnA` | `false` (AC6) |
| Valid Base58Check, wrong prefix byte (`0x00` instead of `0x41`, still 21 bytes) | `16L5yRNPTuciSgXGHqYwn9N6NeoKqopAu` | `false` (AC7) |
| Oversized (65 `'A'` characters) | `"A".repeat(65)` | `false` (AC8, no `Base58Check` call) |
| `null` | `null` | `false` (AC9) |

## Unit tests required

**`AddressValidatorTest`** (plain JUnit, no mocks — pure logic, mirrors `QuorumEvaluatorTest`'s own
"no collaborators to mock" precedent):

- `shouldValidateEip55ChecksumForEvmAddresses` (named) — the correctly-checksummed vector returns
  `true`.
- `rejectsAnAllLowercaseEvmAddress` / `rejectsAnAllUppercaseEvmAddress` (AC3, Amendment #1).
- `rejectsAMixedCaseEvmAddressWithACorruptedChecksum` (AC4).
- `rejectsAnUppercaseZeroXPrefix` (AC2, Amendment #4).
- `rejectsAWhitespacePaddedEvmAddress` (AC2, Amendment #7).
- `rejectsAnEvmAddressOfTheWrongLength` / `rejectsAnEvmAddressWithANonHexCharacter` (AC2).
- `rejectsANullEvmAddress` (AC9).
- `shouldValidateBase58ChecksumForTronAddresses` (named) — the valid vector returns `true`.
- `rejectsATronAddressWithAnIllegalCharacter` / `rejectsATronAddressWithAWrongChecksum` (AC6).
- `rejectsABase58CheckValidStringWithTheWrongPrefixByte` (AC7).
- `rejectsAnOversizedTronAddress` (AC8).
- `rejectsANullTronAddress` (AC9).

No integration test — no persistence, no Docker dependency, consistent with Phase 0's own finding that
this task introduces no such layer.

## Execution order

1. `AddressValidator.java` (+ `AddressValidatorTest.java`) — a single, self-contained file with no
   dependency on anything else new in this task.
2. `mvn -pl services/crypto test-compile` then targeted `mvn -pl services/crypto test -Dtest=AddressValidatorTest`,
   then a full `mvn -pl services/crypto -am test` regression pass.
