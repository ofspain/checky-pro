# crypto · T12 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`AddressValidator.java`) against the frozen brief and `agents.md`. No
code changed in this phase — findings only, per the phase directive.

---

## Finding 1 — `isValidTronAddress` throws an uncaught `NegativeArraySizeException` for any short input (empty string through 4 characters), violating the class's own "never throws" contract

**Severity:** High

**Evidence:** `isValidTronAddress` (`AddressValidator.java:57-67`) catches only `IllegalArgumentException`
around `Base58Check.base58ToBytes(address)`. **Confirmed by direct execution against the real,
already-dependency-pinned `trident` library** (not reasoned from bytecode alone): for any input whose
decoded raw Base58 byte length is less than 4 — concretely, `""`, and any string of 1-4 characters
tested (`"A"`, `"AB"`, `"ABC"`) — `Base58Check.base58ToBytes` throws `java.lang.NegativeArraySizeException`
instead, because it internally computes `Arrays.copyOf(raw, raw.length - 4)` without ever checking
`raw.length >= 4` first. `NegativeArraySizeException` is not a subclass of `IllegalArgumentException`,
so it propagates straight out of `isValidTronAddress`, uncaught. This class's own Javadoc explicitly
states "Both methods are `boolean` predicates, never throwing" — that claim is currently false for the
single most trivial hostile/malformed input a boundary validator should be most robust against (an
empty or near-empty string, exactly the kind of input a blank form field or a buggy client would send).
This exact failure mode is also outside the frozen brief's own AC6 (which only names "illegal character"
and "wrong checksum" as the two failure shapes needing coverage) and would not have been caught by any
test in the Phase 5 plan, since every planned Tron test vector is 33-35 characters long, far above the
danger zone. Confirmed via direct execution:

```
len=0 -> threw NegativeArraySizeException: -4
len=1 -> threw NegativeArraySizeException: -3
len=2 -> threw NegativeArraySizeException: -2
len=3 -> threw NegativeArraySizeException: -2
len=4 -> threw NegativeArraySizeException: -1
len=5 -> threw IllegalArgumentException: Checksum mismatch   (correctly caught today)
```

**Recommendation:** Broaden the catch to `RuntimeException` (defense-in-depth against this and any
other unenumerated exception shape this third-party library might throw for other malformed input this
review didn't specifically test), and/or add an explicit minimum-length guard (e.g., reject anything
under ~25 characters outright, mirroring the existing maximum-length guard's own defensive-clarity
style) so the "too short to possibly be valid" case is self-documenting rather than relying solely on a
broad catch. Either fix (or both together) closes the gap; add a required test for empty-string and
very-short input to lock in whichever fix is chosen.

---

## Finding 2 — No explicit minimum-length guard mirrors the existing maximum-length guard's own defensive-clarity style

**Severity:** Low (related to, but distinct from, Finding 1's correctness bug)

**Evidence:** `MAX_TRON_ADDRESS_LENGTH` (`AddressValidator.java:46`) exists specifically to make "this
input is obviously too large to be a real Tron address" an explicit, named, self-documenting check
rather than relying on `Base58Check`'s own behavior. No equivalent minimum-length constant exists on
the other side, even though real Tron addresses are always exactly 34 characters — a `MIN_TRON_ADDRESS_LENGTH`
constant would both close Finding 1's correctness gap and make the validator's own reasoning symmetric
and easier to read.

**Recommendation:** Low priority independent of Finding 1's fix; worth adding for symmetry/readability
even if Finding 1 is fixed via a broadened catch alone.

---

## Summary table

| # | Issue | Severity |
|---|-------|----------|
| 1 | `isValidTronAddress` throws uncaught `NegativeArraySizeException` for short input | High |
| 2 | No explicit minimum-length guard (symmetry with the existing maximum-length guard) | Low |

(End of self-review.)
