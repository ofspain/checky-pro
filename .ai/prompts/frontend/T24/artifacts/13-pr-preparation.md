# frontend · T24 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. **T24 is complete: 14 of 14 phases.**

---

## Commit title

```
frontend T24: send a completed sign-in to an allowlisted target, or home
```

## Commit message

```
frontend T24: send a completed sign-in to an allowlisted target, or home

Add auth/returnTarget.ts - L16's fourth item, the post-login return
target - and one navigation at the end of CallbackScreen's successful
path. Thirty lines of production code for what L1 and agents.md:21 call
an allowlisted return target "falling back to the home route, never to
an arbitrary URL".

That last clause is why this is a security control and not a
convenience. An open redirect at the end of an OIDC flow is how a
phishing chain ends, because the victim really did sign in, so whatever
page they land on inherits the trust of a completed login.

The check is SET MEMBERSHIP over router-relative paths derived from
AUTHENTICATED_ROUTE_PATHS - no prefix test, no pattern match, no
normalisation pass. /app/home is not a member and falls back; so do
/app/homeward, //evil.example, a javascript: target, a backslash path,
/home/../admin and /home?x=1. Every one of those is wrong in a different
way, and membership refuses them all for the same reason, which means
there is nothing to strip and nothing to decode.

takeReturnTarget() is a TOTAL function. CallbackScreen calls it inside
.then(onFulfilled, discard), and a synchronous throw from onFulfilled is
not caught by the second argument - T21 measured that. An uncaught throw
here would blank the page at the end of a successful sign-in, after the
visitor has already authenticated: the worst moment this application has
to offer. So every failure yields the home path.

Three decisions that are not obvious from the code:

- The target is consumed even when the visitor has LEFT the callback
  screen. A guard written to mean "do not move someone who has left"
  had quietly acquired a second meaning, "do not consume for someone who
  has left", and the cost was invisible: the next sign-in in that tab
  would be sent somewhere from a journey they abandoned.

- A DISCARD leaves the target alone. "Single-use" and "survives a failed
  attempt" are only in tension until the use is named: the use is the
  arrival, not the attempt, so a visitor whose callback was refused
  retries and still gets where they were going.

- The route table is read INSIDE a function, never at module scope. The
  cycle routes -> CallbackScreen -> returnTarget -> routes means a
  module-scope read lands in a temporal dead zone, and T16 measured Vite
  yielding undefined rather than throwing. An undefined allowlist fails
  closed SILENTLY: every target refused, the SPA apparently fine. A scan
  keeps it out, counting brace depth rather than indentation.

The allowlist excludes parameterised routes, so invoices/:id and
receipts/:id are not honoured - the two routes notification emails
deep-link to. The exclusion matches react-router's own grammar, a colon
at the START of a segment, so a literal time/12:30 would stay legal.

Seventeen inherited assertions across five test files said a successful
callback ends on /app/callback. Each is updated with its reason
restated; T21's unmount case passes unchanged, which is how the
leave-no-one-behind decision is verified.

Named test returnTargetOutsideAllowlistFallsBackToHome: 67 cases, plus
15 it.todo rows that are task 25's rejection families - the spec splits
"on the allowlist is honoured" (task 24) from "off the allowlist falls
back" (task 25), and task 24 pre-empts none of them. Twenty-five
mutants over the allowlist and the fallback, all detected.

Declared: /app/home has no element and no owning task, so the fallback
lands on a blank route; nothing calls rememberReturnTarget in production
yet, because R28's redirect is task 41; and the storage carve-out is now
two constants in two modules, which task 26's guard must both enumerate.

Verifies returnTargetOutsideAllowlistFallsBackToHome (package.md §8).
Cites R7, L1.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

**Created (2)**

| File | Lines | What |
|---|---|---|
| `frontend/src/auth/returnTarget.ts` | 139 (≈30 code) | `RETURN_TARGET_KEY`, `rememberReturnTarget`, `takeReturnTarget`, `isAllowlistedTarget`, and the derived allowlist |
| `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | 1072 | the named test, T24's half: 67 cases + 15 `it.todo` rows for T25 |

**Modified (7)**

| File | Change |
|---|---|
| `frontend/src/auth/CallbackScreen.tsx` | the navigation at the end of the successful path, the `onScreen` ref and its cleanup (+34) |
| `frontend/src/app/routes.tsx` | `HOME_PATH` exported and used in `AUTHENTICATED_ROUTE_PATHS` (+5/-2) |
| `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` | `CALLBACK_ROUTE` → `POST_LOGIN_ROUTE`, three assertions, and the key-set pin's message now records T24-D4's answer |
| `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | the `returnTarget.ts` allowlist row, "four files" → five, the AC5 URL control, and `ROUTE_HAS_NO_ELEMENT` |
| `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | two assertions; **the unmount case unchanged** |
| `frontend/src/auth/callbackValidatesNonce.test.tsx` | two cases, one renamed |
| `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | two cases, one renamed |

`spec/` untouched. `package.json`, `src/i18n/`, `codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts`,
`authorizeRequest.ts` and `.oxlintrc.json` byte-identical to their pre-T24 state.

## Summary

L1 and `agents.md:21` are one sentence between them: a completed sign-in goes to an **allowlisted** post-login
return target, falling back to the home route, never to an arbitrary URL. T24 builds the whole of that — the
storage key, the capture, the derived allowlist, the validation, and the navigation — so that task 41's redirect
is a one-line call rather than a design, and task 25's rejection table is rows in a harness that already exists.

The design is one idea: **the allowlist is the rule.** It is a set of router-relative paths derived from the route
table, and the decision is membership. Nothing is normalised, so nothing can be normalised wrongly; the near-miss
`/app/home` and the hostile `//evil.example` are refused by the same line, and a path added to the route table
becomes legal with no second edit. Validation happens on the read, which is the trust boundary, and also on the
write, which is not the boundary but is a place a value that could never be honoured should not be allowed to sit.

## Testing performed

| | |
|---|---|
| `tsc --noEmit` | exit **0** |
| Full suite (`vitest run`) | **1075 passed, 15 todo, 1 failed** by exit code, over two consecutive runs |
| The one failure | `lintRejectsHandWrittenBackendFetch.test.ts:118` — the known environmental case; this host's `oxlint@1.87.0` prints no summary line (T18). Not caused by T24, and the suite was green on this host before it |
| Named test alone | 67 passed, 15 todo |
| Flake check | three full runs at Phase 10, two at Phase 12 — identical counts |
| Mutation harness | **25 mutants, 25 detected**: 24 KILLED by assertion, M13 (module-scope allowlist) detected by collapse — it stops the file from collecting at all, which the harness scores BROKEN rather than KILLED |

Two mutants survived their first run, and **both were faults in new tests rather than in production** — Gap 5's
rows padded the home path with whitespace, which trims to the fallback and so cannot fail, and Gap 2's stub
refused every write, which made its claim unfalsifiable. Both repaired; both mutants now die. That is the second
time in this task that writing the mutant before believing the case caught a test that could not fail.

**The house risk was a test that passes against no navigation at all**, so every destination case asserts the
destination *and* that the other destination was reachable from the same harness. The honoured case watches the
router sit on `/app/callback` with the exchange in flight and only then releases the response.

## Specification references

**Task 24** (`spec/frontend/tasks.md:36`) · **R7** (`requirements.md:23`) · **L1** (`design.md:8`) ·
`agents.md:21` · and, necessarily touched: **L3** (`design.md:13`), **L16** (`design.md:58`, whose clause (a) this
task completes), **L4**, **R8** · named test `returnTargetOutsideAllowlistFallsBackToHome` (`package.md` §8).

**Frozen decisions:** T24-D1 … T24-D11 (`artifacts/04-frozen-task-brief.md` §3), plus **T24-D14** (a discard
leaves the target) and **T24-D15** (the write filters too), both taken at the Phase 9 gate.

## Carried forward, not this task's

- `/app/home` has no element and **no owning task** — the fallback lands on a blank route (T24-D11). Fifth unowned
  piece, beside the composition root, silent re-authentication, and the real-browser check.
- `rememberReturnTarget` has no production caller until **task 41**, so every sign-in in the running SPA takes the
  fallback today.
- The storage carve-out is now **two** constants in two modules, deliberately unmerged (T24-D4); **task 26**'s
  guard must enumerate both, or it will report the return target as a violation of the carve-out that names it.
- Notification-email deep links to `invoices/:id` and `receipts/:id` are not honoured (T24-D5).
- `oidcRoundTripEndToEnd` has no Playwright suite and no owning task — the largest verification gap in the package.
- CI must pin the suite total **and** the identity of the `oxlint` failure (T18 Gap 6), or a real lint regression
  is indistinguishable from the environmental one on this host.
- Unchanged package-level items: `contracts/api/token-claims.md` false on `email`/`name`; no ID-token contract
  document; `design.md` §4c's stale heading; `package.md:172`'s mapping; `expires_in` dropped (T21-D2).

**One process note, unchanged from T23:** the Phase 13 prompt template still asks for a
`Claude Opus 4.8 (1M context)` trailer. The commit message above uses the current one, as the session's
attribution instruction requires. The template is generated, so the fix belongs in `.ai/generate.py`.

---

**T24 COMPLETE — 14 of 14 phases.** Next task: **T25 — return target outside allowlist**, which inherits this
file's harness and the 15 `it.todo` rows.
