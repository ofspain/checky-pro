# crypto · T12 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T12
for merge. Branches off `main`; `main` remains deployable throughout — no commit in this task touches
anything outside `services/crypto/` (plus this task's own `.ai/prompts/crypto/T12/` artifacts).

## Commit title

```
crypto: add EIP-55 and Base58Check address validation (T12)
```

## Commit message

```
crypto: add EIP-55 and Base58Check address validation (T12)

Implement AddressValidator: isValidEvmAddress enforces strict EIP-55
checksum validity (R15), isValidTronAddress enforces Base58Check
validity plus the Tron mainnet 21-byte/0x41-prefix decoded shape (R16).
Both are pure boolean predicates - no persistence, no caller wired in
yet (L8's "rejected at the boundary" is a future task's job, most
plausibly the watch registration API).

The EIP-55 interpretation is deliberately strict: an all-lowercase or
all-uppercase EVM address is rejected, not accepted as an unchecksummed
fallback the way raw EIP-55 itself treats it. This reads L8's own
"mandatory"/"rejected" wording as this platform's own stricter policy -
accepting unchecksummed input would make the checksum mandate trivially
bypassable. One real exception: the all-zero address has no alphabetic
hex characters at all, so the checksum has nothing to act on and it is
correctly accepted - confirmed by direct execution against the real
web3j library, not assumed, since an independent review's own premise
about this case turned out to be factually wrong.

This task's own review cycle found and fixed a genuine bug via direct
execution against the pinned trident library: Base58Check.base58ToBytes
throws NegativeArraySizeException, not IllegalArgumentException, for any
input under 5 characters (including the empty string) - a gap the
original IllegalArgumentException-only catch missed entirely, and one no
planned test vector would have caught since all of them were 33+
characters. Fixed with a MIN_TRON_ADDRESS_LENGTH guard plus a broadened
RuntimeException catch as defense-in-depth.

Both web3j's toChecksumAddress and trident's Base58Check were verified
via direct bytecode inspection and execution before being relied on,
rather than trusted from memory - toChecksumAddress does not itself
validate structure (only an independent regex does), and
Base58Check.base58ToBytes's exception contract has at least the one
edge case above beyond its documented IllegalArgumentException surface.

This is the first task in this service's own build-out with no
persistence layer at all - its full test suite runs with zero Docker
dependency.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/token/AddressValidator.java` — new

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/token/AddressValidatorTest.java` — new (27 tests)

**Pipeline artifacts:**
- `.ai/prompts/crypto/T12/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T12 adds the address-format gate R15/R16/L8 require: strict EIP-55 checksum validity for EVM addresses
and Base58Check-plus-mainnet-shape validity for Tron addresses. It is the first task in this service's
build-out with no persistence layer and no Docker dependency at all, and its own review cycle both
caught a genuine third-party library exception-safety gap (confirmed by direct execution, not assumed)
and corrected a factually incorrect independent-review premise about the all-zero address's behavior —
both resolved before verification, not deferred.

## Testing performed

- `mvn -pl services/crypto test-compile` — BUILD SUCCESS, no new warnings.
- `mvn -pl services/crypto test -Dtest=AddressValidatorTest` — 27/27 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 372 tests, 364 passing, 8 errors, all
  `IllegalState: … Docker environment …` (the same pre-existing set carried unchanged from T11's own
  baseline — this task introduces no new persistence layer) — zero genuine failures.

## Specification references

- **Task:** T12 — Address validation (`spec/crypto-service/tasks.md` #12).
- **Requirements:** R15, R16 (`spec/crypto-service/requirements.md:27-28`).
- **Locked decisions:** L8 (`spec/crypto-service/design.md:12`) — address validation is mandatory;
  invalid addresses are rejected at the boundary.
- **Named tests:** `shouldValidateEip55ChecksumForEvmAddresses`,
  `shouldValidateBase58ChecksumForTronAddresses` (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` are touched by this task —
  `AddressValidator` is a pure predicate with no HTTP endpoint and no event emission.
