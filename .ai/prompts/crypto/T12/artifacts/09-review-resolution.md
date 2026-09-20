# crypto · T12 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-05. Findings from Phase 7 (self-review) and Phase 8 (Kimi
independent review) are consolidated below — Kimi Issues 1 and 3 independently confirmed self-review
Findings 1 and 2. No public API changed, no class renamed.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review Finding 1 / Kimi Issue 1 — `isValidTronAddress` throws uncaught `NegativeArraySizeException` for input under 5 characters | **ACCEPTED** | Added `MIN_TRON_ADDRESS_LENGTH = 25` (rejects short input before ever reaching `Base58Check`) and broadened the `catch` from `IllegalArgumentException` to `RuntimeException` (defense-in-depth) in `AddressValidator.java`. Both the min-length guard alone and the broadened catch alone would have closed the specific confirmed bug; both are applied together, as recommended. |
| 2 | Kimi Issue 2 — `AddressValidatorTest` is missing from the working tree | **REJECTED** | No change. Same misreading of this pipeline's own phase separation already corrected once in T11's Phase 9: Phase 6's own directive states "Do NOT write tests here (that is Phase 10)." Tests are correctly deferred, not missing. |
| 3 | Self-review Finding 2 / Kimi Issue 3 — no minimum-length guard mirroring the existing maximum-length guard | **ACCEPTED (same change as #1)** | `MIN_TRON_ADDRESS_LENGTH` added. |
| 4 | Kimi Issue 4 — test vectors/required tests omit short/empty Tron inputs | **ACCEPTED (noted for Phase 10)** | The frozen brief (Phase 4) is not reopened for this — instead, noted here as additional required tests for Phase 10 to add: `rejectsAnEmptyTronAddress`, `rejectsAOneCharacterTronAddress`, `rejectsAFourCharacterTronAddress`, and a boundary test at exactly `MIN_TRON_ADDRESS_LENGTH - 1` / `MIN_TRON_ADDRESS_LENGTH`. |
| 5 | Kimi Issue 5 — Phase 6 implementation notes overstated AC9 as satisfied by the `null` checks alone | **ACCEPTED (corrected here, not by retroactively editing Phase 6's artifact)** | Correction: AC9 (null-safety / never-throwing) is satisfied by the `null` checks **and** the Phase 9 fix above (the min-length guard plus broadened catch) — the Phase 6 claim was incomplete, since the short-input exception path existed at that point and was only caught afterward. Phase 6's own artifact is left as the point-in-time record it is; this table is the correction of record. |
| 6 | Kimi Issue 6 — no explicit verification `TokenModuleBoundaryTest` still passes with `AddressValidator` present | **ACCEPTED — verified now** | `mvn -pl services/crypto test -Dtest=TokenModuleBoundaryTest` — passes (exit 0). `AddressValidator`'s only imports (`org.web3j.crypto.Keys`, `org.tron.trident.utils.Base58Check`, `org.springframework.stereotype.Component`, `java.util.regex.Pattern`) are all external libraries or JDK, none from `adapter/`, `observation/`, `provider/`, or `quorum/`. |
| 7 | Kimi Issue 7 — strict EIP-55 policy allegedly rejects the all-zero EVM address | **REJECTED — factually incorrect premise, confirmed by direct execution** | `0x0000000000000000000000000000000000000000` contains no alphabetic hex characters at all, so EIP-55's case-checksum has nothing to act on — `Keys.toChecksumAddress` returns it unchanged, and `isValidEvmAddress` returns **`true`** for it, not `false` as Kimi's finding assumed. No code change (the existing behavior is correct and requires no fix), but noted here as a genuinely non-obvious nuance worth an explicit, correctly-documented test in Phase 10 (asserting acceptance, not rejection) so this doesn't get "corrected" into a bug later by someone recalling Kimi's incorrect assumption instead of the verified behavior. |

## Summary

3 accepted with the same code fix (1, 3, and 4's underlying cause), 2 accepted as documentation
corrections/verifications with no further code change (5, 6), 1 accepted as a test-vector note for
Phase 10 (4), 2 rejected — one a repeated phase-separation misunderstanding (2), one a factually
incorrect premise this session's own direct-execution verification disproved (7).

`mvn -pl services/crypto compile` succeeds cleanly after the fix, with zero new warnings.
`TokenModuleBoundaryTest` re-verified passing with `AddressValidator.java` present.

Files changed in this phase: `AddressValidator.java` only. No public method signature changed; no
class renamed.
