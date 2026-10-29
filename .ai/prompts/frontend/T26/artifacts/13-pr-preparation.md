# frontend · T26 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 is PASS. Prepared for merge.

---

## Commit title

```
feat(frontend/T26): prove sessionStorage holds only the L16 items
```

## Commit message

```
feat(frontend/T26): prove sessionStorage holds only the L16 items

Add `sessionStorageHoldsOnlyTheL16Items`, the package-wide guard that L16's
carve-out is exhaustive. Twelve tasks have written to a client-side store, each
proving its own item was permitted; this asks the opposite question, and it is
the only file in the package that asks it: is anything there that is not on the
list?

Derive the inventory from the two constants that own it - AUTHORIZE_STORAGE_KEYS
and RETURN_TARGET_KEY, deliberately unmerged under T24-D4 - rather than spelling
four keys that would keep passing after a rename. A case scans this file's own
source to hold that rule.

Verify with three instruments, each watched to fail before it is believed:

- a runtime watch on Storage.prototype, which is alias-proof by construction;
  measured, it sees a bracket call, a bound alias, .call and .apply alike;
- a source scan over every production module, comments stripped, with a table of
  which file may call which write method and why;
- a namespace scan keyed on the name of the thing stored rather than the call
  that stores it. A mutant written to escape - a fifth key with a computed method
  name, behind a condition no test produces - is invisible to the first two and
  caught by this one.

Read values as well as keys. Measured: with getItem throwing, Object.keys still
returns every key and length is still right, so a key-only sweep PASSES over a
store it never read. The sweep cross-checks three enumerations and reports
unreachable separately from empty, failing on the first.

Exclude the ID token's `nonce` claim from the decoded-claim sweep, by name. L16
permits the nonce in the store and the authorize step puts it there, so a sweep
over every claim reports a sanctioned item as a leak - which is what the first
run did. The exclusion is one claim wide and a case holds it there.

No production code changes. L16 clause (b) - non-sensitive mid-task form input
with a 15-minute TTL - is permitted by the spec, absent from the codebase, and
owned by no task in tasks.md; the inventory pin names it as the expected next
entrant so the day it lands the failure reads as an instruction.

Tests: 53 cases in the named test; suite 1245 passed, 1 pre-existing
environmental failure; 22 of 22 mutants killed, every one by this file alone.

Verifies: sessionStorageHoldsOnlyTheL16Items (package.md §8)
Spec: spec/frontend/tasks.md task 26; R29; L16 (with L3, L17, design.md §5)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

> Note on the trailer: the Phase 13 template asks for `Claude Opus 4.8 (1M context)`, which no longer matches the
> model running these phases. The trailer above names the model that did the work. **This is the fifth
> consecutive task to report it** (T22, T23, T24, T25, T26) — the fix belongs in `.ai/generate.py`, which
> generates every task's phase 13 prompt, not in another per-task correction.

## Files changed

| File | Change | Lines |
|---|---|---|
| `frontend/src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` | **new** — the named test | ~1450 |
| `.ai/prompts/frontend/T26/artifacts/*.md` | **new** — the 14-phase record | 13 artifacts |
| `.ai/prompts/frontend/T26/artifacts/07-mutation-harness.py` | **new** — 22 mutants, committed and runnable | ~215 |

**No production file was modified.** Verified: `git status` clean after every run, and the mutation harness
reverts with `git checkout -- src/auth/`.

## Summary

L16's final sentence is "This list is the entire carve-out." That is an exhaustive claim, and an exhaustive claim
cannot be proven item by item — it needs one place that enumerates the whole inventory and sweeps the surface
against it. T26 is that place.

The work was shaped by four measurements rather than by argument:

1. **Mounting the SPA writes no key**, and after a successful callback the store is **empty** — so the inventory
   has no third-party entrant to tolerate, and the end state of a flow is zero keys, not four.
2. **A key-only sweep is immune to a broken store.** With `getItem` throwing, `Object.keys` still returns all
   four keys. The obvious implementation reports compliance for a store it never read.
3. **A `Storage.prototype` spy sees all four indirect call forms**, and those writes really land. That is
   T22-D14's two-net argument with numbers behind it, and it is why the source scan's patterns were widened
   rather than trusted.
4. **A dormant write with a computed method name escapes both instruments.** Written as a mutant to escape, it
   was killed only by a neighbour (T23, which counts key spellings in source) until the namespace scan landed —
   at which point it moved from `INHERITED-ONLY` to `own`. One column change in a harness report, and it is the
   entire justification for the third instrument.

Two findings were reported rather than absorbed. **L16 clause (b) has no implementation and no owning task**, and
its named test is claimed by none of the 125 tasks — the guard names it as the expected next entrant instead of
pretending the carve-out is three items wide. And the **`localStorage` scan originally forbade what
`design.md:193` permits**, so it would have failed a legitimate theme toggle while its message stated the rule
that change complies with; it now enforces what L16 and L3 actually forbid.

## Testing performed

| What | Result |
|---|---|
| `tsc --noEmit` | clean |
| `vitest run src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` | **53 passed**, exit 0 |
| `vitest run` (whole suite) | **1245 passed / 1 failed** (1246 collected) — `api/lintRejectsHandWrittenBackendFetch.test.ts:118`, pre-existing and environmental (this host's `oxlint@1.87.0` prints no summary line) |
| Mutation, 22 mutants in 8 families | **22 killed, 0 survived, 0 inherited-only** |
| Harness | `python3 -I .ai/prompts/frontend/T26/artifacts/07-mutation-harness.py [MA<n>]` from the repo root |
| Exit codes | read as the verdict throughout, never pass counts |

Mutant families: a fifth key (MA1–MA4) · a third writing module or a reader that starts writing (MA5–MA6) · the
indirect call forms (MA7–MA9) · the wrong store or a wipe (MA10–MA13, MA12 being the `clear()` mutant that
escaped T25) · a forbidden surface named (MA14–MA16) · a secret under a legitimate key (MA17–MA18) · the
transient write (MA19–MA20) · two written to escape (MA21–MA22).

## Specification references

- **Task:** `spec/frontend/tasks.md` task **26** — "Session storage holds only L16 items."
- **Requirement:** **R29** (`requirements.md:101-103`), with **R7/L3** as the boundary.
- **LOCKED decisions:** **L16** (`design.md:58-60`), **L3** (`design.md:13`), **L17** (`design.md:61-62`),
  `design.md` §5 (`:191-196`), and `agents.md:79-81`'s storage, logging and privacy rule.
- **Named test:** `sessionStorageHoldsOnlyTheL16Items` (`package.md:160`).
- **Inherited decision honoured:** T24-D4 — the carve-out is two constants in two modules, unmerged.
- **Open questions carried to the author:** OQ2 (clause (b) and its unowned named test), OQ3 (L16's absolute
  sentence versus the service-worker precache), OQ4 (whether the six-surface list is closed).

---

**T26 COMPLETE — 14 of 14 phases.** Next task: **T27 — the SAS MFA challenge resumes at the callback**
(`tasks.md:39`, verifying `loginChallengeRunsInsideSasFlowAndResumesAtCallback`, citing R9).
