<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review) -->

# frontend · T36 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/06-implementation.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

## Mutation score

```
baseline whole suite: 1 failed of 1655, exit 1
baseline own cases:   0 failed of 76, exit 0

regressions: 26/26 killed
survivors:   none
broken:      none
canaries:    none — see below
```

26 mutants, **ES01–ES26**, every one labelled `own` (killed by a case in this task's own suite, not only by
inherited failures elsewhere). Reproducible:

```
python3 -I .ai/prompts/frontend/T36/artifacts/07-mutation-harness.py
python3 -I .ai/prompts/frontend/T36/artifacts/07-mutation-harness.py ES14
```

**No canaries, and that is a change from T35.** T35 had one, `SO15`, because its AC9 pins a hazard by
asserting the behaviour as it is — so the mutant that kills it is one that *fixes* the hazard, and the row had
to be scored apart or the number would teach a reader to discount it. T36's AC13 also pins a current behaviour
(`endSession()` is not idempotent), but the change a canary would record there is a **latch**, which AC15
forbids. A row asserting a forbidden fix is not a canary; it is noise. AC12 and AC13 are ordinary regressions
here.

## S1 — the harness caught my own bad mutant, and that is worth more than the score

**ES16 was a dirty kill on the first run.** The verdict line read:

```
KILLED  INHERITED-ONLY  ES16 the access token is sent as the hint instead of the ID token  (2 failed of 1579, exit 1)
```

Two numbers give it away. `INHERITED-ONLY` means nothing in **this task's** suite failed. `1579` is **75 short
of 1654** — exactly the size of my own test file, which had failed to collect. The mutant appended an
`import` statement *after* a statement, which is invalid TypeScript, so the file never loaded and the "kill"
was two unrelated suites reacting to a broken module graph.

A mutant that cannot compile measures nothing about the suite it is aimed at. Re-anchored so the import goes
where an import goes, and re-run: `own`, **8 failed of 1654**.

This is the second task running in which the machinery's own labels caught a defect in a mutant rather than in
the code — T35's preflight refused `SO21` for matching twice. Both are arguments for keeping the labels and
the counts rather than printing a bare score.

## S2 — the coarse kills, verified narrowly

Four mutants failed 17–20 cases. A coarse kill can hide the fact that the mutant was never isolated by the
case that describes it, so each was re-run against this suite alone and the named failures recorded:

| Mutant | Failures | Killed by, by name |
|---|---|---|
| **ES04** no hand-over at all | 20 | AC2 `performs one navigation and no more` |
| **ES26** router navigation instead | 20 | AC7 `imports no router and reaches for no history` — **its own description**, confirmed by `-t`, not inferred from the count |
| **ES05** a second hand-over through `window.open` | 17 | AC2 `performs one navigation and no more` |
| **ES25** denylist entry dropped | 17 | AC16 `is on the service-worker navigation denylist` **and** AC10 `exactly two production modules` |

ES04 and ES26 produce **identical** top-eight failure lists, which is exactly the situation where a count is
misleading: both look like "the navigation stopped working." Running AC7's case alone is what separated them,
and confirmed ES26 is measured by the scan written for it rather than merely by the absence of a navigation.

**ES05 is the mutant Kimi's F5 would have made unkillable.** F5 proposed narrowing AC2 to the two mechanisms
the module uses. ES05 adds a `window.open` *beside* the existing `assign` — an `assign`-only watcher counts
one call and passes. The 17 failures are the four-mechanism counter doing the one job F5 would have removed.

**ES25's double kill is a both-directions property I did not plan.** Dropping the denylist entry fails AC16
(the spelling is gone from the list) *and* AC10 (only one production module now names the path, where the scan
requires exactly two). The scan that exists to catch a *third* module also catches the loss of the second.

## S3 — the `SIGN_OUT_SET` finding, fixed with a control because no mutant can reach it

Phase 6 recorded that T35's monopoly case pins a **hardcoded four**, so `signOut.ts` growing to five
primitives left it passing while its own description — "the whole sign-out set" — had become false. A second
module permitted to call all five would not have been caught.

Fixed: `SIGN_OUT_SET` is now **derived from `signOut.ts`'s source**, with a floor assertion (`toBe(5)`) so the
derivation cannot silently find nothing and pass vacuously.

**This gap could not be measured by a mutant**, and saying so precisely matters: the defect was in *this test
file's logic*, not in production source, and the harness only mutates production source. So it gets the other
instrument — a control that plants a module into every primitive's allowlist one entry at a time and shows the
strengthened case sees it, plus an assertion that the derived set actually contains `takeIdToken`, which is
the claim the hardcoded version could not make.

The fix has measurable teeth: **ES19** (`signOut no longer clears the ID token`) went from **3 failures to
6** between the two harness runs, because the derived-set case and the "calls all five" case now fail on it as
well.

## S4 — what the suite gets right

- **AC9's first case is the one that would otherwise have been missed.** `signOut()` called *alone* must clear
  the ID token. A suite that only ever invoked `endSession()` would pass with `signOut()` ignoring the token
  entirely, because `endSession()` consumes it for the hint on the way past. **ES19** is that mutant and it
  dies.
- **ES14 is the `?? ''` mutant**, and it is the subtlest regression in the set. `URLSearchParams` serialises
  the empty string without complaint, so `id_token_hint=` is *present and empty* — asking SAS to end the
  session identified by `''`. Every case that only reads `get('id_token_hint')` against a held token still
  passes. AC12 asserts `has(...)` is `false`, which is what kills it.
- **ES09 is the coincidence mutant**, inherited knowingly from task 22: while `APP_BASENAME` is `/app`,
  hardcoding `'/app'` produces a byte-identical URL and no assertion against the real route table can tell the
  two apart. The moved-basename case is the only thing that kills it, and it does.
- **ES21 closes the gap phase 2 found in T35's own AC7** — declaration counts pinned only for *excused* files,
  so a second mutable in a *cleared* file would pass. T36 is the first task to add one, and the mutant that
  plants a second `let` in `idToken.ts` dies.
- **ES16 is the worst thing this module could do** — the access token in a URL, which means a browser history
  entry, a referrer and a server log. It dies in 8 cases.

## S5 — the weakest things, stated plainly

- **Nothing calls `endSession()`.** Every one of the 37 new cases is a statement about behaviour *when
  invoked*. The harness cannot change this and neither can a scan: there is no production caller to mutate.
  **OQ-T36-5**, seventh consecutive task.
- **AC4's "derived from the route table" is really "checked against" it** (D1). `routes.tsx` has no
  `SIGNED_OUT_PATH` export, so the spelling is declared privately in `endSession.ts` and held equal by a test.
  **ES11** proves the instrument works, but a genuine derivation would need an edit to a closed module.
- **AC10's call scan sees only direct calls.** An alias, a wrapper or a reference passed as a callback would
  pass — T35 recorded this as Kimi's phase 8 I6 and accepted it, and T36 inherits the same limit without
  improving on it. The threat model is a second sign-out assembled from direct calls.
- **The de-slash cases mock the route table and dynamically re-import**, so they exercise a *different module
  instance* than the statically imported one. They assert only on the derived URL for that reason, which is
  said in a comment — but it means those two cases cannot also check the clearing, and a reader could
  misread them as full end-to-end cases.
- **`watchHandover` is the fifth duplicated helper** (`strip` is the fourth). **OQ-T32-4**, now nine reviews
  across four tasks. Nothing in T36's scope permits the extraction, because the other four copies live in
  closed suites.
- **AC13 pins non-idempotence rather than fixing it.** Defensible (a latch is the module state AC15 forbids,
  traded for a navigation that cannot happen after the page has left), but it is a behaviour frozen by a test
  rather than a behaviour anyone chose on its merits.

## Gates

| | |
|---|---|
| own suite | **76 passed (76)** — 38 task 35's, 37 task 36's, 1 the strengthened monopoly control |
| whole suite | **1654 passed / 1 failed of 1655** — the known `oxlint` host case only |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **26/26 regressions killed · 0 survivors · 0 broken · 0 canaries** |

Baseline for T35 was 1616/1617, so T36 adds 38 passing cases and no new failures.

## Open questions carried to phase 8

- **The `SIGN_OUT_SET` strengthening edits a closed task's test logic.** It is marked `TASK 36` with its
  reason, per Kimi's phase 3 F9, and the original T35 cases are unchanged — but it is still a change to T35's
  instrument made by a later task, and phase 8 should say whether that was the right call or whether the
  finding should have been recorded and left.
- **OQ-T35-3 is now producible**, which it was not before T36. A signed-out tab that adopts a sibling's
  renewal holds an access token and **no ID token** — precisely AC12's shape, which phase 2 recorded as
  *unreachable in production today*. That sentence is now false, and it is false because of T36. The hazard is
  still task 39's, but AC12 has stopped being hypothetical and the artifact should not keep implying it is.
- **OQ-T36-4** — whether SAS 1.5.1 honours `post_logout_redirect_uri` with no `id_token_hint`. Unmeasured, and
  AC12 navigates anyway. A backend measurement would either justify AC12's behaviour or retire `idToken.ts`
  entirely.
- **D3's blunt `open(` ban** in `endSession.ts` would forbid an unrelated legitimate `open`. No such use
  exists in 120 lines; phase 8 may judge the precision worth buying.

**Phase 7 complete.** Next is **phase 8 — Kimi 2.7's independent review.**
