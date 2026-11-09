<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review) -->

# frontend · T41 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Consumes** | `artifacts/06-implementation.md`, the diff |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |

## Verification performed

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1745 / 1746** — the one failure is the known host-environmental `lintRejectsHandWrittenBackendFetch` case |
| `python3 -I .ai/prompts/frontend/T41/artifacts/07-mutation-harness.py` | **14/19 regressions killed, every kill `KILLED/own`; 5 expected survivors for phase 10; 1 unfalsifiable scored apart; 0 broken; 0 disagreements** |

The harness is the **ninth** copy of T37's machinery (OQ-T32-4), carrying T40's expectation field. Twenty rows,
each predicting `own`, `phase 10` or `unfalsifiable` **before** the run. The full run is kept at
`artifacts/07-mutation-run.txt`.

## What the harness measured

**Every one of the fourteen kills is by a case in a file this task grew** — no `INHERITED-ONLY` row, which is
the failure mode T40's phase 8 I5 was about. The five survivors are not a gap in the eleven grown instruments;
they are the shape of the task at phase 7, and they divide cleanly:

| row | what survives | why nothing can kill it yet |
|---|---|---|
| **RD06** | **the basename left on the arrival — every deep link silently discarded** | `AC14`. Nothing in `src/` reads `RETURN_TARGET_KEY` after a redirect. **This is the mutant most likely to ship:** the redirect still works, the visitor still reaches sign-in, and the only symptom is landing on home afterwards |
| **RD07** | the strip off by one (`/account` → `account`) | the same, one character wide |
| **RD08** | the return target never remembered | the same |
| **RD02** | the authenticated clause dropped, so a signed-in visitor is redirected too (F7) | no case holds a session and arrives at an unmatched path |
| **RD05** | the destination as a hardcoded `"/sign-in"` literal | behaviourally identical; only a source scan can see it |

Three of the five are the **return target**, which is AC7 and AC14 — the half of this task that has no
observable effect on the page. Phase 10's named suite exists mostly for them, and the harness has now said so
in numbers rather than in intention.

**RD09 is unfalsifiable, and recording it is a correction to my own reasoning.** It adds the branch the
component's header argues against — skipping the write for the arrival that matched nothing. The allowlist
refuses `/` anyway, so the two are indistinguishable by any assertion. The case for "no branch, the allowlist
is the only filter" is therefore an argument about **where a rule lives**, not about behaviour, and a reader
who believed a test enforced it would be wrong. Phase 10 must not write a case that pretends otherwise.

**One hazard is deliberately not a row.** Spelling the destination with the basename makes react-router apply
it twice, `/app/app/sign-in` hits the catch-all, and the process dies of heap exhaustion (measured at phase 4).
A mutant that crashes the runner measures nothing and would take every row after it down with it, so the hazard
lives in `RedirectToSignIn.tsx`'s header instead. **This is a known hole in the harness**, stated rather than
hidden: the most expensive mistake available at this seam is the one row this harness cannot score.

## Findings

### F1 — The named test does not exist, so the task's own half is unmeasured
· **Severity:** **Blocker** (expected; phase 10 closes it)
· **Evidence:** `package.md` §8 names `unauthenticatedRoutesRedirectToSignIn`. No such file. The five survivors above.
· **Assessment:** Identical in shape to T40's phase-7 state, and deliberate: phase 6 grew the instruments that
already existed, and the arms that measure the redirect's *own* claims are phase 10's.
· **Recommendation:** Phase 10 writes the file. Priority order is the harness's survivor list, not the AC
order: the **stored return target** (RD06/RD07/RD08) first, then the authenticated-visitor arm (RD02), then the
source scan (RD05).

### F2 — Nine of T40's arms failed in phase 6 without being wrong, and the instrument is why
· **Severity:** **Major** (fixed in phase 6; recorded because the lesson is general)
· **Evidence:** `withProbes` identified the gate as `child.path === undefined`
(`publicRouteSetIsExactlyTheDeclaredSet.test.tsx:219`). RD15 reproduces it: fourteen failures, nine of them
arms that are correct.
· **Assessment:** The baseline assertion T40's phase-11 review praised (S2) is what made this visible in one
run — but a baseline that cannot tell two shapes apart converts one real failure into nine false ones, and the
four arms phase 5 named as *"the boundary of this task"* were among them. For a few minutes the evidence said
T41 had overreached when it had not.
· **Recommendation:** None for the code. For the next task that adds a route: an instrument that **derives** a
table must identify each route by the property that makes it that route, not by the property that happened to
be unique when it was written.

### F3 — `gatesFound`'s failure message is wrong in the direction that now matters
· **Severity:** **Minor**
· **Evidence:** `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:245` — `expect(gatesFound, 'the derived table
found no pathless gate to replace').toBe(1)`.
· **Assessment:** The message names the `0` case only. After T41 the realistic failure is `2` — a second
pathless layout route — and the message would then say the opposite of what happened. Phase 6 added a
correctly-worded assertion for the new `pathlessLeaves` counter beside it but left this one as it was.
· **Recommendation:** Phase 9: reword to name both directions, e.g. *"the derived table did not find exactly
one pathless gate with children"*.

### F4 — AC13 now pins traversal ORDER inside a case about counts
· **Severity:** **Minor/Major**
· **Evidence:** `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — `expect(carrying.map(…type))
.toEqual([RedirectToSignIn, RequireSession])`.
· **Assessment:** Correct today, and naming which pathless element is which is the whole difference between
growing this pin and loosening it. But a later task that moves the index route **after** the gate would fail a
case titled *"exactly one pathless route carrying exactly one element"* for a reason that has nothing to do
with elements — and `pathTree` is already the pin that owns order.
· **Recommendation:** Phase 9: keep the order (it is intent, and intent is what a reader needs) but say in the
comment that order here is deliberate and that `pathTree` is the instrument that owns it, so the next reader
does not treat the failure as an element problem.

### F5 — The grown AC2 arms would report a confusing failure if `sign-in` ever left the public set
· **Severity:** **Minor**
· **Evidence:** the three AC2 arms assert `probeIn(container) === SIGN_IN_PATH` with the message
`` `${path} served its own content without a session` ``.
· **Assessment:** The assertion is right; the message describes only one of two failures. If the redirect broke,
`probeIn` is `null` and the message is accurate. If `sign-in` stopped being a probed public path, `probeIn` is
also `null` and the message is misleading.
· **Recommendation:** Phase 9: name the destination in the message. One word, and it is the difference between
a failure that explains itself and one that sends the reader to the wrong file.

### F6 — The catch-all's grown case proves the 404 is gone, not that the right screen arrived
· **Severity:** **Minor**
· **Evidence:** `routesResolveUnderAppPrefixOnly.test.tsx` — `expect(shellText).not.toBe('')` and
`.not.toMatch(/404|Not Found/i)`.
· **Assessment:** Deliberately narrow: that file's claim is L11 (no router-supplied English), and the sign-in
screen's copy is pinned in its own suite. But "not empty and not a 404" is satisfied by any screen at all.
· **Recommendation:** Phase 10's named suite asserts the sign-in heading for all three arrivals — which is
also what AC9's indistinguishability claim needs, since byte-identical output is a claim about *what* rendered.

### F7 — The basename strip is now duplicated twice over
· **Severity:** **Minor**
· **Evidence:** `RedirectToSignIn.tsx:58-60` and `cleanReauth.ts:43`'s `inAppPath()`.
· **Assessment:** Second instance of the shape. The duplication is defensible and the header says why — `inAppPath`
reads `globalThis.location`, which a memory-router test cannot drive — but it is now a rule with two homes, and
RD06/RD07 are both mutations of this copy only.
· **Recommendation:** None for T41, which may not touch `cleanReauth.ts`. Log it: **a third instance is the
one that should be extracted**, and whoever writes it should put it in `auth/` where both can reach it.

### F8 — The return target is written during render
· **Severity:** **Minor**
· **Evidence:** `RedirectToSignIn.tsx:67`.
· **Assessment:** A `sessionStorage` write in a render function is a side effect, and React's rules ask render
to be pure. Measured under `StrictMode`: it writes twice with the same value, derived only from the arrival, so
the outcome is identical. `useLayoutEffect` would make it one write and move it **after** the navigation
decision, and the hook is a surface this module's own source scan otherwise forbids.
· **Recommendation:** Keep it, and keep the constraint it implies: **no test may assert a write count.** Phase 10
asserts the stored value. Kimi's F4 said the same; this confirms it against a measurement rather than a reading.

### F9 — The two callback seams changed, and §3 of the plan did not name them
· **Severity:** **Minor/Major** (process)
· **Evidence:** `callbackValidatesNonce.test.tsx:124` and `callbackWithMismatchedStateIsDiscarded.test.tsx`,
both now resolving `{ idToken, accessToken: 'a-token-the-exchange-returned' }`.
· **Assessment:** Six cases in tasks 5, 6 and 23 failed because their fixtures resolved no access token, so
`retainAccessToken(undefined)` ran, no session was held, and `/app/home` now bounces. Their discriminator is
*home means exchanged, sign-in means discarded* — one case asserts `.not.toBe('/app/sign-in')` outright — so
the redirect collapsed it. **Verified before accepting the fix:** neither file asserts `accessToken.current()`
anywhere, so no claim in either suite is weakened by the seam holding a token, and the seam is now more faithful
to production, not less (`CallbackScreen.tsx:170` retains exactly this field).
· **Recommendation:** Phase 8 should check this independently rather than take the paragraph above on trust; it
is the one place T41 touched a closed task's fixture rather than its assertions.

### F10 — `RedirectToSignIn` reads the session at render, not reactively
· **Severity:** **Minor** (inherited)
· **Evidence:** `accessToken.ts` publishes no subscribe API; OQ-T40-1.
· **Assessment:** Same limit as the gate, and it reaches one case further here: a token arriving with no
navigation would leave a redirect already decided. Nothing does that today.
· **Recommendation:** None. OQ-T40-1 covers it; this is a second citation for the same open question, not a new one.

## Open Questions

- **OQ-T41-1** — The harness cannot score the basename-spelled destination, because that mutant kills the
  process rather than failing a test. The hazard is documented in two places and measured in neither.
- **OQ-T41-2** — Nothing in `src/` reads the return target in production except `takeReturnTarget`'s caller in
  the callback screen, so AC14's end-to-end value (deep link → sign-in → back to the deep link) is provable only
  by composing two suites. Task 42 is the first task that could prove it with a real screen.
- Carried, unchanged: **OQ-T40-1** (the read is not reactive) · **FU-T37-2** (`routes.tsx:5`'s TDZ claim is
  false as written) · **OQ-T33-2** (the authenticated data path is dormant).

**Phase 7 complete.** Twenty mutants, 14/19 regressions killed with every kill in this task's own files, five
survivors that name phase 10's priority order, one unfalsifiable row that corrects an argument I had made,
no disagreements, and ten findings — none of them fixed, per the phase's rule.
