<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T40 · Phase 10 — Test Generation

| | |
|---|---|
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `artifacts/10-mutation-run.txt` |

## Result

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| named suite | **47 / 47** — 29 of task 37's, **18 new** |
| `vitest run` | **1742 / 1743** — the one known host-environmental failure |
| harness | **17 mutants · 16/16 regressions killed · 1 unfalsifiable scored apart · 0 broken · 0 survivors · 0 disagreements** |

Every kill is `KILLED/own`: each regression dies with a **named failure in a file this task owns or grew**.
That is a stronger claim than the harness made at phase 7, and the expectation field was rewritten to carry
it — see §4.

## 1 · The instrument

`publicRouteSetIsExactlyTheDeclaredSet.test.tsx` gains one derived table and five helpers.

**`withProbes(gate)`** copies `appRoutes`, attaches a `<Probe path>` to every declared path **and to the
catch-all**, and puts `gate` on the pathless route. The gate is the **only** value that varies between arms.
It does three things beyond copying:

- **It asserts its own baseline** — exactly one pathless route found, and
  `PUBLIC_ROUTE_PATHS.length + AUTHENTICATED_ROUTE_PATHS.length + 1` probes attached, **derived, never
  written down** (AC5). A derived table that found no gate, or probed nothing, would hand every arm a green
  result that measured nothing. That is how a harness lies, and three tasks have paid for it.
- **It names the three keys it copies** (`path`, `element`, `children`) and **fails if a route has a
  fourth**. A spread would have carried a `loader` or a `lazy` silently; naming them means a later task that
  adds one finds out in a case about the instrument rather than in an arm that quietly measured a flattened
  route.
- `probeIn(container)` returns **which** probe rendered, so an arm cannot pass on the *wrong* screen
  appearing.

**`arriveWithTable(table, path, { strict })`** mounts without `AppShell`. The shell is not part of the route
table — it is a bare `<div>` that task 16's suite wraps around the provider — so the container **is** the
outlet's subtree and `container.innerHTML === ''` is AC8's exact claim with no scoping to get wrong.

**`release()`** unmounts between iterations of a loop, because `afterEach` only runs between *cases*.

**`guardSource()`** is a **second** comment-stripping reader beside task 37's `source()`, not a rewrite of
it: `source()` has eleven call sites and the abstraction serving both is not worth editing them for.
Stripping is not optional here — the guard's header *names* the surfaces it does not touch, so a scan
reading prose would fail on the comment that explains why the code is clean. T39's AC2 scan passed on prose
its own phase-9 fix had written.

## 2 · The eighteen cases

| | case | kills |
|---|---|---|
| **AC2, AC8** | no content on **any** of the eleven without a session | RS01, RS05, RS06, RS09, RS10 |
| **AC2** | …under `StrictMode` too | RS01, RS05, RS06 |
| **AC2** | an **empty-string** token is not a session | **RS03** |
| **AC4** | **all eleven** served *with* a session — a split, not a deny | **RS02** |
| **AC3** | **CONTROL:** same table, same paths, pass-through gate — the content arrives | makes AC2 falsifiable |
| **AC1** | **all nine** public paths served without a session | RS13 |
| **AC1** | the **catch-all** served without a session | **RS11** |
| **AC10** | the location is unchanged and `historyAction` is `POP` on every public path | RS06 |
| **AC10** | …and on a **blocked** path too, rather than redirecting | **RS06** |
| **AC12** | one pathless route holding exactly `AUTHENTICATED_ROUTE_PATHS`, public nine and catch-all beside it | **RS12**, RS11 |
| **AC13** | exactly one pathless route carrying exactly **one** element, and it is the gate | **RS14**, RS17 |
| **AC13** | **CONTROL:** the sweep counts, rather than returning a list that happens to match | — |
| **AC7** | the gate reaches for nothing it should not — 37 patterns | **RS07**, **RS08**, RS06 |
| **AC7** | **CONTROL:** **every** pattern fires on its own planted probe | — |
| **AC7** | it **does** read the session owner, and decides on truthiness | RS01, RS02, RS03 |
| **AC6** | `auth/` and no import from `app/`; `app/` names it | — |
| **AC1** | a public path with a **trailing slash** | — |
| **AC1** | a public path with a **query string**, and the query survives | — |

The AC3 control is the case the whole task turns on: it differs from the AC2 case in **one value**. Without
it, AC2 would be measuring the absence of a screen rather than the presence of a gate.

**AC7's 37 rows are each paired with a snippet that trips them**, and the control iterates the same table,
so a pattern cannot arrive without a probe proving it fires — T37's AC7 lesson, where a single planted
`sessionStorage` proved one surface and said nothing about twelve others. The session **reader** is
deliberately absent from the forbidden list and its **writers** are deliberately present: asking
`accessToken.current()` is this module's whole job, while writing the session from inside a render would make
the gate a cause rather than a question.

## 3 · Two things measured rather than assumed

- **A trailing slash is normalised.** `/app/verify/` resolves to `verify` and is served. Phase 4 accepted
  F15 without knowing the answer; the case now pins the behaviour, so a router upgrade that changed it is a
  failure rather than a surprise.
- **`historyAction` is the instrument for "no navigation."** `POP` is the initial entry; a `<Navigate>` or a
  `redirect()` anywhere in the tree makes it `REPLACE` or `PUSH`. That is what keeps R28's **second** clause
  with task 41, and it is why RS06 now dies by a T40 case instead of only by T22's token-storage suite.

## 4 · The harness's expectation field now claims more

At phase 7 the field predicted *where* a kill would come from, because the suite did not exist
(`inherited` / `phase 10`). Those predictions and their outcomes are kept in `07-mutation-run.txt` and
`09-mutation-run.txt`: five rows died better than predicted, and three of those disagreements changed what
this phase wrote. Now the field means:

- **`own`** — killed, **and** with a named failure in a file this task owns or grew.
- **`unfalsifiable`** — no observable effect; scored apart, never as a kill or a loss.

A row that dies only outside `OWN_TESTS` prints `INHERITED-ONLY` and is reported as a **disagreement**, not
a kill. This is Kimi's **I5** and self-review **F4** turned into machinery: RS06 and RS07 died exactly that
way at phase 7, by T22's package-wide `console` sweep — a claim about **token leaks**. If that sweep is ever
narrowed, this harness says so instead of T40's coverage disappearing silently.

## 5 · Instruments of other tasks: none touched this phase

Phase 6 grew three (task 16's `pathTree`, task 33's AC11 walker, task 25's chain control) and phase 9 fixed
two statements task 40 had made false. **Phase 10 added nothing to any file but the named suite**, and the
two closed-task files are in `OWN_TESTS` only so that a kill landing there is reported as `own` rather than
as inherited cover.

## 6 · Open, for phase 12

- **RS04** stays scored apart: `null` and `<></>` both render nothing, so no DOM assertion separates them.
- **OQ-T40-1** (the gate reads at render, not reactively) is documented in the guard's header and in the
  frozen brief, with task 42 named. No case asserts it, because closing it would need a new surface on the
  session owner that this task may not add.
- **The gate is still dormant in production**, like the nine tasks before it: `endSession()` has no caller
  (OQ-T36-5) and no authenticated screen exists to be gated (task 42, blocked on 173/174). What T40 builds is
  reachable the moment a screen is attached — and the handoff pins are what will make that visible.

**Phase 10 complete.** Eighteen cases, 47/47 in the named suite, 1742/1743 whole-suite, 16/16 regressions
killed by named cases of this task's own.
