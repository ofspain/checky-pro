# frontend · T26 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 (packet assembly; the decision is the author's) |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

**Human Approval gate.** Seven independent findings (I1–I7) and six self-review findings (S1–S6). The two
reviews agree on the substance: I1=S1, I2=S2, I3=S3, I4=S5, I6=S6. **All accepted.** One carries a correction to
the finding's own proposal, and the correction is what makes it work.

---

## I1 = S1 · The third instrument — **ACCEPTED, and designed from measurement**

Both reviews reach the same conclusion from the same mutant: MA22, a fifth-key write whose method name is
computed and whose branch no test drives, is invisible to the runtime watch (it never runs) and to the source
scan (no literal `setItem`). T23 catches it at
`stateSurvivesRedirectAndIsConsumedOnce.test.tsx:851` by counting key-prefix spellings in source. The key's
**name** survives when the call form and the code path do not.

**Resolution.** Phase 10 adds a third instrument keyed on the stored name rather than the storing call, with
MA22 as its control.

## I5 · The shape of that instrument — **ACCEPTED; the proposal corrected and widened**

I5 is right that a prefix literal is too narrow, and names the shapes that would slip past: `themistra.auth_state`
(no dot after `auth`) and `themistra.authx.foo` (a sibling namespace). But **the pattern it proposes does not
catch its own examples**: `/\bthemistra\.auth(?:\.\w+)+\b/` requires a dot immediately after `auth`, so
`themistra.auth_state` fails the `(?:\.\w+)+` group and `themistra.authx.foo` fails the literal `auth` boundary.
The finding is right and its remedy is not, so the remedy was measured instead.

**Measured:** every `themistra`-namespace literal in production source, `grep -roE` over `src/**/*.ts{,x}`
excluding tests. There are **nine occurrences in seven files, and only four are storage keys**:

| Literal | Where | What it is |
|---|---|---|
| `themistra.auth.state`, `themistra.auth.nonce`, `themistra.auth.code_verifier` | `auth/authorizeRequest.ts:36-38` | the inventory |
| `themistra.auth.return_to` | `auth/returnTarget.ts:39` | the inventory |
| `themistra.session.renewal` ×2 | `api/crossTabRenewal.ts:18-19` | `RENEWAL_CHANNEL` and `RENEWAL_LOCK` — a **BroadcastChannel name and a Web Locks name** under L18. Not a store, and nothing is persisted by either. |
| `@themistra/api-client` ×3 | `api/client.ts:1`, `api/sessionBoundary.ts:1`, `auth/codeExchange.ts:1` | the npm package scope in an import path |

So the instrument scans the **whole `themistra` namespace**, not a prefix of it, and every match must be either an
inventory key or a row in a short non-storage allowlist that says what it is and why it is not a store. That
catches both of I5's shapes, catches a sibling namespace nobody has thought of, and — the part a prefix scan
cannot do — makes the next `themistra.*` identifier an explicit decision: its author must say whether it is a
store key. The package-scope import is excluded by requiring the literal to appear inside quotes and not after
`@`, measured against the three real import lines rather than assumed.

**Why this is worth the extra width.** A prefix scan asks "is this key spelled right?" The namespace scan asks
"is this name a store key at all?" — which is the question L16's exhaustiveness actually poses, and the one the
dormant mutant exposed.

## I2 = S2 · The `localStorage` claim — **ACCEPTED**

`design.md:193` permits `localStorage` for "public preferences (theme, locale) only", and the current case
forbids naming it at all, with a message that states the rule a compliant change would comply with. Phase 10
keeps the behavioural claim (empty after every flow this file drives) and narrows the structural one to what L16
and L3 forbid: **no inventory key and no secret-shaped value in `localStorage`**, with the message naming public
preferences as the permitted entrant — the same convention as the inventory count pin.

## I3 = S3 · Two arrivals leave the first screen mounted — **ACCEPTED**

`arriveAt` overwrites `mounted` without unmounting, so three cases in the repetition block abandon a live
`CallbackScreen` whose effects keep running while the next flow's store assertions are taken. Phase 10 replaces
each `mounted = undefined` with `arrival.unmount()`, the idiom T24 and T25 already use. No case fails today,
which is the reason to fix it now rather than when one does.

## I4 = S5 · Exact sets in the six discard rows — **ACCEPTED**

A discard deletes nothing (T23-D2) and does not consume the target (T24-D14), so the set is known: all four.
Phase 10 asserts `keysIn(sweepSession())` equals `inventoryKeys()` in each row, keeping `expectOnlyInventory`
for its message and `expectNoKeyOutsideInventory` for the write-path claim.

## S4 · The forward-looking needles — **ACCEPTED** (self-review only)

Five of the swept values — a password, a recovery code, an API key, an email address, an account UUID — are
shapes no flow in this file produces, so their absence proves only that the sweep read the surface. Phase 10 says
so at the case, names which needles are live, and points at the two sentinels that make the search credible.

## I6 = S6 · The enumerative surface list — **ACCEPTED as documentation, no code change**

Recorded here, as both reviews ask: the structural claim about `indexedDB`, `document.cookie`, `cookieStore`,
Cache Storage, `navigator.storage` and `webkitRequestFileSystem` is **bounded by that list**. Six names cannot
prove the absence of a surface nobody thought of; `design.md` §5 governs additions, and a new client-side surface
is a design decision rather than a test change. This is open question **OQ4**, deferred to the author in Phase 4,
not a coverage gap. jsdom defines none of the six (measured), so the claim is structural by necessity, not by
choice.

## I7 · Record where the harness lives — **ACCEPTED, and it is already committed**

`.ai/prompts/frontend/T26/artifacts/07-mutation-harness.py`, committed **before** it was first run, because the
harness reverts with `git checkout -- src/auth/` and because a score from a scratchpad is reportable but not
reproducible — which is what T14–T24's are. Run from the repository root:

```
python3 -I .ai/prompts/frontend/T26/artifacts/07-mutation-harness.py          # all 22
python3 -I .ai/prompts/frontend/T26/artifacts/07-mutation-harness.py MA1      # one family
```

Phase 10's manifest and Phase 12's verification both carry this path and command.

## A note on Kimi's verification environment, for the record

The Phase 8 header says `vitest` cannot start locally because `vite-plugin-pwa` is absent, so its findings are
static. That is the third consecutive task with the same note, and it is why every one of its findings was
re-checked against source here: I5's proposed pattern failing its own examples is exactly the kind of error a
static review cannot catch itself. The findings were still right about the *problem* in all seven cases.

---

## What Phase 10 will do

1. **The namespace instrument** (I1, I5) — derive `themistra` from the inventory constants, scan production
   source for every namespace literal, require each to be an inventory key or an allowlisted non-storage name
   with a reason, with synthetic controls for `themistra.auth.evil`, `themistra.auth_state` and
   `themistra.authx.foo`, plus MA22's dormant computed write as the measured proof.
2. **Narrow the `localStorage` structural claim** (I2) and keep the behavioural one.
3. **`arrival.unmount()` between arrivals** (I3) in the three repetition cases.
4. **Exact key sets in the six discard rows** (I4).
5. **Name the forward-looking needles at the case** (S4).
6. **Re-run the harness** after the changes, including MA22, and report whether the third instrument moves it
   from `INHERITED-ONLY` to `own` — which is the whole point of adding it.

No production change. The twelve acceptance criteria stand as frozen; AC3's strength rises once the namespace
instrument lands, and Phase 12 will say so in those terms.
