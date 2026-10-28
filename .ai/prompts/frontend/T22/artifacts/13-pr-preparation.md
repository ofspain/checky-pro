# frontend · T22 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. **T22 is complete: 14 of 14 phases.**

---

## Commit title

```
frontend T22: prove no token reaches any storage, URL or log
```

## Commit message

```
frontend T22: prove no token reaches any storage, URL or log

Add tokensAreNeverWrittenToAnyStorage.test.tsx: 47 cases that drive the
real sign-in -> callback -> exchange flow and sweep every surface the task
statement names, plus the two the design challenge added - history.state
and window.name.

No production code changed, and that is the finding rather than a
shortfall. The measurement behind it: this service has exactly ONE
storage writer in 23 production modules, authorizeRequest.ts, which
writes exactly the three items L16 names, and there is no console call,
no localStorage, no IndexedDB, no cookie write, no sendBeacon and no
absolute address anywhere in it.

A leak is invisible by nature - a token in localStorage changes no
behaviour until something reads it - so the whole value of a sweep is
its ability to report dirty. Eleven cases plant a leak and require the
same instrument to catch it: a token under an innocuous key, a write
through globalThis['localStorage'], a Bearer-prefixed value, a decoded
claim, sentinels in the three surfaces that are otherwise empty boxes, a
node outside the mounted container, a log through a console alias, a
store that throws, and a scan handed nothing.

Five traps were measured rather than imagined, and the case count is
what they cost:

- The setItem spy must predate the authorize step. Installed after, it
  reported ZERO writes for a flow that had just made three.
- createMemoryRouter writes neither window.location nor history, so
  sweeping location.href reads an untouched document - not even the
  authorization code is in it. The URL claim is proven against
  router.state.location, with a control asserting it carries the
  callback query.
- Searching for a token built from a file constant while the flow
  carried one built from the live nonce proves nothing. Every searched
  value now comes from the response body the stub returned.
- Nine failure rows that each failed twice measured neither failure. The
  table now sends a valid ID token except on the bad-nonce row.
- A decoded claim value is part of the token and no segment search can
  see it: a surface holding `the-subject` contains no part of the
  base64url text that encodes it.

Two instruments, and they fail differently. The spies on
Storage.prototype and the six console channels are alias-proof, because
a write through a bracket alias still calls the same method; the
comment-stripped source scan is text, so it is the second net over code
no test executes. The file says which is primary and proves both.

What it cannot prove is written in the header, not implied: it does not
build the bundle, so a leak inside a dependency's minified code at
runtime is covered by neither instrument; jsdom has no indexedDB, no
caches and no cookieStore, so those surfaces are structural only; and a
sentinel proves the sweep reads a surface, not that a leak which does
not exist would have reached it.

Named file 47/47 exit 0, suite 978/979 with only the known oxlint
environmental failure, tsc clean, 25 mutants and 25 killed - every
mutant a real leak.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Lines | What |
|---|---|---|
| `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | **new**, 956 | The named test (`package.md:157` → R7): the sweep, the scan, and eleven self-proofs. |
| `.ai/prompts/frontend/T22/artifacts/` | 14 | Pipeline artifacts. |

**No production file.** No contract, no dependency, no catalogue key, no `.oxlintrc.json` change.

## Summary

T22's job is to prove a negative about a whole package, and the honest difficulty is that a sweep which cannot
report *dirty* is indistinguishable from one that reports *clean*. Phase 6 ran the sweep as throwaway probes
first, precisely so the instrument's own failures would surface before anything was committed — and they did,
four of them, two found by the probe and two by re-reading the probe's source.

The result is a file where roughly a quarter of the cases exist to validate the other three quarters. That ratio
is the deliverable. A leak in this package is invisible; an instrument nobody can falsify is worse than no
instrument, because it reads like evidence.

The structural claim worth taking from this task, stronger than "no findings": **one** of 23 production modules
writes to any store at all, it writes exactly the three items L16 sanctions, and the allowlist table now makes
the next addition announce itself.

## Testing performed

```
$ npx -y pnpm@12.14.0 -C frontend exec vitest run src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx
  Tests  47 passed (47)                exit 0

$ npx -y pnpm@12.14.0 -C frontend exec vitest run
  Tests  1 failed | 978 passed (979)   exit 1
  ↳ src/api/lintRejectsHandWrittenBackendFetch.test.ts:118 (AC4) — this host's oxlint@1.87.0 prints no
    summary line. Environmental, unchanged since T18, the only failure in the run.

$ npx -y pnpm@12.14.0 -C frontend run typecheck
  tsc --noEmit                         exit 0

Mutation: 25 leaks planted across accessToken.ts, CallbackScreen.tsx, codeExchange.ts, idTokenNonce.ts and
authorizeRequest.ts, run against the named file (47 cases, baseline 0 failed). 25 killed, 0 survived.
```

Two mutants earned their place by finding holes rather than confirming coverage:

| Mutant | What happened |
|---|---|
| **M12** the screen carries the token in `navigate(…, { state })` | **Survived** the file as first written. In a real browser that *is* `history.state`; under a memory router it lives in router state, which the sweep did not read. `routerNavigateState` exists because of this mutant. |
| **M25** a **decoded claim** written to `document.title` | Caught by nothing but the claim search Phase 11 G1 added — no whole-store equality can see the title. This is the proof that G1 was an incompleteness and not a style note. |

And two that show why there are two instruments at all: **M3** (a write through `globalThis['localStorage']`) and
**M5** (a log through `globalThis['console']`) die only to the runtime spies; no text scan sees either.

## Specification references

**Task 22** (`spec/frontend/tasks.md:34`). **Requirements:** R7 (the scoped one), with R29's prohibition half and
R57's Cache Storage surface referenced rather than re-verified. **LOCKED decisions:** L3 (the scoped one,
`design.md:13`) — all three clauses, storage, logs and analytics, the last resolved by `package.md` Q9 (none at
launch); L16 honoured by asserting its three items **present**; L17, L13, L4, L9 unaffected. **Standing rules:**
`agents.md:79-85`, which is where "never placed in a URL" comes from — `requirements.md` does not say it.
**Named tests:** `tokensAreNeverWrittenToAnyStorage` (this task's, new);
`callbackValidatesNonceAndKeepsAccessTokenInMemory` and `callbackValidatesNonce` (T19/T21, built, green);
`returnTargetOutsideAllowlistFallsBackToHome` (tasks 24-25). **Decisions frozen here:** T22-D1..D15, in
`artifacts/04-frozen-task-brief.md` and `artifacts/09-review-resolution.md`.

Branch off `main`; `main` stays deployable. The change is a test file; nothing in the running app differs.

## Carried forward

**Needs an owner, and is not T22's to take:**
- **No task owns a real-browser check.** IndexedDB, the Cache Storage runtime, and the cookie surface beyond
  `document.cookie` cannot be proven under jsdom. T22 states the limit instead of implying coverage, but nothing
  in `tasks.md` 1-39 closes it.
- **Nothing wires the composition root**, and **silent re-authentication has no task** — both carried unchanged
  from T21.

**To task 23, which owns single-use deletion:** it is the expected next entrant in `STORAGE_ALLOWLIST`, and it
will also have to change the AC2 whole-store equality when it consumes the three items. Mutant M16 proves an
unannounced `removeItem` fails with a message naming the file and what to do.

**To task 24, which owns the post-login destination:** the callback URL still carries `code` and `state` when the
visitor leaves the callback screen. Removing the callback query (`code`, `state`, `error`, `error_description`)
before the next screen renders is that task's call (T22-D3, T22-D9).

**To task 26, which owns the L16 inventory:** T22 proves the three items are the only ones present after a
sign-in; the full inventory across both halves of L16, including R29's 15-minute TTL, is still task 26's.

**Recorded and not taken (T22-D12):** banning `localStorage`, `indexedDB` and `openDatabase` as `oxlint`
restricted globals would be an alias-proof *static* guard with no legitimate exception — `sessionStorage` cannot
join them, because `authorizeRequest.ts` writes it. The cost is T18's AC5, which pins that config by set equality
with L6's message, plus a decision that the lint config enforces L3 as well as L6.

**Open, gating nothing** (unchanged from T21 unless marked): `credentials: 'omit'` on the token request; CI
enforcement of the suite total and of the `oxlint` failure's identity (T18 Gap 6); `contracts/api/token-claims.md`
measured false on email/name; the ID token has no contract document; `design.md` §4c's stale "PROPOSED, pending
sign-off" heading; `package.md:172` mapping `serviceWorkerUpdateWaitsForReload` to R57; `expires_in` dropped at
T20's boundary (T21-D2); `retainAccessToken` validating nothing by design; and this template's stale
`Claude Opus 4.8 (1M context)` trailer, which the commit message above does not use.

---

## T22 — COMPLETE. 14 of 14 phases. Phase 12 verdict: **PASS**.
