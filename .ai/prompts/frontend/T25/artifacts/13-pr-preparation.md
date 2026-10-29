# frontend · T25 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. **T25 is complete: 14 of 14 phases.**

---

## Commit title

```
frontend T25: prove "never to an arbitrary URL", family by family
```

## Commit message

```
frontend T25: prove "never to an arbitrary URL", family by family

L1 and agents.md:21 are one sentence between them: a completed sign-in
goes to an allowlisted return target, falling back to the home route,
"never to an arbitrary URL". T24 built that mechanism. The spec splits
the verification in two - tasks.md:36 is "ON the allowlist is honoured",
:37 is "OFF the allowlist falls back" - and this is the second half.

No production code changed. T25-D5 made a fix conditional in writing: if
any shape had been found unrefused, the narrowest change was in scope.
The condition never triggered across 24 families, each checked against
the predicate, the producer, the real flow, and an unreachable store.

MEASURE THE HARM BEFORE CLAIMING THE DEFENCE. A rejection row otherwise
asserts that nothing happened, which is also what a broken test asserts.
So every family is first put to a bare router, and the answers split the
24 into three kinds:

  7 REFUSE - react-router throws "External navigation is not allowed".
    Every absolute URL, the protocol-relative host, javascript:, data:,
    and /\. Here the allowlist is defence in depth.

  8 MISS - the router lands on the catch-all. Encoded separators,
    prefix matches, nonexistent routes, relative paths. Harmless either
    way.

  9 REACH - THE ROUTER WOULD LAND ON A REAL ROUTE, and membership is the
    only thing stopping it:
      /home/../admin      resolves to /app/admin
      /sign-in            a real public route, so the harm is a LOOP:
                          a completed sign-in sent back to sign-in
      /invoices/123       matches invoices/:id. The route WORKS. The
                          allowlist is exact paths and cannot express
                          it - T24-D5's cost, measured rather than
                          argued
      /home/  /HOME       react-router ignores a trailing slash and
      /account/sessions/  matches CASE-INSENSITIVELY
      /home?x=1 /home#top the query and fragment ride along

That seven-of-nine is the finding of the task. A membership check that
folded case, stripped a trailing slash, decoded, or trimmed would not
merely admit a stray string - the router would honour the result, and a
visitor would end a successful login somewhere they were never sent.

Every row asserts six things, because a destination alone is weak: it
lands on /app/home; the sign-in COMPLETED (the token is held, so no row
can pass against a discard); the refused value is gone; it was gone by a
REMOVAL, never a clear(); the real exported predicate refuses it; and
the outcome is indistinguishable from no target at all over pathname,
search, rendered text and history action. That last comparison is per
row, not one sampled sweep - a sweep passes if the family it samples
happens to match.

Two mutants found what review did not. A clear() in place of the one
removal passed all 57 T25 cases and was caught only by two of T24's; it
now fails 20. And stripping a leading slash from both sides survived the
entire 25-mutant table, because nothing in 21 families had a target
WITHOUT a leading slash - the one shape missing was the most ordinary.
T18 had already measured why that matters: a relative target resolves
against the CURRENT route, so `home` from the callback becomes
/app/callback/home and lands on the catch-all silently. That silence is
why T24-D8 fixed the stored form as a path with a leading slash, and
until now nothing held the two halves of that decision together.

The mutation harness is committed for the first time in this package.
It has lived in a session scratchpad since T14, so twelve tasks' results
were reportable but not reproducible.

Verifies returnTargetOutsideAllowlistFallsBackToHome (package.md §8).
Cites R7, L1.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

**Created (1)**

| File | What |
|---|---|
| `.ai/prompts/frontend/T25/artifacts/10-mutation-harness.py` | the 25-mutant harness, path-independent, with the `git checkout` danger in its header and the reasoning behind the mutant list |

**Modified (1)**

| File | Change |
|---|---|
| `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | +712 / −35. The T25 section: 15 `it.todo` rows → 117 cases. Three new harness helpers (`visibleOutcome`, `routerOutcomeFor`, `threeFlows`), one of them lifted out of a T24 case without changing any of its assertions |

**No production file was touched.** `returnTarget.ts`, `CallbackScreen.tsx`, `routes.tsx`,
`authorizeRequest.ts`, the two pure readers, the holder, `.oxlintrc.json`, `src/i18n/` and `package.json` are
byte-identical to their pre-T25 state. `spec/` untouched.

## Summary

T25 proves the clause that makes the return target a security control rather than a convenience. An open redirect
at the end of an OIDC flow is how a phishing chain ends, because the victim really did sign in — so whatever page
they land on inherits the trust of a completed login.

The design being verified is one idea: **the allowlist is the rule.** Set membership over router-relative paths
derived from the route table, with nothing normalised — so nothing can be normalised wrongly, and the near-miss
`/HOME` is refused by the same line as the hostile `javascript:`. This task's contribution is to show that the
line holds for twenty-four distinct shapes, and — more usefully — to identify the nine where it is the *only*
thing holding.

## Testing performed

| | |
|---|---|
| `tsc --noEmit` | exit **0** |
| Full suite (`vitest run`) | **1192 passed, 1 failed** by exit code, over repeated runs |
| The one failure | `lintRejectsHandWrittenBackendFetch.test.ts:118` — the known environmental case; this host's `oxlint@1.87.0` prints no summary line (T18). Not caused by T25 |
| Named test alone | **184 passed** (67 T24 + 117 T25) |
| Mutation harness | **25 mutants, 25 killed, no survivors** — and **every one by T25's own file**, not by an inherited T24 or T23 case. Re-run from the committed script at the repository root |

The harness reports two columns on purpose: "the suite caught it" and "the section that owns this behaviour caught
it" are different claims, and only the second one is this task's. The three mutants that matter most are M1
(case-folding), M2 (trailing-slash strip) and M3 (trimming) — the three whose results react-router would honour —
and they fail 4, 6 and 9 cases respectively.

**Two surviving mutants drove real changes rather than being argued away**, and both exposed vacuity in tests I
had just written: the `clear()` escape and the missing no-leading-slash family. Both are recorded in the Phase 9
and Phase 10 artifacts with their measurements.

## Specification references

**Task 25** (`spec/frontend/tasks.md:37`) · **R7** (`requirements.md:23`) · **L1** (`design.md:8`) ·
`agents.md:21` · and, necessarily engaged: **L3** (`design.md:13`), **L4**, **L11**, **L16** (`design.md:58`),
**R8** · named test `returnTargetOutsideAllowlistFallsBackToHome` (`package.md` §8 → `package.md:158`).

**Frozen decisions:** T25-D1 … T25-D6 (`artifacts/04-frozen-task-brief.md` §3). Inherited and re-proven for the
refused path: T24-D5, T24-D6, T24-D8, T24-D9, T24-D10, T24-D14, T24-D15.

## Carried forward, not this task's

- **Seven of the nine `reach` families have no second line of defence.** The live hazard is a future "just
  normalise it" change; T25-D5 forbids one without a gate decision, and M1–M3 exist to make it fail loudly.
- `/app/home` has no element and **no owning task** — every row here asserts a navigation to a blank route
  (T24-D11).
- `rememberReturnTarget` has no production caller until **task 41**, so the fallback is the only path the product
  exercises today.
- The storage carve-out is **two** constants in two modules (T24-D4); **task 26**'s guard must enumerate both.
- Notification-email deep links to `invoices/:id` and `receipts/:id` are not honoured (T24-D5) — and the
  counterfactual now shows the router *does* match the route, so the cost is measured.
- `oidcRoundTripEndToEnd` has no Playwright suite and no owning task — the largest verification gap in the
  package, and the reason no family is proven against a real browser (T25-D6).
- CI must pin the suite total **and** the identity of the `oxlint` failure (T18 Gap 6).
- **The mutation harnesses for T14–T24 are not in version control.** T25's is. Making the earlier ones
  reproducible is a package-level follow-up, raised by Kimi Gap 1.
- Unchanged package items: `contracts/api/token-claims.md` false on `email`/`name`; no ID-token contract document;
  `design.md` §4c's stale heading; `package.md:172`'s mapping; `expires_in` dropped (T21-D2).

**One process note, now in its third task:** the Phase 13 prompt template still asks for a
`Claude Opus 4.8 (1M context)` trailer. The commit message above uses the current one. The template is generated,
so the fix belongs in `.ai/generate.py`.

---

**T25 COMPLETE — 14 of 14 phases.** Next task: **T26 — the sessionStorage carve-out guard**, which must enumerate
both `AUTHORIZE_STORAGE_KEYS` and `RETURN_TARGET_KEY` (T24-D4).
