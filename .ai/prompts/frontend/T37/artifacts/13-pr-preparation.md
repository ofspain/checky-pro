<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation) -->

# frontend · T37 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/12-specification-verification.md` |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Status: **CLOSED PASS**

| | |
|---|---|
| **Cases** | 29 in `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`, 13 frozen criteria + 1 phase-10 addition, 6 controls |
| **Mutants** | **35/35 killed**, 0 survivors, 0 broken, 0 canaries |
| **Suite** | 1691 passed / 1 failed of 1692 — the known `oxlint` host case |
| **`tsc --noEmit`** | exit 0 |
| **`oxlint src`** | exit 0 |
| **Kimi findings** | 14 (phase 3) + 14 (phase 8) + 13 (phase 11) = **41 across three gates** |
| **Deviations** | 7 |
| **Production** | 6 files, +723 / −14 |
| **Artifacts** | 13 files, +2116 |

## What shipped

| File | |
|---|---|
| `frontend/src/auth/SignedOutScreen.tsx` | **NEW**, 52 lines, **8 of markup**. A landmark, a heading, a sentence, one `Link`. The emptiness is the deliverable. |
| `frontend/src/i18n/catalogue.ts` | +18 — `signedOut.heading / explanation / action`, derived from L4 and justified clause by clause |
| `frontend/src/app/routes.tsx` | +8 — publishes `SIGNED_OUT_PATH`, attaches the element, replaces the literal in `PUBLIC_ROUTE_PATHS` |
| `frontend/src/auth/endSession.ts` | +13/−13 — imports the published constant, deletes its private copy. **Retires T36-D2.** |
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | +4 — task 16's attached-screen list, additively |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **NEW**, +642, 29 cases |

## The findings worth remembering

Three of the four most useful findings in this task were about **instruments, not behaviour** — and two of
them invalidated something I had already counted as proof.

1. **A scoped query cannot measure an unscoped claim.** Every assertion in the suite reached through
   `main!`, so **SL23** — a second way out rendered one line *outside* `<main>` — survived all 1688 tests,
   on a screen whose one behavioural criterion is "one way back, and only one". The gap was in *where* the
   cases looked, not in what they claimed.

2. **A pattern can be aimed at nothing.** Phase 8 found that rendered-output comparison cannot see a read
   that branches to the same markup, so a token sweep was added. Phase 11 found the sweep's entry was
   `/\baccessToken\s*\(/` — and `accessToken` is an *object*, so it forbade a call shape that cannot
   legally exist and allowed all three that can. **It also invalidated my own SL25**, which had planted
   that impossible shape: 15 failures, of which fourteen were a runtime crash. Rewritten with
   `.current()`, it fails **2** — the scan and its control. A legal read breaks nothing, which is exactly
   why it needs a scan and exactly why the old count meant nothing.

3. **A side effect is not a contract.** SL19, SL20 and SL21 each died on a case about something else —
   routing, the catalogue, identity. Every kill would have evaporated the moment that case was refactored,
   and no message would have said the screen's *shape* changed. Closed as one named case asserting
   structure **as tags**, which is the half a string equality cannot give, plus phase 11's semantics:
   `<main role="region">` and `<h1 aria-level="2">` change no tag name.

4. **Two code comments were false, and measurement said so.** `routes.tsx:5` claims a module-scope read in
   a route cycle makes the application fail to start. Planted, it throws **nothing** — it renders
   `href="/app/undefined"`. The same read in `authorizeRequest.ts`, the cycle that comment is actually
   about, also throws nothing and fails exactly one case. The discipline is right; the reason given for it
   was not. What catches it is a test.

**Four separate times a kill count and the named failures disagreed.** Every mutant in this harness is now
verified narrowly — applied alone, only T37's file run, every failing case named.

## Deviations carried forward

D1 the journey in the task's own sentence is unprovable · D2 the file name is task 40's claim · D3 L11's
styling half is unmet SPA-wide · D4 the copy is derived · D5 AC13 proves non-branching, not truthfulness ·
D6 two closed tasks' files edited · D7 `routes.tsx:5`'s false comment. Full text in
`12-specification-verification.md`.

## Open for the owner

| | |
|---|---|
| **OQ-T37-1** | The copy is derived, not specified. Three instruments prove it cannot change silently; none can say the words are right. |
| **OQ-T36-5** | **Nothing calls `endSession()`.** Eighth consecutive dormant task (5, 6, 30, 31, 33, 35, 36, 37). This is what makes D1 unprovable, and no test in any of those tasks can close it. |
| **FU-T37-2** | `routes.tsx:5`'s false consequence — task 16's closed file, a comment not an instrument. |
| **OQ-T32-4** | Sixth copy of T35's mutation harness; **eleven** reviews across five tasks have now named it. Two stale `T35` labels were fixed in this copy only. |
| **Task 38's "Blocked on Q15"** | Looks mis-assigned: `package.md:475` lists Q15 as resolved and `:421-430` shows it is about revocation, not sign-out. **Task 38 is the only end-to-end proof of R19**, so the block is load-bearing. |
| **Spec drift** | `package.md:123`'s pre-revision test name; `design.md:138`'s "PROPOSED … pending Q6" against §11's resolved list. |

**FU-T37-1 is CLOSED** by phase 10 and phase 11. **FU-T36-3** (the broken-counts-as-killed arithmetic in
four earlier harness copies) is unchanged — this copy has the fix.

## Commits

20, each pushed. Phase artifacts `00`–`13`; `07-mutation-harness.py` alongside.
