# crypto · T11 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`TokenAllowlistProperties.java`, `TokenAllowlist.java`,
`TokenAllowlistRepository.java`, `TokenValidator.java`, `TokenAllowlistSeeder.java`,
`V5__crypto_app_token_allowlist_grant.sql`, `application.properties`,
`ChainBaselineMigrationIntegrationTest.java`) against the frozen brief and `agents.md`. No code changed
in this phase — findings only, per the phase directive.

---

## Finding 1 — "Current version" is computed globally across all chains, so a version bump on one chain silently breaks validation for every other chain that hasn't been bumped to match

**Severity:** High

**Evidence:** `TokenValidator.validate` (`TokenValidator.java:53-60`) calls
`repository.findTopByOrderByVersionDesc()` with no `chain` filter — this returns the single highest
`version` value across the **entire table**, regardless of which chain it belongs to. It then looks up
`(chain, contractAddress, thatGlobalMaxVersion)`. If, say, a future config change adds a new Ethereum
entry at `version = 2` while Tron's entries remain at `version = 1` (a completely realistic scenario —
different chains' allowlists have no reason to change in lockstep), every Tron lookup would compute
`thatGlobalMaxVersion = 2`, then search for Tron rows at version 2 — finding none, since Tron was never
bumped — and report `UNKNOWN_TOKEN` for every previously-valid Tron token. This is not a superseded-
version edge case; it would silently break an entire chain's worth of otherwise-correct, unchanged
allowlist entries the moment any other chain's version advances. Neither this session's own Phase 3/4
design work nor Kimi's independent review caught this before implementation.

**Recommendation:** Scope "current version" per chain, not globally:
`findTopByChainOrderByVersionDesc(String chain)` (or equivalent), so each chain's lookups are governed
by that chain's own highest version, independent of any other chain's version number. This is a design
change to `TokenAllowlistRepository`/`TokenValidator`, not just a test gap — the frozen brief's own
"single, global current version" decision (Amendment discussion, Phase 2/4) should be revisited.

---

## Finding 2 — `{"ETHEREUM", "TRON"}` is now hardcoded independently in a third, unlinked location

**Severity:** Low

**Evidence:** `TokenValidator.KNOWN_CHAINS` (`TokenValidator.java:38`) hardcodes the same two-chain set
already independently hardcoded in `ProviderProperties`'s `@Pattern(regexp = "ETHEREUM|TRON")` and
`FinalityProperties`'s equivalent `@Pattern`. This mirrors an existing duplication pattern in this
codebase (not a new problem T11 introduces), but adding a third independent copy makes a future
chain addition (e.g., Solana per package.md's own roadmap mention) more error-prone — three unlinked
places to update, with no compiler-enforced consistency between them.

**Recommendation:** Low priority given it matches existing codebase precedent; worth consolidating into
one shared constant (e.g., in `common/`) if a third chain is ever actually added, rather than fixing
proactively now.

---

## Finding 3 — `TokenAllowlistSeeder` has no defensive null-check on `properties.entries()`

**Severity:** Low

**Evidence:** `TokenAllowlistSeeder.run` (`TokenAllowlistSeeder.java:44-48`) iterates
`properties.entries()` directly. `@NotEmpty` on `TokenAllowlistProperties.entries` prevents a null/empty
list in a properly Spring-validated context, but `TokenAllowlistSeeder` itself has no independent guard
— if ever constructed with a manually-built `TokenAllowlistProperties` bypassing Bean Validation (e.g.,
in a test), a null `entries` list throws an unnamed `NullPointerException` from inside the for-each loop
rather than a clearly-worded one.

**Recommendation:** Low priority given Spring's own validation covers the real, wired-up path; worth a
named `Objects.requireNonNull` if this class is ever constructed outside a validated Spring context.

---

## Finding 4 — `decimals`'s range check (`[0, Short.MAX_VALUE]`) is far looser than any realistic token decimals value

**Severity:** Low

**Evidence:** `TokenAllowlist.toShort` (`TokenAllowlist.java:91-97`) accepts any value from 0 to 32767.
Real ERC-20/TRC-20 token decimals are virtually always 0-18 (rarely up to ~24). A config typo like
`decimals=600` would pass this check silently rather than being caught as an obvious data-entry error,
even though it's far outside any plausible real value.

**Recommendation:** Low priority; the current bound correctly prevents a `short`-overflow-style
data-corruption bug (its actual purpose, mirroring `QuorumDecision`'s identical precedent) but does not
catch an implausible-but-technically-valid value. A tighter bound (e.g., `[0, 24]`) would need
justification not given anywhere in this task's own spec; not changed without one.

---

## Summary table

| # | Issue | Severity |
|---|-------|----------|
| 1 | Global (not per-chain) "current version" silently breaks lagging chains | High |
| 2 | `{"ETHEREUM","TRON"}` hardcoded in a third, unlinked location | Low |
| 3 | `TokenAllowlistSeeder` has no defensive null-check on `entries` | Low |
| 4 | `decimals` range check looser than any realistic value | Low |

(End of self-review.)
