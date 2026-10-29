# frontend · T25 · Phase 6 — Implementation Notes

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

**No production code changed.** T25-D5's condition did not trigger: every one of the eighteen frozen shapes is
refused by the real predicate and by the real producer, measured through the real flow. One file was edited,
`returnTargetOutsideAllowlistFallsBackToHome.test.tsx`, +350/−37.

**Named test: 124 passed, 0 todo** (was 67 passed, 15 todo). **Suite: 1132 passed / 1 failed** by exit code over
two consecutive runs, the failure being the known environmental case
(`lintRejectsHandWrittenBackendFetch.test.ts:118`). `tsc --noEmit` exit 0.

---

## 1 · The measurement that reorganised the table

The plan's execution order put the counterfactual controls first, and they paid for themselves immediately: with
all eighteen shapes measured against a bare router, **the families are not one kind of risk but three**, and the
table now records which each is.

| Kind | Count | What the router would do | What that means |
|---|---|---|---|
| `refuse` | **7** | throws `External navigation is not allowed` | the allowlist is **defence in depth** — two independent refusals |
| `reach` | **7** | lands on a **real route** | the allowlist is the **sole defence** |
| `miss` | **4** | lands on the catch-all | harmless either way |

The `reach` seven, each verified:

| Shape | Router reaches | Why it is a real route |
|---|---|---|
| `/home/../admin` | `/app/admin` (route `admin`) | traversal is resolved by the router before matching |
| `/sign-in` | `/app/sign-in` (route `sign-in`) | a real **public** route — the harm is a loop: a completed sign-in sent back to the sign-in screen |
| `/invoices/123` | `/app/invoices/123` (route `invoices/:id`) | **the route works.** The allowlist is exact paths and cannot express a parameterised one — T24-D5's cost, now concrete rather than argued |
| `/home?x=1` | `/app/home` (route `home`) | the query rides along |
| `/home#top` | `/app/home` (route `home`) | so does the fragment |
| `/home/` | `/app/home/` (route `home`) | **react-router ignores a trailing slash** |
| `/HOME` | `/app/HOME` (route `home`) | **react-router matches case-insensitively** |

This is the finding of the task, and it is why Kimi's F4 was upgraded at the gate. A membership check that folded
case, stripped a trailing slash, decoded, or trimmed would not merely admit a stray string — **the router would
honour the result**, and a visitor would end a successful login somewhere they were never sent. Seven of eighteen
families have no second line of defence behind them.

`it('is three kinds of risk, and the sole-defence kind is the larger half')` pins the split by count, so a row
cannot be quietly reclassified into the weaker assertion.

## 2 · What changed, mapped to the plan

| Plan item | Done | Where |
|---|---|---|
| `visibleOutcome` lifted out of the T24 AC9 case | yes — **the one authorized touch of T24 code** | the case's body shrank to `visibleOutcome(absent)` / `visibleOutcome(rejected)`; **not one assertion changed**, and the file was re-run on its own to prove it (67 passed, unchanged) |
| `routerOutcomeFor(target)` | yes | a bare `createMemoryRouter` on the real `appRoutes`, returning either `{threw}` or `{pathname, matched}` |
| `threeFlows(shape)` | yes | three sign-ins per row — refused, absent, honoured — each with its own `sessionStorage.clear()` and `vi.resetModules()`. **No baseline is captured in another case** (T25-D2) |
| eighteen family rows | yes | `FAMILIES`, with `label` / `shape` / `routerWould` |
| counterfactual controls (T25-D1) | yes | `describe('what the router would have done, measured first')`, one case per family plus the split pin |
| per-row AC4 (T25-D2, F5) | yes | `expect(flows.refused).toEqual(flows.absent)` inside every row, four fields, plus `historyAction === 'REPLACE'` |
| per-row AC12 (T25-D3, F2) | yes | a second `it.each`: `is not even storable: $label`, with the allowlisted control beside it |
| F3's discard row | yes | a refused target seeded, a refused exchange, and the value **untouched** |
| two new constants | yes | `CALLBACK_PATH_SEGMENT`, `SIGN_IN_PATH_SEGMENT` — the helpers and the table needed the segment, not the full route |

**124 = 67 (T24) + 57 (T25):** 18 router controls + 1 split pin + 18 flow rows + 18 producer rows + 1 discard row
+ 1 completeness pin.

## 3 · Acceptance criteria

| AC | Where |
|---|---|
| **AC1** every family lands on `/app/home` | the flow `it.each`, first assertion |
| **AC2** refused by the real exported predicate | `expect(isAllowlistedTarget(shape)).toBe(false)` in every row |
| **AC3** the honoured destination was reachable from the same harness | `flows.honoured.pathname === '/app/account/sessions'` in every row |
| **AC4 / AC17** indistinguishable from no target, four fields, **per row** | `toEqual(flows.absent)` + the `REPLACE` assertion |
| **AC5** a refused target is consumed | `flows.storeAfterRefusal` is `[]` |
| **AC6 / AC20** no discard honours one, and a discard leaves a refused one | the discard case |
| **AC7** nothing enters storage, no token or code | `storeAfterRefusal` is the whole store, sorted and empty |
| **AC8** the traversal control | the `reach` row for `/home/../admin` asserts `/app/admin` and route `admin` |
| **AC9 / AC16** the external shapes never reach `navigate()` | the seven `refuse` controls assert the throw, and the flow rows show the same shapes ending on home without it |
| **AC10** query and fragment not honoured, query does not travel | their rows, plus `search` inside the four-field comparison |
| **AC11** a parameterised route with a real id | its row, with the router reaching `invoices/:id` |
| **AC12 / AC18** the producer refuses every shape | the `is not even storable` `it.each` |
| **AC13** no new catalogue string, dependency, or production change | one file edited; `git diff --stat` is one line |
| **AC14** suite green by exit code, `tsc` 0 | measured twice |
| **AC15** the T24 cases pass **unchanged** | 67 before, 67 after, and the only T24 edit is a body with identical assertions |
| **AC19** `/home/`, `/HOME`, `/account%2Fsessions` are rows | three rows; the first two are in the `reach` seven |

## 4 · The runtime measurement T25-D2 asked for

Three flows × 18 rows is **54 mounts**, and the gate required the number to come back here rather than a flow to
be dropped. **The whole file runs in 1.18s** (124 cases; 43% in tests, 40% environment). No flow is dropped and
nothing goes back to the gate.

## 5 · Non-vacuity, checked rather than assumed

Everything passed on the first run, which is the circumstance in which a table is most likely to be proving
nothing. So one mutant was applied before this artifact was written: `isAllowlistedTarget` made to **fold case and
strip a trailing slash** — the exact refactor §1 says would be honoured by the router.

**Five cases failed**, and they are the right five: the `/home/` row, the `/HOME` row, both of their producer
rows, and T24's own `/HOME` corrupted-value row. The mutant was reverted. The full harness is Phase 10's; this was
one probe to establish that the two rows the Phase 4 gate upgraded are the two that catch the mutant it was
upgraded for.

## 6 · Deviations

**None.** T25-D5's conditional production change did not trigger. No normalisation, decoding, pattern matching or
prefix handling was introduced. `CallbackScreen.tsx`, `routes.tsx`, `authorizeRequest.ts`, the two pure readers,
the holder, `.oxlintrc.json`, `src/i18n/` and `package.json` are untouched, and `spec/` was not modified.

**One thing worth naming as a limit rather than a deviation:** the fifteenth `it.todo` row read "every row above
leaves the visitor with the same copy as an honoured target". It is gone as a row because F5 moved it *inside*
every row, which is strictly stronger — a single sweep passes if the one family it samples happens to match. The
completeness pin asserts that no family was lost in that rewrite.

---

## Next — Phase 7 — self-review.
