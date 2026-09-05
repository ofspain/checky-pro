# crypto · T12 · Phase 12 — Specification Verification

**Task (verbatim, `tasks.md` #12):** Address validation. Implement `AddressValidator` (EIP-55 for EVM,
Base58Check for Tron) at the boundary (L8, R15/R16).

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R15 — EIP-55 checksum validity for EVM addresses | Yes | `AddressValidator.isValidEvmAddress` (`AddressValidator.java:56-63`) | `AddressValidatorTest.shouldValidateEip55ChecksumForEvmAddresses` (named) + 12 others | No | No |
| R16 — Base58Check validity for Tron addresses | Yes | `AddressValidator.isValidTronAddress` (`:65-83`) | `AddressValidatorTest.shouldValidateBase58ChecksumForTronAddresses` (named) + 14 others | No | No |
| L8 — mandatory address validation, invalid addresses rejected at the boundary | Yes (predicate only) | Both methods return `false` for every invalid shape tested | Full test suite (27 tests) | No | No — "rejected at the boundary" itself is explicitly, disclosedly deferred to a future caller (Amendment #9); this task supplies the predicate, consistent with every prior task's "no real caller yet" pattern |
| AC1 (correctly checksummed EVM → true) | Yes | `:62` — equality against `Keys.toChecksumAddress` | `.shouldValidateEip55ChecksumForEvmAddresses`, `.acceptsTheAllZeroEvmAddress` | No | No |
| AC2 (structurally malformed EVM → false) | Yes | `EVM_ADDRESS_PATTERN` (`:34`), fully anchored | `.rejectsAnEvmAddressOfTheWrongLength`, `.rejectsAnEvmAddressWithANonHexCharacter`, `.rejectsAnEvmAddressWithAVisuallyConfusableNonHexCharacter`, `.rejectsAnEvmAddressThatIsTooLong`, `.rejectsAnEvmAddressMissingTheZeroXPrefix`, `.rejectsAnEmptyEvmAddress` | No | No |
| AC3 (all-lowercase/all-uppercase EVM → false) | Yes | Same equality check (`:62`) | `.rejectsAnAllLowercaseEvmAddress`, `.rejectsAnAllUppercaseEvmAddress` | No | No |
| AC4 (corrupted-checksum mixed-case EVM → false) | Yes | Same equality check | `.rejectsAMixedCaseEvmAddressWithACorruptedChecksum` | No | No |
| AC5 (valid Tron → true) | Yes | `:70-72` | `.shouldValidateBase58ChecksumForTronAddresses` | No | No |
| AC6 (illegal character / wrong checksum → false) | Yes | The `catch (RuntimeException)` (`:73-82`) | `.rejectsATronAddressWithAnIllegalCharacter`, `.rejectsATronAddressWithAWrongChecksum` | No | No |
| AC7 (valid Base58Check, wrong shape → false) | Yes | The explicit length/prefix check (`:72`) | `.rejectsABase58CheckValidStringWithTheWrongPrefixByte`, `.rejectsABase58CheckValidStringWithTheWrongDecodedLength` (Phase 11) | No | No |
| AC8 (oversized Tron input → false, no `Base58Check` call) | Yes | The length guard (`:66-68`), checked before the `try` block | `.rejectsAnOversizedTronAddress`, `.rejectsATronAddressAtTheMaximumLength` (Phase 11) | No | No |
| AC9 (null-safety, never throwing) | Yes | Both methods' leading `null` checks, plus the Phase 9 `MIN_TRON_ADDRESS_LENGTH` guard and broadened catch (`:66-68`, `:73`) — corrected from Phase 6's incomplete claim (Amendment/Phase 9 Issue 5) that the `null` checks alone were sufficient | `.rejectsANullEvmAddress`, `.rejectsANullTronAddress`, `.rejectsAnEmptyTronAddress`, `.rejectsAOneCharacterTronAddress`, `.rejectsAFourCharacterTronAddress`, `.rejectsATronAddressJustBelowTheMinimumLength`, `.rejectsATronAddressAtTheMinimumLength` (Phase 11) | No | No |

## Amendments (Phase 3, 9 findings; Phase 8, 7 findings; Phase 11, 7 gaps) — verification

**Phase 3 (design challenge), all 9 verified implemented as decided:** the strict EIP-55 interpretation
(Amendment #1) is unchanged and now prominently documented in the class Javadoc with its operational
consequence stated explicitly; the T11 casing-mismatch integration note (Amendment #2) and the
"rejected at the boundary" deferral note (Amendment #9) are both documented in the class Javadoc, not
silently dropped; the literal-lowercase-`0x`-prefix (Amendment #4) and no-trimming (Amendment #7)
decisions are both implemented via the single fully-anchored regex and independently tested; the
64-character Tron length guard (Amendment #5) is implemented and tested at its exact boundary (Phase
11); the `TronAdapter.ADDRESS_PREFIX` cross-reference comment (Amendment #8) is present.

**Phase 8 (independent review), 5 in full + 2 rejected (one correctly, one on a factually-wrong
premise this session's own direct execution disproved), all verified:** the headline fix — the
confirmed `NegativeArraySizeException` escape for short Tron input — is closed via both a
`MIN_TRON_ADDRESS_LENGTH` guard and a broadened `catch (RuntimeException)`, independently confirmed by
this session's own direct execution against the real `trident` library both before and after the fix.
`TokenModuleBoundaryTest` was re-verified passing with `AddressValidator.java` present. The Phase 6
implementation notes' incomplete AC9 claim is corrected in the Phase 9 resolution log. The zero-address
behavior (Kimi's own premise was wrong) is now correctly documented and tested as **accepted**, not
rejected — `AddressValidatorTest.acceptsTheAllZeroEvmAddress` locks this in precisely so a future
maintainer doesn't "fix" it into a bug based on the disproven assumption.

**Phase 11 (test review), 6 full + 1 rejected, all verified:** all six accepted test additions exist and
pass, including a freshly-computed, verified Base58Check test vector for the wrong-decoded-length branch
of AC7 (distinct from the already-covered wrong-prefix-byte branch) and exact-boundary tests at both
`MIN_TRON_ADDRESS_LENGTH` and `MAX_TRON_ADDRESS_LENGTH`. The rejected suggestion (static-mocking
`Base58Check` to force an arbitrary exception) remains undone, with the existing code comment serving
as the documented defense-in-depth rationale Kimi's own finding accepted as a fallback.

## Files-to-create / Files-to-modify conformance

The one file listed under "Files to Create" in the frozen brief exists at its exact specified path
(`AddressValidator.java`). "Files to Modify: None expected" held — no other file was touched in Phases
6 or 9. No file under "Files NOT to Modify" was touched: `TokenAllowlist.java`/
`TokenAllowlistRepository.java`/`TokenValidator.java` (T11), `TronAdapter.java` (T07, referenced in a
comment only), `TokenModuleBoundaryTest.java` (T11, unmodified — it automatically covers the new file
by scanning the whole package), and nothing under `spec/`.

## Required Tests conformance

All required tests from the frozen brief exist, plus the Phase 11 (Kimi)-driven additions layered on
top (all human-approved 2026-09-05): 6 new test cases covering the wrong-decoded-length Tron branch,
too-long/missing-prefix/empty-string/visually-confusable-character EVM cases, and both exact Tron
length boundaries. Current suite state (last full run, this session): 372 module tests total, 364
passing, 8 errors — all Docker-environment-unavailable (`IllegalState: … Docker environment …`), the
same pre-existing, disclosed environment limitation carried unchanged from T11's own baseline (this
task introduces no persistence layer, so no new Docker-gated test exists). Zero genuine failures.

## Principal-engineer review

**(1) Is the task fully complete?** Yes. `AddressValidator` exists exactly as specified, both methods
are implemented and independently tested (27 tests total), with no Docker dependency at all — this is
the first task in this session's own crypto-service work whose full test suite actually executes in
this environment with zero caveats.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC9, see matrix above, each with
file:line evidence and passing tests.

**(3) Does it violate any LOCKED decision?** No. L8's checksum/Base58Check requirements are both
implemented; the "rejected at the boundary" half of L8 is explicitly, disclosedly out of this task's own
scope (no caller exists anywhere in this codebase yet), matching every prior task's identical pattern —
not a violation, a documented scope boundary. No cross-module import violation: `AddressValidator`
imports only `org.web3j.crypto.Keys`, `org.tron.trident.utils.Base58Check`, and
`org.springframework.stereotype.Component` — no sibling feature package, independently confirmed by
`TokenModuleBoundaryTest`.

**(4) Remaining risks?**
- L8's "rejected at the boundary" clause is not yet enforced end-to-end by any real caller — a
  disclosed, expected gap (Amendment #9) that a future task (most plausibly the watch registration API,
  task 15) must close by actually wiring this validator in.
- The casing mismatch between this validator (requires mixed-case checksummed EVM input) and T11's
  allowlist (stores lowercase, matches exact-string) is documented but not yet resolved by any code —
  whichever future task chains the two together must remember to lowercase after validation, before
  lookup (Amendment #2). This is a real integration hazard for that future task to get right, not a
  defect in this one.
- `AddressValidator`'s Tron path relies on a third-party library (`trident`) whose exception behavior
  for malformed input was found, during this task's own review cycle, to include at least one
  undocumented, confirmed-by-execution edge case (`NegativeArraySizeException` for short input) beyond
  its own stated `IllegalArgumentException` contract. The broadened `catch (RuntimeException)` is
  deliberate defense-in-depth against further unenumerated cases of the same kind that this review did
  not specifically test for.
- The strict EIP-55 interpretation (Amendment #1) is a genuine, disclosed platform policy choice
  stricter than raw EIP-55 itself — any future client integrating with this system must re-checksum a
  lowercase address before submission, an operational consequence now explicitly documented but not
  something this task can enforce on external callers.

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion for T12 is implemented with
file:line evidence and test coverage. This task's own review cycle caught and fixed a genuine,
independently-confirmed exception-safety bug in third-party library usage before it reached
verification, and correctly identified and reversed a factually incorrect independent-review premise
(the zero-address behavior) using this session's own direct-execution-verification discipline rather
than accepting it at face value. Remaining risks are disclosed, expected consequences of this task's
own deliberately narrow scope (no caller wired yet), not defects.
