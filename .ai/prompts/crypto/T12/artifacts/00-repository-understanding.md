# crypto · T12 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module (`services/crypto`), package-by-feature under
`com.themistra.crypto`. This task introduces no persistence, no outbox/event emission, no HTTP
endpoint, and no new external dependency — `AddressValidator` is a pure, stateless validation utility.
Its two required libraries (`org.web3j:core:6.0.0` for EVM, `io.github.tronprotocol:trident:1.0.0` for
Tron) are already dependencies of this module (added in T06/T07 for the chain adapters) — no `pom.xml`
change is expected.

## 2. Existing code this task touches

**Already exists, consumed but not modified:**
- `org.web3j.crypto.Keys.toChecksumAddress(String)` (web3j `crypto` 6.0.0, transitively pulled in by
  `org.web3j:core`) — **confirmed via direct bytecode inspection (`javap -c`)** to implement the exact
  EIP-55 algorithm: strip the `0x` prefix, lowercase, compute the Keccak-256 hash of the lowercased
  string, then re-case each hex digit of the *original* address to uppercase wherever the corresponding
  hash nibble is ≥ 8. **Critically, this method does not itself validate anything** — it reads only the
  hash's own (always-valid) hex digits to decide casing, never validates the input address's own
  character set or length. A malformed address (wrong length, non-hex characters) would not
  necessarily throw from this method alone. This means `AddressValidator` cannot rely on
  `toChecksumAddress` for structural validation — it must independently check the `0x` + 40-hex-char
  shape first, then use `address.equals(Keys.toChecksumAddress(address))` to detect a genuinely
  correctly-checksummed address (an all-lowercase or all-uppercase address structurally passes the
  shape check but fails this equality check, and — per L8's own "EIP-55 checksum on all EVM addresses...
  invalid addresses are rejected" wording — this task's own reading is that such an address must be
  rejected, not silently accepted as a valid unchecksummed fallback; the strict reading is the more
  defensible one given L8's explicit "mandatory" framing, but this is this task's own interpretation,
  not something the spec spells out in so many words).
- `org.tron.trident.utils.Base58Check.base58ToBytes(String)` (trident 1.0.0) — **confirmed via direct
  bytecode inspection** to: decode via `Base58.decode` (which itself throws `IllegalArgumentException`
  with message `"Illegal character X at N"` for any character outside the Base58 alphabet), split the
  result into payload (all but the last 4 bytes) and the given 4-byte checksum, compute
  double-SHA-256 of the payload and compare its first 4 bytes to the given checksum, throwing
  `IllegalArgumentException("Checksum mismatch")` on any mismatch. **`IllegalArgumentException` is
  therefore the single, uniform exception type for every Base58Check validity failure** (illegal
  character or checksum mismatch alike) — directly usable via a `try`/`catch` in `AddressValidator`.
  `TronAdapter` (T07) already uses this same class's `bytesToBase58` (the inverse, encoding direction)
  and documents Tron mainnet addresses' own raw-byte shape: 21 bytes total, a `0x41` prefix byte
  (`TronAdapter.ADDRESS_PREFIX`) followed by a 20-byte body — a useful structural check beyond bare
  Base58Check validity (this task's own scope, per R16, is "Base58Check validity" specifically; whether
  to additionally check the 21-byte/`0x41`-prefix shape is a Phase 1/2 decision, not assumed here).
  `org.tron.trident.core.utils.Base58` (the plain, checksum-less variant `TronAdapter`'s own Javadoc
  already distinguishes from `Base58Check`) is not the right class for this task — R16 specifically asks
  for Base58Check (checksummed) validity, not plain Base58.

**New in this task (per design.md §6 `token/` package map):**
- `token/AddressValidator.java` — the only new file design.md names for this task.

**Explicitly NOT in this task's scope, despite being listed under `token/` in design.md §6 alongside
this task's own file:**
- `token/TokenAllowlist.java`/`TokenAllowlistRepository.java`/`TokenValidator.java` (T11, already
  shipped) — consumed conceptually only (both live in the same package), not modified.
- `token/AddressPoisoningDetector.java` (L9) — task 13, not this one.

## 3. Established patterns to follow

- **Library-verification-over-memory discipline**: this session has repeatedly used `javap`/direct
  bytecode inspection (T06/T07 for web3j/trident specifically) rather than trusting recalled library
  behavior — already applied above to confirm both `Keys.toChecksumAddress`'s actual non-validating
  behavior and `Base58Check.base58ToBytes`'s exact exception contract, since getting either wrong would
  produce a validator that silently accepts invalid addresses (the exact failure mode L8 exists to
  prevent).
- **Package boundaries**: `token/` already exists (T11) with an established, no-`adapter`/no-`quorum`
  import discipline (`TokenModuleBoundaryTest`, T11). `AddressValidator` will very likely need to import
  `org.web3j.crypto.Keys` and `org.tron.trident.utils.Base58Check`/`Base58` directly — these are
  external libraries, not sibling feature packages, so no `token/`→`adapter/` import is implied merely
  by both consuming the same third-party chain libraries independently.
- **No persistence, no `Clock`, no outbox** — this task's own scope (a pure function of a string) needs
  none of T04's established patterns.
- **"At the boundary" (L8's own wording)** — not elaborated anywhere in this spec beyond that phrase.
  The most plausible real call site is the watch-registration API (task 15,
  `POST /internal/v1/watches`), which will need to validate a caller-supplied address before trusting
  it — but task 15 is later, not yet built, and does not explicitly name `AddressValidator` in its own
  task statement. Consistent with every prior task's own "no real caller yet" pattern (T08's
  `Observation`, T09's `QuorumEvaluator`, T11's `TokenValidator` all had none until their respective
  consuming tasks existed), this task builds the validator; wiring it into an actual boundary is a
  future task's job.

## 4. Testing conventions

- Plain JUnit 5 — no mocks needed at all, since `AddressValidator` is a pure function with no
  collaborators (no repository, no `Clock`, no injected dependency of any kind).
- No Testcontainers/Docker dependency for this task — the first task in this session's own crypto-
  service work with no persistence layer at all, so no integration test is expected.
- Named test convention: `shouldValidateEip55ChecksumForEvmAddresses` and
  `shouldValidateBase58ChecksumForTronAddresses` (package.md §8) are written verbatim as test method
  names, per every prior task's own convention.
- Real, well-known test vectors exist for EIP-55 (the EIP-55 specification itself publishes a canonical
  set of mixed-case example addresses derived from a fixed test private key/hash) — unlike T11's own
  seed-data problem (real mainnet contract addresses this task had no way to verify from memory), EIP-
  55's own published test vectors are exact, standard, and independently well-known; whether to use them
  verbatim or construct fresh ones via `Keys.toChecksumAddress` itself (self-consistent, not dependent on
  recalling the EIP's exact published strings correctly) is a Phase 2 decision.

## 5. Known gaps / unknowns

- **No numeric/structural completeness requirement is stated for Tron addresses beyond "Base58Check
  validity."** Whether `AddressValidator` should also enforce the 21-byte/`0x41`-mainnet-prefix shape
  (beyond bare checksum correctness) is not specified by R16's own wording ("enforce Base58Check
  validity") — a Phase 1/2 decision, not assumed here.
- **Whether an all-lowercase/all-uppercase EVM address should be rejected (strict EIP-55) or accepted as
  a valid non-checksummed fallback is not spelled out explicitly** — this task's own reading, given L8's
  "mandatory"/"invalid addresses are rejected" framing, leans toward strict rejection, but this is an
  interpretation, not a directly quoted rule, and deserves explicit resolution (and Kimi challenge) at
  Phase 1/2 rather than being assumed silently.
- **No caller/integration point exists yet for `AddressValidator`** — consistent with every prior task's
  own pattern, not a gap specific to this task.
