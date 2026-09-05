# crypto · T13 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`AddressPoisoningDetector.java`) against the frozen brief and
`agents.md`. No code changed in this phase — findings only, per the phase directive.

---

## Finding 1 — The uniform (chain-agnostic) prefix threshold, while correctly fixing EVM's entropy-loss issue, makes Tron detection slightly more conservative than the original design intended

**Severity:** Low

**Evidence:** `PREFIX_MATCH_LENGTH = 6` (`AddressPoisoningDetector.java:51`) was raised specifically to
restore 4 *real* matching hex digits for EVM beyond its universal `"0x"` prefix (Amendment #1). Tron
mainnet addresses share only a single universal character (`"T"`), so under the *original* flat-4
threshold, a Tron match already required 3 real matching characters beyond `"T"` — a reasonable signal
given Base58's 58-symbol alphabet. Raising the threshold uniformly to 6 for *both* chains (a deliberate
simplification to avoid making this detector chain-aware, per Amendment #1's own stated tradeoff) means
a Tron match now requires 5 real matching characters beyond `"T"`, not 3 — stricter than the original
design's own intent for Tron specifically, and could miss a small class of borderline Tron look-alikes
that a Tron-tuned threshold would have caught. This is a disclosed, deliberate compromise (documented in
the class Javadoc and the frozen brief's own Amendment #1 reasoning), not an oversight, but it is worth
stating explicitly as a real, non-zero cost of the chosen fix rather than presenting the fix as costless.

**Recommendation:** No action needed within this task's own scope — the tradeoff was already
deliberately accepted at the Phase 4 gate. Worth revisiting only if operational experience with real
Tron traffic ever suggests the current threshold under-catches; the class's own Javadoc already notes a
future task may externalize either threshold as configuration if that need arises.

---

## Finding 2 — Important behavioral contract details are documented only at the class level, not on the `detectPoisoning` method itself

**Severity:** Low

**Evidence:** Null-handling, the "arbitrary match among multiple" behavior (AC6), and the case-sensitive/
no-normalization contract (AC5) are all explained in the class-level Javadoc (`AddressPoisoningDetector.java:8-41`)
but `detectPoisoning` (`:58`) itself carries no method-level Javadoc. A caller or an IDE's hover-tooltip
view focused on just the method signature (a common way developers inspect an unfamiliar API) would not
see these contract details without separately opening and reading the class-level documentation.

**Recommendation:** Low priority; consider moving (or duplicating a condensed version of) the most
caller-relevant contract points — null-safety, case-sensitivity, and the arbitrary-match caveat — onto
the method's own Javadoc, where a caller is most likely to look first.

---

## Summary table

| # | Issue | Severity |
|---|-------|----------|
| 1 | Uniform prefix threshold slightly under-tunes Tron detection relative to the original per-chain intent | Low |
| 2 | Key behavioral contracts documented only at class level, not on the public method itself | Low |

(End of self-review.)
