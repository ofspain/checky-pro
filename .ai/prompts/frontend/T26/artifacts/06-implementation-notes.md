# frontend · T26 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

T26 is a test-only task, so this phase wrote the named test itself. Phase 10 extends it.

---

## What changed

**One file created.** `frontend/src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` — 42 cases in ten
`describe` blocks. **No production file was modified**, as the frozen brief anticipated.

**Verification.** `tsc --noEmit` clean. The file alone: **42 passed**. Whole suite: **1234 passed / 1 failed**,
exit code 1 — the single failure is the pre-existing environmental one,
`lintRejectsHandWrittenBackendFetch.test.ts:118` (this host's `oxlint@1.87.0` prints no summary line), unchanged
by this task. 1192 before + 42 = 1234, so every new case is accounted for.

## How each block maps to the plan and the criteria

| Block (`describe`) | Plan | AC | Content |
|---|---|---|---|
| the inventory is derived from the two modules that own it | B1 | AC1, AC8, AC10 | Four keys assembled from `AUTHORIZE_STORAGE_KEYS` + `RETURN_TARGET_KEY`, each with an owner and a reason; the count pinned at four with the carve-out message; **a scan of this file's own source proving no key literal appears in it**, needles built from the imported constants at runtime. |
| every state of the flow holds only inventory keys | B2 | AC1, AC2 | Empty before anything starts (and mounting the SPA adds nothing); three after authorize; four with a target remembered; **empty after a successful callback**; and six discard paths, each asserting the store still holds only the four. |
| no key outside the carve-out is ever written, even transiently | B3 | AC3, AC4 | The four writes in order with their values, the four removals, and `clear()` asserted never-called rather than inferred. |
| the two instruments, and why both are needed | B4 | AC3, AC9 | Four cases, one per indirect call form: each fails the widened scan, **and** is caught by the prototype watch, **and** really lands in the store. Plus a case proving the widened patterns still permit the two legitimate writers. |
| only the two modules that own the carve-out write to a store | B3, B7 | AC3, AC6 | No third writer and no `clear()` caller in production source; each permitted module touches only its own keys; no production module writes `localStorage` at all. |
| no forbidden client-side surface is even named | B7 | AC7 | Six surfaces absent from live production code; the name/call distinction demonstrated; and a case recording that jsdom defines none of them, so the claim **cannot** be behavioural. |
| nothing R29 forbids is in the store, as a key or as a value | B5 | AC5, AC11 | The four values at their fullest and every write across a whole sign-in, swept for the access token, the ID token whole and by segment, its decoded claims, the authorization code, a password, a recovery code, an API key, an email address and an account UUID — plus two sentinels. |
| an unreachable store is not an empty one | B8 | AC12, AC9 | The key-only check passing and the real sweep failing on the same throwing store; an empty store reported as empty; a self-disagreeing enumeration reported unreachable. |
| the instruments are proved against a dirty store before they are believed | B9 | AC9 | Out-of-inventory key fails the sweep; a key written **and removed** fails the write check though the store ends clean; synthetic offending lines fail both scans; an empty tree is refused; and a watch installed late reports zero writes for a flow that made four. |
| repetition and resumption add no fifth key | B10 | AC1, AC2, AC8 | Two sign-ins in one tab; **discard → fresh sign-in → completed callback**; a callback replayed after it succeeded. |

Execution followed the plan's order: inventory, sweep, watch, flow harness, behavioural blocks, scans,
indirection, controls, repetition.

## The one finding of this phase

**A decoded-claim sweep collides with L16's own carve-out, and the first run proved it.**

Two cases failed on the first run. Neither was a production fault and neither was a careless assertion: the ID
token carries a `nonce` claim, the authorize step puts **that same nonce** in sessionStorage under a key L16 names,
and a sweep over "every decoded claim value" therefore reported a **permitted item as a leak**. The failure
message showed the stored nonce sitting between the state and the verifier, exactly where L16 says it may sit.

The resolution is not to weaken the sweep but to make the exclusion explicit and narrow: `decodedClaimsOf`
drops `nonce` **by name**, and the helper's comment says why. T22 reached the same conclusion independently —
its `decodedClaimsOf` excludes the nonce with the same reasoning, that searching for it "would contradict AC2 —
which asserts it PRESENT — and turn a sanctioned value into a reported leak". Two files arriving at the same
exclusion from opposite directions is the strongest evidence available that the exclusion is right rather than
convenient.

A case was added to hold the exclusion to one claim: it asserts the nonce is dropped, asserts `sub` is **not**,
and plants `sub` under a legitimate key to prove the decoded search still bites. Without it, "exclude a claim" is
one edit away from "exclude the claims that fail".

## Deviations from the plan, flagged rather than hidden

1. **`piecesOf` was split in two.** The plan had one helper returning the whole token, its segments and its
   decoded claims. The nonce collision forced the split into `piecesOf` (whole + segments, mechanical) and
   `decodedClaimsOf` (semantic, with one documented exclusion). This also matches the shape T22 already uses, so
   the two files now read the same way. **No criterion is weakened:** AC5 still covers whole token, each segment
   and each decoded claim value, less one claim L16 permits by name.
2. **The reachability block has three cases, not two.** The plan named the throwing-store pair; a third was added
   for an enumeration that disagrees with itself (`key(i)` stubbed to return `null` while `Object.keys` still
   answers). It costs one case and closes the other way a sweep can report "empty" for a store it failed to read.
3. **`B1`'s self-scan asserts a positive control.** Beyond "no key literal appears here", it asserts a string
   that *does* appear, so the scan cannot pass by reading an empty file — the same vacuity guard
   `productionSources()` applies to the tree.
4. **No `StrictMode` double-render case.** T24 needed one because its subject was an effect with a latch; T26's
   subject is a store, and every state assertion here is taken after the flow settles. Recorded as a deliberate
   omission rather than an oversight.

## What is not yet done

Phase 10 owns the mutation harness and any case the Phase 8 review asks for. The harness will be **committed with
these artifacts**, not left in a scratchpad: T14–T24's lived outside the repo, which makes their scores
reportable but not reproducible. Tests are committed before any mutation run, because the harness reverts with
`git checkout`.

---

**Phase 6 complete.** One new file, 42 cases, no production change, one finding resolved by narrowing rather than
by weakening.
