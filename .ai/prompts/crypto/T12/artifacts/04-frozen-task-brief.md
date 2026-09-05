# crypto · T12 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-05. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Address validation. Implement `AddressValidator` (EIP-55 for EVM, Base58Check for Tron) at the boundary
(L8, R15/R16).

## Purpose

The structural gate that stops a malformed or maliciously-crafted address string from ever being
trusted as a real on-chain address — this service's entire value proposition rests on never attesting
to something false, and an address is the first, cheapest thing to get wrong.

## Scope

**In:**
- **`AddressValidator`** — stateless `@Component`, two methods:
  - **`boolean isValidEvmAddress(String address)`** — `false` for `null`. Structurally must match a
    fixed regex requiring the **literal lowercase** `0x` prefix followed by exactly 40 hex characters
    (case-insensitive on the hex digits only) — **Amendment #4 (Kimi Issue 4): `0X...` (uppercase X) is
    explicitly rejected**, not treated as an alternate valid prefix; this was already true by
    construction in the Phase 2 regex, now stated as a deliberate decision. The regex is fully anchored
    (`^...$`), so **Amendment #7 (Kimi Issue 7): leading/trailing whitespace is never trimmed and always
    causes rejection** — also already true by construction, now explicit. Then
    `address.equals(Keys.toChecksumAddress(address))`.
    **Amendment #1 (Kimi Issue 1, made explicit and prominent, interpretation UNCHANGED): an
    all-lowercase or all-uppercase EVM address is REJECTED, not accepted as a valid unchecksummed
    fallback.** This is a deliberate, platform-specific policy choice reading L8's own wording ("EIP-55
    checksum on **all** EVM addresses," "**mandatory**," "invalid addresses are **rejected**") more
    strictly than raw EIP-55 itself (which treats an unchecksummed lowercase address as merely
    "checksum-absent," not invalid) — accepting an unchecksummed address here would make L8's own
    checksum mandate trivially bypassable by lowercasing any address, defeating its purpose as a
    typo/spoofing defense. **Operational consequence, stated explicitly per Kimi's own request:** a
    client or UI presenting an address to this system must re-checksum a lowercase address before
    submission; this validator will not accept it as-is.
  - **`boolean isValidTronAddress(String address)`** — `false` for `null`. **Amendment #5 (Kimi Issue
    5): rejects any input longer than 64 characters before ever calling `Base58Check`** — a defensive
    bound against wasted decode work on a pathologically long string, well above any real Tron
    address's ~34-character length. Otherwise delegates to `Base58Check.base58ToBytes(address)` inside
    a `try`/`catch(IllegalArgumentException)`. On successful decode, additionally checks the result is
    exactly 21 bytes with a leading `0x41` byte (Tron mainnet's own shape). **Amendment #8 (Kimi Issue
    8, documentation only): the `0x41` constant is defined locally in `AddressValidator`, with an
    explicit code comment cross-referencing `TronAdapter.ADDRESS_PREFIX`'s identical, `private` value**
    (T07) — the two cannot be consolidated without either widening `TronAdapter`'s own field visibility
    or a broader `common/` refactor, both out of this task's scope (mirrors T11's identical reasoning
    for not touching T03's frozen `ProviderProperties`/`FinalityProperties`).
- **Amendment #2 (Kimi Issue 2, documentation only — a forward-looking integration note): `AddressValidator`
  and T11's `TokenValidator`/allowlist are NOT casing-compatible as-is.** `isValidEvmAddress` requires
  mixed-case (checksummed) input; T11's allowlist stores EVM addresses lowercase and `TokenValidator`
  does exact-string matching with no case-folding. **A future caller chaining "validate, then look up in
  the allowlist" must lowercase the address after successful EIP-55 validation and before the allowlist
  lookup, or every legitimately-valid checksummed address will incorrectly report `UNKNOWN_TOKEN`.**
  `AddressValidator` itself performs no such normalization — that responsibility belongs to whichever
  future task actually wires the two together.
- **Amendment #6 (Kimi Issue 6, documentation only): `TokenModuleBoundaryTest` (T11) already scans the
  entire `token/` package for forbidden imports** — it will automatically cover the new
  `AddressValidator.java` with zero changes needed; re-confirmed to still pass once the file exists
  (Phase 10), not a new test.
- **Amendment #9 (Kimi Issue 9, documentation only): L8's "rejected at the boundary" is not fully
  enforced by this task alone.** `AddressValidator` supplies the predicate; no caller/boundary exists
  yet in this codebase to act on it (consistent with every prior task's own "no real caller yet"
  pattern — `Observation`, `QuorumEvaluator`, `TokenValidator` all had none until their respective
  consuming tasks existed). A subsequent task (most plausibly the watch registration API, task 15) must
  wire this validator in and actually reject invalid input for L8 to be fully enforced end-to-end.

**Out:**
- `AddressPoisoningDetector` (L9, R17) — task 13.
- Wiring `AddressValidator` into any actual caller/boundary.
- Throwing a domain-specific exception on invalid input — both methods remain a plain `boolean`
  predicate.
- A single, chain-dispatching `validate(String chain, String address)` method.
- Tron-testnet or other non-mainnet address prefix bytes.
- Widening `TronAdapter.ADDRESS_PREFIX`'s visibility or consolidating it into `common/`.

## Business Rules

- **R15.** EVM address validation enforces EIP-55 checksum validity — strictly (Amendment #1).
- **R16.** Tron address validation enforces Base58Check validity, plus the mainnet address shape.

## Locked Decisions

- **L8.** Address validation is mandatory; invalid addresses are rejected at the boundary (the boundary
  itself is a future task's concern per Amendment #9 — this task supplies the predicate).

## Dependencies

- `org.web3j.crypto.Keys` (T06, existing).
- `org.tron.trident.utils.Base58Check` (T07, existing).
- No new external library dependency, no `pom.xml` change.

## Inputs

- A single `String` address (EVM or Tron, depending on which method is called).

## Outputs

- `boolean` — `true` if structurally valid and correctly checksummed for its chain, `false` otherwise
  (including for `null`, an oversized Tron input, or a whitespace-padded/wrong-case-prefixed EVM input).

## State Changes

None — a pure function.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/token/AddressValidator.java`

## Files to Modify

None expected.

## Files NOT to Modify

- `token/TokenAllowlist.java`/`TokenAllowlistRepository.java`/`TokenValidator.java` (T11).
- `adapter/tron/TronAdapter.java` (T07) — referenced (its `0x41` prefix value) in a comment only.
- `TokenModuleBoundaryTest.java` (T11) — automatically covers the new file, not itself modified.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R15, L8).** A correctly EIP-55-checksummed, well-formed EVM address returns `true`.
- **AC2 (R15, L8).** A structurally malformed EVM address (wrong length, non-hex characters, missing or
  wrong-case `0x`/`0X` prefix, whitespace-padded) returns `false`.
- **AC3 (R15, L8, Amendment #1).** An all-lowercase or all-uppercase EVM address returns `false`.
- **AC4 (R15, new).** A mixed-case, structurally valid EVM address with a deliberately corrupted
  checksum (at least one wrong case bit relative to the correct checksum) returns `false`.
- **AC5 (R16, L8).** A correctly Base58Check-encoded, 21-byte, `0x41`-prefixed Tron address returns
  `true`.
- **AC6 (R16, L8).** A Base58Check string with an illegal character, or a correct-alphabet-but-wrong-
  checksum string, returns `false`.
- **AC7 (R16, L8).** A Base58Check-valid string that does not decode to 21 bytes with a leading `0x41`
  returns `false`.
- **AC8 (R16, Amendment #5).** A Tron input longer than 64 characters returns `false` without ever
  invoking `Base58Check`.
- **AC9 (null-safety).** Both methods return `false` for a `null` input, never throwing.

## Required Tests

- `shouldValidateEip55ChecksumForEvmAddresses` (package.md §8, named) — AC1, AC3, AC4.
- `shouldValidateBase58ChecksumForTronAddresses` (package.md §8, named) — AC5, AC6.
- A test asserting a structurally malformed EVM address is rejected (AC2), including an uppercase
  `0X` prefix and a whitespace-padded address as explicit sub-cases (Amendments #4, #7).
- A test asserting both an all-lowercase and an all-uppercase EVM address are rejected (AC3).
- A test asserting a mixed-case EVM address with a corrupted checksum bit is rejected (AC4).
- A test asserting a Base58Check string with an illegal character is rejected (AC6).
- A test asserting a Base58Check string with a correct alphabet but wrong checksum is rejected (AC6).
- A test asserting a Base58Check-valid but wrong-length or wrong-prefix-byte string is rejected (AC7).
- A test asserting an oversized (>64 character) Tron input is rejected without a `Base58Check` call
  (AC8).
- A test asserting `null` is rejected cleanly for both methods (AC9).

## Constraints

- **Performance:** the 64-character length guard (Amendment #5) bounds worst-case Tron decode cost.
- **Security:** this task's entire purpose is a security boundary; no secret is introduced or handled.
- **Thread-safety:** `AddressValidator` holds no mutable state; trivially thread-safe.
- **Module boundaries:** no import from `adapter/`, `observation/`, `provider/`, or `quorum/` — only
  `org.web3j.crypto.Keys` and `org.tron.trident.utils.Base58Check` (external libraries).
- **Null handling:** both methods return `false` for `null` (AC9) rather than throwing — a deliberate
  fit for a validation predicate, unlike every prior task's collaborator methods, which treat `null` as
  a programming error.

## Open Questions

No blockers. All 9 Phase 3 findings are resolved above.
