<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T39 · Phase 6 — Implementation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

## Gates

| Gate | Result |
|---|---|
| `vitest run src/api/signOutPropagatesToAllTabs.test.ts` | **21 passed (21)** |
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **91 passed (91)** |
| `vitest run` | **1719 passed / 1 failed of 1720** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

## What was built

| File | |
|---|---|
| `src/api/crossTabSignOut.ts` | **NEW**, ~135 lines — channel, message type, latch, broadcast, listener |
| `src/api/crossTabRenewal.ts` | +15 — one guard, `if (hasSignedOut()) return;` |
| `src/auth/signOut.ts` | +13 — `markSignedOut()` **first**, with the reason |
| `src/auth/accessToken.ts` | +8 — `markSignedIn()` in `retainAccessToken` |
| `src/auth/endSession.ts` | +13 — `broadcastSignOut()` after the sign-out, before the hand-over |
| `src/api/signOutPropagatesToAllTabs.test.ts` | **NEW**, 21 cases |
| `src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | 84 → 91 cases: AC9 replaced, AC9b deleted, AC9c and AC9d added, three instruments grown |

## Four instruments fired, which is why the steps were ordered that way

Phase 5 put the production change before the instrument updates specifically so each existing guard would
be *seen* firing. All four did, and one was not predicted.

| Instrument | What it said |
|---|---|
| **T35's `NOT_SESSION_STATE`** (`:198`) | *"a module holds mutable state that sign-out neither clears nor excuses"*. The latch cannot be in `cleared` — `signOut()` is what **sets** it — so excusing is the only option the scan offers, with a `why` naming it the **inverse** of session state. |
| **T35's namespace scan** (`:664`) | caught `'themistra.session.signout'` as an unaccounted `themistra.` literal. A `BroadcastChannel` name is not a storage key and the scan cannot tell, which is what `NOT_STORAGE` exists for. |
| **T22's `nonStorageNamespaceNames()`** (`sessionStorageHoldsOnlyTheL16Items.test.tsx:691`) | **not predicted by phase 5.** A *second* allowlist for the same namespace, in another suite. Neither is redundant: task 22 owns the carve-out's exhaustiveness, task 35 owns what sign-out clears. |
| **T36's source-derived `SIGN_OUT_SET`** (`:956`) | *"expected 6 to be 5"*. The count is derived from `signOut.ts`'s source, so one line changed — and it changed **because the instrument failed first**, which is the whole reason T36 made it source-derived rather than written down. |

Then the one that was designed to fire:

```
AC9 … KNOWN HAZARD: a signed-out tab still adopts a sibling renewal (task 39 must replace this)
AssertionError: GOOD NEWS, AND THIS CASE MUST CHANGE … expected null to be 'T35-access-token-10'
```

**T35's inherited failing expectation, firing with its own words.** That is the deliverable, not a nuisance.

## Three things measurement corrected while building

### 1. An `as const` object literal does **not** escape T35's mutable scan

Phase 5 stated it did, inferring from `NOT_SESSION_STATE`'s `readAccount.ts` entry — *"the scan reaches
object literals (phase 11 F3) and this one is not `as const`"*. The regex is

```
/^(?:let|var)\s|^const\s+\w+\s*(?::[^=\n]*)?=\s*(?:new\s+(?:Map|Set|WeakMap|WeakSet)\s*\(|\[|\{)/m
```

and it matches `= {` at column 0 **whatever follows**, `as const` included. The scan reported
`declarations: 2`. I read a neighbouring comment instead of the regex. The message is a **string** constant
now (`SIGN_OUT_TYPE`), so the file-level excusal stays at the one thing that is actually state — which
matters, because excusing a file excuses every declaration in it.

### 2. `signOut()` does **not** throw when a store step fails

AC9's throw case first spied `Storage.prototype.removeItem`. `signOut()` did not throw: each store step is
guarded inside its own module, exactly as `signOut.ts`'s header claims. So the throw is injected at the
step **immediately after** the latch, by mocking `./accessToken`, with both halves read from the **same
fresh module graph** — a dynamic import brings its own instance of the latch, which is what T36's phase 8
I10 recorded about module-instance isolation.

### 3. Two scans read prose, and one scan found its own needles

- The new suite's source scans failed on the module's **header**, which names `endSession()` and
  `accessToken.ts:82` while explaining why neither is reachable from there. Comments are stripped now, with
  a **control asserting the strip left the real declarations behind** — an over-eager strip would empty the
  module and pass every scan.
- The retired-wording scan searched for `'KNOWN HAZARD'` and `'GOOD NEWS'` and found **AC9c**, which
  deliberately reuses both to pin a hazard T39 does *not* close. Narrowed to the retired case's own
  sentences, and those are **assembled from fragments** rather than spelled whole — which is the technique
  this very file already uses for storage keys (`:656`, *"spells no whole key of its own, so a rename
  breaks it rather than passing it"*).

## Risks from phase 5, settled by running

| | Outcome |
|---|---|
| **R1** `SIGN_OUT_SET` grows | fired and resolved as designed |
| **R2** the mutable scan | fired; resolved, after correction 1 above |
| **R3** `endSession.ts`'s import scan | **unaffected**, confirmed by running. Adding an import *by* `endSession.ts` is not an import *of* it, and `PERMITTED_IMPORTERS` stays empty. |
| **R4** the three suites that adopt tokens | **did not break.** They call `retainAccessToken`, which releases the latch, so no case lost an adopt. The cross-suite effect phase 5 feared did not materialise. |
| **R5** restoring `BroadcastChannel` | handled in a `finally`, and the case asserts the global is back rather than trusting it |

## What is pinned but not closed

**AC9c** pins the stale-adopt race (**OQ-T39-6**) the way T35 pinned OQ-T35-3: asserted as it *is*, with the
message as the handover, addressed to whoever builds renewal. The latch must be released by any token
assignment — or a tab that signed out and back in could never adopt again — and that release is the hole:
a success from a renewal that *started* before the sign-out can replace a fresh token with an older one.
Closing it needs a generation stamp **on the message**, i.e. changing `crossTabRenewal`'s closed union,
which D1 declined for better reasons.

**OQ-T39-7** is recorded in `crossTabRenewal.ts`'s guard comment: `use()` still *returns* a sibling's token
to a local `renew()` caller in a signed-out tab. Nothing holds it, but a caller awaiting `renew()` receives
it. R22's half-authenticated question is task 33's and renewal semantics are R21's.

## Deviations

| | |
|---|---|
| **D1** | **Nothing installs the listener** — `endSession()` has no caller (OQ-T36-5) and no module owns app-level listeners. Every case installs it directly. **T39 is the ninth consecutive dormant task.** |
| **D2** | Three closed tasks' production files modified (`crossTabRenewal.ts`, `accessToken.ts`, `endSession.ts`). |
| **D3** | Four instruments in three suites grown, two of them in closed tasks' files, as T35's handoff instructed. |
| **D4** | The hazard closed is **latent, not live** — no production caller, and `renew()` always rejects. |
| **D5** | **OQ-T39-6 not closed**, pinned by AC9c. |
| **D6** | **The parser is deliberately lenient** about extra properties, pinned by a case that *acts* on `{ type, reason }`. |
| **D7** | AC3 proves "the SAS session ends once" by **source scan** over the propagation module, not end-to-end — no tab can navigate anywhere because nothing wires the listener (D1). |

## Carried to phase 7

- A mutation harness, **with the tests committed first**.
- AC9c is a case asserting a defect. A harness that "fixes" the defect would turn it red — so **AC9c must be
  excluded from the kill numerator or it becomes a canary**, and phase 7 has to decide which.
- `quiet()` in the new suite is a fixed 50 ms spin. Phase 7 should check whether any case can pass because
  nothing was ever delivered, which is the false-PASS shape T35 warned about in the case I inherited.

## Phase 7

Self-review and mutation testing.
