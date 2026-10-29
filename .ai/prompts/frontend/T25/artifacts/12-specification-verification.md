# frontend · T25 · Phase 12 — Specification Verification

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |

> **Return target outside allowlist.** A return target off the allowlist falls back to the home route. Verifies
> `returnTargetOutsideAllowlistFallsBackToHome`. Cites R7, L1. — `spec/frontend/tasks.md:37`

**Verdict up front: PASS**, with **three declared deviations**, none of which is a shortfall against this task's
own scope.

**Measured state.** `tsc --noEmit` exit 0. Suite **1192 passed / 1 failed** by exit code, the failure being the
one known environmental case (`lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0`
prints no summary line, recorded at T18; CI must pin its identity, which no task owns). Named test **184 cases**.
**Mutation: 25 of 25 killed, no survivors, every one by T25's own file**, re-run from the committed script at
`.ai/prompts/frontend/T25/artifacts/10-mutation-harness.py`.

**Files.** **One file changed in the entire task:**
`frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` (+712/−35), plus the harness script as a
new artifact. **No production code was touched** — verified by diff over `0ec7f16d..HEAD`. **`spec/` untouched.**

---

## 1 · Traceability — requirements and LOCKED decisions

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R7** — on a matching `state`, validate `nonce`, exchange with the verifier, hold the token in memory only | Yes (T20/T21/T23/T24) | `CallbackScreen.tsx:194-195` | Yes — every one of the 24 flow rows asserts the token is **held**, so no row can pass against a discard | No | No |
| **L1** — allowlisted post-login return target, falling back to the home route | Yes (T24 built it) | `returnTarget.ts:70-72` (membership), `:51-59` (derivation), `:120-139` (take + fallback) | **Yes — this task.** `:1388` (24 flow rows), `:1335` (24 counterfactuals), `:1489` (8 near-miss pairs) | No | No |
| **L1 / `agents.md:21`** — **"never to an arbitrary URL"** | Yes | `returnTarget.ts:70-72` | **Yes, family by family** — 24 families, 7 `refuse` / 9 `reach` / 8 `miss`, each with the harm measured first | No | No |
| **L3** — tokens in memory only | Unchanged | `returnTarget.ts:91` writes only a route path | Yes — `:1389` asserts the whole store is empty after every refusal, and `:1056` sweeps both stores for the token and the code | No | No |
| **L4** — neutral copy; no outcome distinguishes a cause | Yes | nothing added to `src/i18n/` | Yes — **per row** (`:1389`): pathname, search, **rendered text** and `historyAction` all equal the no-target baseline | No | No |
| **L16** — the carve-out; the return target single-use and deleted at callback | Yes | `returnTarget.ts:120-139` | Yes — a refused target is **consumed** (`:1389`) and consumed **by a removal, never a `clear()`** (AC21) | No | No |
| **L9 / L10** — single origin, secrets out of the bundle | Not engaged | — | n/a — the absolute-URL guard scans the **built bundle**; test files are not in it (Phase 0 §5.4), so the rows carry real URLs legitimately | No | No |
| **L11** — catalogue-only strings | Yes, vacuously | no new string | Yes — `src/i18n/` untouched by diff | No | No |
| **R8** — a discard returns the visitor to sign-in | Unchanged | `CallbackScreen.tsx:89-99` | Yes — `:1437`, four shapes, three of them `reach` | No | No |

## 2 · Traceability — acceptance criteria AC1–AC21

| AC | Verified? | Evidence |
|---|---|---|
| **AC1** every family lands on `/app/home` | Yes | `:1389`, 24 rows |
| **AC2** refused by the real exported predicate | Yes | `isAllowlistedTarget(shape)` in every row; exported for this task by name (T24 F6) |
| **AC3** the honoured destination reachable from the same harness | Yes | `flows.honoured.pathname === '/app/account/sessions'` in every row |
| **AC4 / AC17** indistinguishable from no target, four fields, **per row** | Yes | `toEqual(flows.absent)` + `historyAction === 'REPLACE'` — Kimi F5's correction, and mutant M25 kills the alternative |
| **AC5** a refused target is consumed | Yes | `flows.storeAfterRefusal` is `[]`; mutant M18 (refused targets not consumed) kills 40 cases |
| **AC6 / AC20** no discard honours one; a discard leaves a refused one | Yes | `:1437` |
| **AC7** nothing enters storage; no token or code | Yes | the whole store asserted, sorted and empty |
| **AC8** the traversal control | Yes | `:1336` row 7 asserts the bare router reaches `/app/admin`, route `admin` |
| **AC9 / AC16** the external shapes never reach `navigate()` | Yes, **by conjunction** | the 7 `refuse` controls assert the throw; the flow rows show the same shapes ending on home, which is unreachable had the throw occurred. Recorded at the Phase 9 gate, including its reliance on reading the **exit code** |
| **AC10** query and fragment not honoured; the query does not travel | Yes | their rows, plus `search` inside the four-field comparison; mutants M10 and M11 die |
| **AC11** a parameterised route with a real id | Yes | its row — and the counterfactual shows the router **does** match `invoices/:id`, which makes T24-D5's cost a measurement |
| **AC12 / AC18** the producer refuses every shape | Yes | `:1418`, 24 rows, with the allowlisted control; mutants M22 and M23 die |
| **AC13** no new catalogue string, dependency, or production change | Yes | one file changed in the whole task, verified by diff |
| **AC14** suite green by exit code; `tsc` 0 | Yes | measured repeatedly |
| **AC15** totality on the **refused** branch | Yes | `:1536` — a throwing removal, a throwing read, and an unreachable store **over every family** |
| **AC19** `/home/`, `/HOME`, `/account%2Fsessions` as rows, each paired with its near miss | Yes | the three rows plus `:1490`'s 8 pairs |
| **AC21** consumed by a **removal**, never by wiping the store | Yes | `flows.clearedDuringRefusal === 0`; mutant M19 kills 28 cases where it previously killed none of T25's |

**All twenty-one met. None deferred.**

## 3 · Declared deviations

**D-1 · No production code was written, and that was the expected outcome.** T25-D5 made it conditional in
writing: if any shape had been found unrefused, the narrowest fix was in scope as a declared deviation. **The
condition never triggered** across 24 families × (predicate, producer, flow, unreachable store). The spec splits
one named test between tasks 24 and 25 (`tasks.md:36-37`), T24 built the mechanism, and T25's deliverable is the
proof. Recorded as a deviation only because "a task that changes no production code" deserves to be stated rather
than discovered.

**D-2 · The destination renders nothing (T24-D11).** `/app/home` is registered with no element and no task in the
142-task list builds a home screen, so every row in this task asserts a navigation to a blank route. The
navigation is the whole of the verifiable claim and no row asserts more.

**D-3 · jsdom cannot prove a browser refuses anything (T25-D6).** `window.location` is unchanged in every
measurement **because nothing here can change it**, and no row presents that as evidence. The counterfactual
controls are the honest substitute: they show what react-router itself would do. A real round trip belongs to
`oidcRoundTripEndToEnd`, which has no suite and no owning task.

## 4 · The approving principal engineer's answers

**(1) Is the task fully complete?** Yes. `tasks.md:37` asks that a return target off the allowlist falls back to
the home route, and that is proven for twenty-four families — the fourteen the `it.todo` list named plus ten
added, every one of them traceable to a Kimi finding or a surviving mutant. The fifteen `it.todo` rows T24 left
are all discharged: fourteen as families, and the fifteenth ("the same copy as an honoured target") promoted from
one sampled sweep into an assertion inside all twenty-four rows.

**(2) Does it satisfy every acceptance criterion?** All twenty-one, including the five added at the Phase 4 gate
and AC21 added at Phase 9. None deferred, none partial.

**(3) Does it violate any LOCKED decision?** No. L1 is the decision verified; L3, L4, L11 and L16 are re-proven
for the refused path specifically — L4 **per row**, which is stronger than T24's single sweep; L9/L10 are not
engaged, and the one place they could have been was checked rather than assumed. `spec/` untouched, and no
production file was modified at all.

**(4) Remaining risks.**

| Risk | Owner |
|---|---|
| **Seven of the nine `reach` families have no second line of defence.** The router would land on a real route, so membership is the only thing stopping them. A future "just normalise it" change is the live hazard, and three mutants (M1 case-folding, M2 trailing slash, M3 trimming) exist to make it fail | this task's tests; **T25-D5 forbids** such a change without a gate decision |
| The fallback lands on a blank route; no task builds `/app/home` | **unowned** (T24-D11) |
| No production caller of `rememberReturnTarget`, so the fallback is the only path the product exercises | task 41 |
| The carve-out is two constants in two modules; a guard enumerating only one would flag the return target | **task 26** (T24-D4) |
| Notification-email deep links to `invoices/:id` and `receipts/:id` are not honoured — and the counterfactual now shows the route **does** match, so the cost is a measured one | declared (T24-D5) |
| No real-browser round trip — the largest verification gap in the package | **unowned** |
| The environmental `oxlint` failure is indistinguishable from a real lint regression on this host | **unowned** (T18 Gap 6) |
| **The mutation harness was uncommitted for twelve tasks (T14–T24)**, so those results are not reproducible. T25's is committed; the earlier ones are not | a package-level follow-up, raised by Kimi Gap 1 |

Two smaller carry-forwards, unchanged: `contracts/api/token-claims.md` is false on `email`/`name`, and there is no
ID-token contract document.

---

**VERDICT: PASS** — "never to an arbitrary URL" is verified family by family, with the harm of each family measured
before its defence is claimed, and with the nine families where the allowlist is the *sole* defence identified as
such; all twenty-one acceptance criteria are met and no production code needed to change.

---

## Next — Phase 13 — PR preparation.
