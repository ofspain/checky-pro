# frontend · T29 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (**FROZEN**) |
| **Produces** | `artifacts/05-implementation-plan.md` |

One file, no production change anticipated. Eleven acceptance criteria, eleven `describe` blocks.

---

## The two measurements this plan is built on

Both were taken before planning, with a throwaway probe that has been deleted.

### M1 · Under a fake clock that is never advanced, a microtask flush is enough

**This is the hazard that would have broken AC5 in Phase 6.** T28's `turn()` helper is

```ts
await act(async () => { await new Promise((resolve) => { setTimeout(resolve, 0); }); });
```

With `vi.useFakeTimers()` installed, that `setTimeout` **never settles**, so any case reusing `turn()` under a
fake clock hangs until vitest's timeout. AC5 therefore cannot use the file's ordinary arrival helper.

**Measured:** the `error=` discard path in `CallbackScreen` is **synchronous** — `params.get('error')` is read
and `discard()` is called with nothing awaited in between (`CallbackScreen.tsx:86-104`) — so

```ts
await act(async () => { root.render(…); });
await act(async () => {});          // microtasks only; the clock is never touched
```

renders `en.signIn.discarded` into the document and the live region, with the fake clock at 0. Verified for
`?error=access_denied&state=S`.

**Consequence for the plan:** a second arrival helper, `arriveUnderFakeClock`, which installs
`vi.useFakeTimers()` **before** the render and flushes microtasks only. The five AC5 cases use it; nothing
else does. `vi.useRealTimers()` goes in `afterEach`.

### M2 · A synthetic `setTimeout(…, 100)` render is absent at clock 0

**Measured:** a component that sets its text inside `setTimeout(() => …, 100)` has an empty document after the
same two-step flush. So F9's control can fail for the right reason, and AC5's assertion is not decoration.
A `setTimeout(…, 0)` fails it too, which is the shape F3 named.

## File

`frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx`

## Vocabulary and patterns

| Constant | Contents | Why drawn here |
|---|---|---|
| `ACCOUNT_STATE_WORDS` | the five state names, as the **axis**: `unknown`, `missing`, `locked`, `suspended`, `deleted` | F1, from `tasks.md:95` — not from R30, which lists four clauses |
| `ALWAYS_FORBIDDEN_STATE` | `locked\|lockout\|suspended\|deleted\|deactivat\|inactive\|no\s*such\|banned\|terminated\|archived\|dormant` | each measured at **zero** comment-stripped hits, and no spec surface commissions it |
| `SIGN_IN_SCOPED_STATE` | `expired\|revoke\|invalid\|denied\|forbidden\|blocked\|frozen\|rejected\|unknown\s+account\|account\s+(?:not\|does\s+not)` | F7's rejected half: `EXPIRED` is a specified invoice state, `revoke` a specified session action, `invalid` form-validation copy, `blocked`/`denied` the `HELD` compliance path. Sign-in-scoped or not at all |
| **never in either** | bare `unknown`, `missing`, `pending`, `closed`, `disabled`, `not found` | measured 14 / 2 / 9 / 3 / 1 / 1 legitimate hits — the TypeScript type, `'claim-missing'`, the in-flight renewal, a channel, `aria-disabled`, `mountShell`'s throw |
| `TIMER_SOURCE` | `setTimeout\|setInterval\|requestIdleCallback\|requestAnimationFrame\|queueMicrotask` | AC6, whole-tree |
| `PERMITTED_TIMERS` | three rows: `api/sessionBoundary.ts`, `api/crossTabRenewal.ts`, `api/rateLimitNotice.ts`, with counts and reasons | F4/F5 — a reasoned allowlist in place of a file list |
| `SIGN_IN_SCOPED_PREFIXES` | `['signIn.', 'callback.']` | the same split T28 froze |
| `SIGN_IN_KEYS` | `['action','discarded','explanation','failed','heading','starting']` | AC10, cited to T28 |

Every pattern is a **factory**, never a module-level `RegExp` constant: a `g`-flagged literal used with
`.test()` carries `lastIndex` between calls.

## Helper inventory

**Reused from T28's shape, not imported** — a test file importing another test file's helpers couples two
independent claims, and T24→T25 already set the precedent of duplicating rather than sharing:

`strip`, `productionFiles`, `productionSources`, `productionPaths`, `sourceRoot`, `rel`, `refuseEmpty`,
`hitsOf`, `synthetic`, `catalogueStrings(root = en)`, `watchConsole`, `surfacesAfter`, `leaks`, `stubFetch`,
`seedPkceItems`, `tokenWith`, `tokenEndpointAnswers`, `tokenEndpointRefuses`, `turn`, `arriveAt`, `outcomeOf`,
`clearArrivalSurfaces`.

**New, and T29's own:**

| Helper | Job |
|---|---|
| `ACCOUNT_STATES` | the five fixtures: `{ state, query, description, uri, needles }`, each carrying its own vocabulary in `error`, `error_description` and `error_uri` (F2's Option B) |
| `queryForState(state)` | builds the arrival query from a fixture — one place, so no case writes a URL |
| `measureState(state)` | `measure(queryForState(state), tokenEndpointAnswers())` |
| `arriveUnderFakeClock(query)` | M1's helper: fake timers installed before the render, microtask flush only |
| `renderedSentence(arrival)` | the live region's text, read the one way AC5 asserts |
| `styleAttributesOf(container)` | every `class` and `style` attribute on every node, for AC4 |
| `unpermittedTimers(targets)` | `hitsOf` minus the allowlist, counted — T28's `unexplained` generalised to timers |
| `forbiddenStateEntries(entries)` | the catalogue scan with the two-tier split |
| `Delayed` | the synthetic `setTimeout(…, 100)` component F9 requires |

## The five fixtures (AC1, AC2, AC3)

Each is an arrival at `/app/callback` whose three error fields name one state. More hostile than SAS would
ever send, which is the point (F2, T29-D1).

| State | `error` | `error_description` | `error_uri` |
|---|---|---|---|
| `unknown` | `access_denied` | `No account exists for that email address` | `https://id.example/help/unknown-account` |
| `missing` | `invalid_request` | `The account record is missing` | `https://id.example/help/missing-account` |
| `locked` | `access_denied` | `This account is locked after too many attempts` | `https://id.example/help/locked-account` |
| `suspended` | `unauthorized_client` | `This account has been suspended by an administrator` | `https://id.example/help/suspended-account` |
| `deleted` | `access_denied` | `This account has been deleted` | `https://id.example/help/deleted-account` |

`needles` per fixture = the state word, the full description, and the URI. AC3 sweeps all three.

## Blocks, in file order

| # | Block | Cases | Instrument |
|---|---|---|---|
| 1 | **AC1** — each of the five ends in the one discard closure | 2 | the real screens; `signIn.discarded`, `/app/sign-in`, `REPLACE`, empty `search`/`hash`, no token |
| 2 | **AC2** — the five produce an identical struct | 3 | `outcomeOf` + the success control + the not-a-shell control |
| 3 | **AC3** — no state word, description or URI survives | 4 | `surfacesAfter` × 6 surfaces, `watchConsole` × 6 channels, sentinel control per surface, late-watcher control |
| 4 | **AC4** — no `class`, no `style`, pinned | 2 | `styleAttributesOf` + the `className="locked"` synthetic (F6) |
| 5 | **AC5** — the sentence is present with the clock at 0, for all five | 2 | `arriveUnderFakeClock` (M1) |
| 6 | **AC5/AC11** — the synthetic delayed render fails the same assertion | 1 | `Delayed` (M2, F9) |
| 7 | **AC6** — no unpermitted timer anywhere | 4 | whole-tree scan, the three-row allowlist, accounting controls (F4, F5) |
| 8 | **AC7** — no production module names a state | 5 | source scan + the four measured-exclusion controls |
| 9 | **AC8** — no catalogue string names a state, split held both ways | 5 | catalogue scan + permitted-elsewhere + too-narrow + accounting |
| 10 | **AC9/AC10** — the gating scan and the key-set pin | 3 | inherited from T28, cited |
| 11 | **AC11** — every instrument shown DIRTY, empty tree refused | 2 | the signpost table + the one real guard |

Estimated **33 cases**, roughly 20 of them controls. The count is an estimate, not a target.

## Ordering risks, each with its mitigation

| Risk | Mitigation |
|---|---|
| `turn()` under a fake clock never settles (M1) | AC5 uses `arriveUnderFakeClock`; `vi.useRealTimers()` in `afterEach`; no case mixes the two helpers |
| Five fixtures that are secretly one fixture (F2's vacuity) | a case asserts the five `needles` sets are **pairwise distinct** before any equivalence is claimed |
| `document.title` / `window.name` leaking between cases | one `clearArrivalSurfaces()`, called from the arrival helpers, not only `afterEach` — T28's Kimi Gap 2 |
| A `g`-flagged pattern carrying `lastIndex` | every pattern is a factory |
| A scan reading the file's own prose | `strip` before every scan; assert over files, not `path:line` |
| An empty tree reported clean | the single `refuseEmpty` guard, driven by the controls themselves |
| The allowlist accounting never exercised | the table is non-empty here, unlike T28's — but a row-short and a row-for-another-file control still run |
| A spy installed after the code under test | the late-watcher control, carried over from T28 |

## Mutants to write in Phase 7

MC01 a state-specific sentence in the catalogue · MC02 a lockout countdown · MC03 a cause-keyed
`setTimeout(…, 250)` before the notice · MC04 a `setTimeout(…, 0)` before the notice (F3's subject) · MC05 an
account-state gate on the render · MC06 a state word in a component · MC07 `className` keyed on the cause
(F6) · MC08 a divergent redirect target for one state · MC09 a timer inside an `auth/` module (F5's subject) ·
MC10 the description written to `document.title` · MC11 the state word carried in the sign-in URL · MC12 an
escape mutant: a delay that fires only for a state no test drives · MC13 the live region removed · MC14 a
sixth `signIn.*` key naming a state.

## Verification

`tsc --noEmit`, `oxlint src`, the file alone, the whole suite (baseline **1342 / 1**), then the harness with
everything under `frontend/src/` committed first.

---

**Phase 5 complete.** Ready for Phase 6 — implementation.
