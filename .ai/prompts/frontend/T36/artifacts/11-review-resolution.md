<!-- MODEL: Claude Opus 5 — Phase 11 (Review Resolution) -->

# frontend · T36 · Phase 11 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | `artifacts/11-review-resolution.md` |

Fourteen findings. **Seven accepted and implemented**, **three rejected**, **four recorded with no action**.

## Score

```
regressions: 33/33 killed      (30 → 33)
survivors:   none
broken:      none
exit:        0
```

| | |
|---|---|
| own suite | **84 passed (84)** — was 81 |
| whole suite | **1662 passed / 1 failed of 1663** — the known `oxlint` host case only |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

---

## F2 — ACCEPTED. The sharpest finding, and it exposes something about my own phase 10.

**Confirmed:** phase 10's encoding case asserts the encoded post-logout target and the absence of `=https://`.
Both are about one parameter. A module that encoded the target and concatenated the hint passes it.

The new case uses a **deliberately hostile token** — `T36-id-token-&evil=1#frag+plus` — and the choice needs
stating, because a reviewer could call it unrealistic. It is unrealistic, and that is the point: a real JWT is
base64url (`A-Za-z0-9-_.`) and needs no encoding at all, **which is exactly why a concatenation bug would hide
behind one indefinitely**. What is under test is the encoder, so the input is chosen to make the encoder
matter. Unencoded, the `&` starts a new parameter, the `=` reads as a separator and the `#` truncates the
query at the fragment — so the post-logout target disappears from what SAS receives, and the visitor's logout
fails for a reason nothing in the SPA would report.

Each character is asserted absent in raw form individually, so the failure names which one escaped, and the
value is round-tripped so the encoding is correct rather than merely present.

**ES31** is the mutant: it concatenates the hint alone, leaving the target encoded, so ES30's case still
passes. 13 failures, verified narrowly against the new case by name.

## F3 and F11 — ACCEPTED together, because they are one finding under two numbers

**They are a different family from the eight surfaces already swept**, and naming that is what makes the
remedy coherent rather than a longer list. The eight are places a token could be **left** — a store, a title,
`window.name`. These six are ways a token could be **sent**.

That distinction is what makes the gap specific: AC11 already forbids `fetch` and `XMLHttpRequest`, so a
reader could reasonably believe exfiltration was covered. It was not.
`new Image().src = '/log?' + hint` is a GET request that neither ban mentions, and **`navigator.sendBeacon`
is designed to survive exactly the page-unload this module causes** — making it the one request from this
module that would certainly complete.

Added to both new modules' sweep: `sendBeacon`, `new Image`, `new Audio`, `performance.mark`, `innerHTML`,
`document.write`. With a control that fires each pattern on a planted leak **and** confirms the module's real
hand-over line is not caught, so the scan is not a ban on working.

**ES32** is the mutant. 23 failures — verified narrowly: AC8's source sweep fails by name.

## F6 — ACCEPTED. The scan I added in phase 9 had the same shape of hole it was closing.

**Confirmed:** the needle was `/from '\.{1,2}(?:\/auth)?\/endSession'/` — keyed on the word `from`. So
`import('./endSession')` and `require('./endSession')` reach the module without matching.

Worth saying plainly: **phase 9's scan existed because the literal scan had a bypass, and it shipped with a
bypass of its own.** The needle now matches the module *specifier* in any of the three positions rather than
the import keyword, and the control covers all six spellings.

**ES33** is the mutant — a dynamic import in `AppShell.tsx`. It would have survived the phase 9 scan.

## F7, F9, F10 — ACCEPTED as written.

- **F7** — the hand-over URL carries no fragment. One line. A fragment never reaches the server, so anything
  in it is a value the visitor carries in their history for nothing; and had the hint ever been appended there
  instead of to the query, AC5 would fail while the token sat in the URL bar.
- **F9** — the strongest of the three, and correct about something real. The order scan read **the whole
  file**, which passes today only because `endSession.ts` is small — not a property to rely on. Now scoped to
  the function's own body, the way T35's AC4 case slices, with a floor assertion so a renamed declaration
  fails rather than silently widening the scan back to the file.
- **F10** — the argument was unpinned. `retainIdToken(response.accessToken)` would put a bearer token in the
  hint — ES16's regression arriving from the other end — and it would satisfy every structural case. Now
  pinned at exactly one occurrence, with the wrong-token form explicitly forbidden.

## F5 — REJECTED, on the rule phase 9 wrote rather than on a new argument.

Raised twice now (as I2, then F5), so it deserves the strongest version of the case for it, not a restatement.

Kimi's stated reason — "a regression would surface as a T36 failure rather than a callback-screen failure,
sending the wrong person to debug it" — is weak: AC14 fails with *"CallbackScreen.tsx does not retain the ID
token at all"*, which names the file. **The stronger version Kimi does not make** is that someone editing
`CallbackScreen.tsx` and running only T19's suite would see green while T36's contract was broken. That is a
real workflow hazard, and it is mitigated rather than eliminated: every gate in this pipeline runs the whole
suite.

It stays rejected because of the line **phase 9 drew for exactly this question**: editing a closed task's
tests is permitted when the instrument has become **false**, or must grow to **admit new state** — and
forbidden when it is merely **incomplete for something the new task owns**. T19's case says "holds the token
only AFTER the nonce check" about the *access* token. That is still true, still enforced, and not a claim the
ID token falsifies. T36 owns the ID token, so T36 owns its contract.

Deciding this by the rule rather than by fresh reasoning is the point of having written it down. Recorded as
**FU-T36-4** so the task that next opens T19's suite can add the mirror case where it costs nothing.

## F4 — REJECTED, second time, on the measured convention.

All 29 test files in this package are named after a task's named test in `tasks.md`; none after a module.
`accessToken.ts` — the same kind of module, holding the same kind of state — has no `accessToken.test.ts`.
The worry that a rename would fail nothing first is already false: AC5, AC9, AC14 and AC15 all name
`auth/idToken.ts` by path.

## F8 — REJECTED, though it is cheap, and the finding agrees it is not critical.

Parameter order cannot matter: SAS parses a query, not a sequence. A case pinning it would fail on a change
that is harmless by construction, and **a test that fails for something that cannot matter teaches a reader
to treat failures as noise** — which is a cost paid by every other case in the file.

## F1, F12, F13, F14 — recorded, no action.

**F1** is OQ-T36-5, seventh consecutive task: **AC1–AC16 are verified by direct invocation only**, and the
empty `PERMITTED_IMPORTERS` is the handoff. **F12** confirms ES27 matches its control. **F13** notes the suite
runtime at ~9s; not a correctness issue, and the source scans and dynamic imports are what buy the precision
above. **F14** confirms FU-T36-3 is correctly scoped to T36's harness copy.

## New mutants

| | | |
|---|---|---|
| **ES31** | the hint alone is concatenated, so a token with `&` or `#` breaks the query | 13 failures, verified narrowly |
| **ES32** | the ID token is exfiltrated with `sendBeacon`, which survives the unload | 23 failures, verified narrowly |
| **ES33** | a third module reaches `endSession` through a dynamic import | 3 failures — **would have survived phase 9's scan** |

## Follow-ups

- **FU-T36-4** — the mirror `retainIdToken` order case in T19's suite, for whoever next opens that file.
- **FU-T36-1** — T33's and T35's modules are not swept for the fourteen surfaces T36 now sweeps.
- **FU-T36-2** — the de-slash derivation duplicated with `authorizeRequest.ts`.
- **FU-T36-3** — broken-counts-as-killed arithmetic in the four earlier harness copies.
- **OQ-T36-5** · **OQ-T36-4** · **OQ-T35-3** — unchanged.

**Phase 11 complete.** Next is **phase 12 — specification verification**.
