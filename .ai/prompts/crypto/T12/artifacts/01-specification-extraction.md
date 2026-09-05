# crypto · T12 · Phase 1 — Specification Extraction

## Business Rules

- **R15.** When validating an EVM (Ethereum) address, the system enforces EIP-55 checksum validity.
- **R16.** When validating a Tron address, the system enforces Base58Check validity.

## Locked Decisions

- **L8.** Address validation is mandatory — EIP-55 checksum on all EVM addresses, Base58Check on Tron.
  Invalid addresses are rejected at the boundary.

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `org.web3j.crypto.Keys` (web3j `crypto` 6.0.0, already a transitive dependency via
  `org.web3j:core`, T06) — `toChecksumAddress(String)`, confirmed via bytecode inspection to
  re-case but not structurally validate its input (Phase 0 finding).
- `org.tron.trident.utils.Base58Check` (trident 1.0.0, already a dependency, T07) —
  `base58ToBytes(String)`, confirmed via bytecode inspection to throw `IllegalArgumentException`
  uniformly for any Base58Check validity failure (Phase 0 finding).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java` (T07) — pattern
  reference only, for the 21-byte/`0x41`-mainnet-prefix Tron address shape; not modified.

**New, per design.md §6 (`token/` package, already established by T11):**
- `token/AddressValidator.java` — the only file design.md names for this task.

**Explicitly NOT in this task's scope, despite being listed under `token/` in design.md §6:**
- `token/AddressPoisoningDetector.java` (L9, R17) — task 13.

## Dependencies

- `org.web3j:core`/`crypto` (T06, existing) and `io.github.tronprotocol:trident` (T07, existing) — no
  new external library dependency, no `pom.xml` change expected.
- No persistence, no `Clock`, no outbox — a pure, stateless validation utility.

## Acceptance Criteria

- **AC1 (R15, L8).** A syntactically well-formed (`0x` + 40 hex characters) EVM address that is
  correctly EIP-55-checksummed is accepted; one that is not correctly checksummed (including an
  all-lowercase or all-uppercase address — strict rejection, per L8's "mandatory"/"rejected" framing,
  a Phase 2 interpretation decision flagged in Phase 0) is rejected.
- **AC2 (structural, R15).** An address that is not even structurally well-formed (wrong length, missing
  `0x` prefix, non-hex characters) is rejected — `Keys.toChecksumAddress` alone does not guarantee this,
  per the Phase 0 bytecode finding, so `AddressValidator` must check shape independently.
- **AC3 (R16, L8).** A syntactically valid Base58Check-encoded Tron address (correct alphabet, correct
  checksum) is accepted; one with an illegal character or a checksum mismatch is rejected.
- **AC4 (structural, R16 — Phase 2 to decide exact scope).** Whether Tron validation also enforces the
  21-byte/`0x41`-mainnet-prefix decoded shape, beyond bare Base58Check correctness, is undecided per
  Phase 0's own flagged gap.

## Tests required

- `shouldValidateEip55ChecksumForEvmAddresses` (package.md §8, named) — AC1.
- `shouldValidateBase58ChecksumForTronAddresses` (package.md §8, named) — AC3.
- A test asserting a structurally malformed EVM address (wrong length, non-hex characters, missing `0x`)
  is rejected (AC2).
- A test asserting an all-lowercase and an all-uppercase EVM address are both rejected (or accepted, per
  whichever Phase 2 decides — the test must exist either way to lock in the chosen behavior) (AC1).
- A test asserting a Base58Check string with an illegal character, and one with a correct-alphabet-but-
  wrong-checksum, are both rejected (AC3).
- A test asserting a null/empty input is rejected cleanly, not via an unhandled exception.

## Open Questions

No blockers cited in `package.md` §11 apply to this task. Two items are genuine interpretation gaps the
spec's author never spelled out explicitly, requiring an implementer-proposed resolution (Phase 2,
subject to Kimi challenge + human sign-off), matching the precedent T08-T11 already set for similarly
under-specified areas:

- Whether an all-lowercase/all-uppercase EVM address is strictly rejected (this task's own leaning,
  given L8's "mandatory"/"rejected" wording) or accepted as a valid non-checksummed fallback.
- Whether Tron validation additionally enforces the 21-byte/`0x41`-prefix decoded shape, or is scoped
  strictly to bare Base58Check correctness as R16's own wording literally states.
