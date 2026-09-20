# crypto · T12 · Phase 2 — Task Implementation Brief (TIB)

## Task

Address validation. Implement `AddressValidator` (EIP-55 for EVM, Base58Check for Tron) at the boundary
(L8, R15/R16).

## Purpose

The structural gate that stops a malformed or maliciously-crafted address string from ever being
trusted as a real on-chain address — L8's "mandatory... rejected at the boundary" framing exists
because this service's entire value proposition is not attesting to something false, and an address is
the very first, cheapest thing to get wrong.

## Scope

**In:**
- **`AddressValidator`** — a stateless `@Component` (mirrors `TokenValidator`'s own precedent: no
  persistence, no injected dependency, but still a proper Spring bean for eventual DI at a future call
  site, not a static-method utility class), two methods:
  - **`boolean isValidEvmAddress(String address)`** — `false` for `null`; structurally must match `0x`
    + exactly 40 hex characters (case-insensitive) — **resolves Phase 1 Open Question #1**: given
    web3j's own `Keys.toChecksumAddress` does NOT validate this shape itself (Phase 0 bytecode finding),
    `AddressValidator` checks it independently via a fixed regex before ever calling into web3j. Then
    `address.equals(Keys.toChecksumAddress(address))` — **an all-lowercase or all-uppercase address is
    REJECTED, not accepted as a valid unchecksummed fallback.** L8's own wording ("mandatory," "invalid
    addresses are rejected") is read strictly here: if an unchecksummed address were accepted, L8's
    checksum requirement would be trivially bypassable by simply lowercasing any address, making the
    whole rule toothless against exactly the failure mode (a subtly-wrong or spoofed address slipping
    through unnoticed) it exists to catch.
  - **`boolean isValidTronAddress(String address)`** — `false` for `null`; delegates to
    `Base58Check.base58ToBytes(address)` inside a `try`/`catch(IllegalArgumentException)` (Phase 0
    bytecode finding: this is the single, uniform exception type for every Base58Check validity failure
    — illegal character or checksum mismatch alike). **Resolves Phase 1 Open Question #2: on successful
    decode, additionally checks the result is exactly 21 bytes with a leading `0x41` byte** (Tron
    mainnet's own address shape, already established as a named constant in `TronAdapter`, T07). Bare
    Base58Check-checksum correctness alone is not sufficient to call a string "a valid Tron address" —
    Bitcoin addresses (and other Base58Check-encoded formats with a different version/prefix byte) can
    pass a checksum-only check while being structurally the wrong kind of address entirely; the
    21-byte/`0x41`-prefix check is what actually confirms "this decodes to a Tron mainnet address," not
    merely "this is some valid Base58Check string."
- No new dependency (`org.web3j:core`/`crypto`, T06; `io.github.tronprotocol:trident`, T07 — both
  already present).

**Out:**
- `AddressPoisoningDetector` (L9, R17) — task 13.
- Wiring `AddressValidator` into any actual caller/boundary (watch registration API, task 15, or
  elsewhere) — no such caller exists in this task's own scope, consistent with every prior task's own
  "no real caller yet" pattern.
- Throwing a domain-specific exception on invalid input — both methods return a plain `boolean`
  predicate; a future caller decides how to react (HTTP 4xx, a different domain exception, a log line),
  mirroring `TokenValidator.validate`'s own "return a value, let the caller decide" precedent rather
  than baking a specific failure-handling policy into this pure validation primitive.
- A single, chain-dispatching `validate(String chain, String address)` method — two explicitly-named
  methods are simpler and avoid needing to reintroduce `TokenValidator`'s own "unrecognized chain"
  fail-fast guardrail for a third parameter this task doesn't need at all (the caller already knows
  which chain's address it has).
- Tron-testnet or other non-mainnet address prefix bytes — only the mainnet `0x41` prefix is checked,
  matching `TronAdapter`'s own existing, launch-scoped convention (T07).

## Business Rules

- **R15.** EVM address validation enforces EIP-55 checksum validity — strictly, per the interpretation
  above.
- **R16.** Tron address validation enforces Base58Check validity, plus the mainnet address shape.

## Locked Decisions

- **L8.** Address validation is mandatory; invalid addresses are rejected at the boundary (the
  boundary itself is a future task's concern — this task supplies the predicate).

## Dependencies

- `org.web3j.crypto.Keys` (T06, existing).
- `org.tron.trident.utils.Base58Check` (T07, existing).
- No new external library dependency, no `pom.xml` change.

## Inputs

- A single `String` address (EVM or Tron, depending on which method is called) — from whatever future
  caller first needs address validation. No such caller exists in this task's own scope.

## Outputs

- `boolean` — `true` if the address is structurally valid and correctly checksummed for its chain,
  `false` otherwise (including for `null`).

## State Changes

None — a pure function, no persistence, no side effects.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/token/AddressValidator.java`

## Files to Modify

None expected.

## Files NOT to Modify

- `token/TokenAllowlist.java`/`TokenAllowlistRepository.java`/`TokenValidator.java` (T11) — consumed
  conceptually only (same package), not modified.
- `adapter/tron/TronAdapter.java` (T07) — referenced as a pattern precedent only (the `0x41` prefix
  constant), not modified; `AddressValidator` defines its own local constant rather than reaching into
  `adapter/` (module boundary, L15) to reuse `TronAdapter.ADDRESS_PREFIX`, which is itself `private`.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R15, L8).** A correctly EIP-55-checksummed, well-formed EVM address returns `true`.
- **AC2 (R15, L8).** A structurally malformed EVM address (wrong length, non-hex characters, missing
  `0x` prefix) returns `false`.
- **AC3 (R15, L8).** An all-lowercase or all-uppercase (structurally valid but unchecksummed) EVM
  address returns `false` — strict rejection, per the resolved interpretation above.
- **AC4 (R16, L8).** A correctly Base58Check-encoded, 21-byte, `0x41`-prefixed Tron address returns
  `true`.
- **AC5 (R16, L8).** A Base58Check string with an illegal character, or a correct-alphabet-but-wrong-
  checksum string, returns `false`.
- **AC6 (R16, L8).** A Base58Check-valid string that does not decode to 21 bytes with a leading `0x41`
  (e.g., a differently-shaped or differently-prefixed Base58Check payload) returns `false`.
- **AC7 (null-safety).** Both methods return `false` for a `null` input, never throwing.

## Required Tests

- `shouldValidateEip55ChecksumForEvmAddresses` (package.md §8, named) — AC1, AC3.
- `shouldValidateBase58ChecksumForTronAddresses` (package.md §8, named) — AC4, AC5.
- A test asserting a structurally malformed EVM address is rejected (AC2).
- A test asserting both an all-lowercase and an all-uppercase EVM address are rejected (AC3).
- A test asserting a Base58Check string with an illegal character is rejected (AC5).
- A test asserting a Base58Check string with a correct alphabet but wrong checksum is rejected (AC5).
- A test asserting a Base58Check-valid but wrong-length or wrong-prefix-byte string is rejected (AC6).
- A test asserting `null` is rejected cleanly for both methods (AC7).

## Constraints

- **Performance:** none beyond existing conventions — no hot-path/per-block concern.
- **Security:** this task's entire purpose is a security boundary; no secret is introduced or handled.
- **Thread-safety:** `AddressValidator` holds no mutable state; trivially thread-safe.
- **Module boundaries:** no import from `adapter/`, `observation/`, `provider/`, or `quorum/` — only
  `org.web3j.crypto.Keys` and `org.tron.trident.utils.Base58Check` (external libraries, not sibling
  feature packages).
- **Null handling:** both methods return `false` for `null`, per AC7 — no `Objects.requireNonNull`
  fail-fast here, since a "reject cleanly" boolean predicate is a better fit for a validation function
  than throwing on the exact input it exists to validate (unlike every prior task's collaborator
  methods, which reject `null` as a programming error).

## Open Questions

No blockers. Both Phase 1 open items (strict EIP-55 rejection of unchecksummed addresses; the Tron
21-byte/`0x41`-prefix structural check) are resolved above as implementer-proposed decisions, ready for
Phase 3 (Kimi) challenge.
