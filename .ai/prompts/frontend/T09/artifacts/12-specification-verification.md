# frontend · T09 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact, the implementation, `spec/frontend/*` |
| **Produces** | `artifacts/12-specification-verification.md` |

## Phase 11 gaps (Kimi), resolved before verification

| Gap | Decision | Change |
|---|---|---|
| 1 No percent-encoded path case | **ACCEPTED, premise corrected** | Kimi expected `/login%2Fstep-two` to normalise to `/login` and be denylisted. The URL spec does **not** decode `%2F`: `new URL('/login%2Fstep-two', origin).pathname` is `/login%2Fstep-two`, one segment, matching neither list — so it reaches the network, which is the safe side, and is now pinned that way (`:141-142`). The case Kimi's finding *should* have named is dot-segment resolution, which `URL` **does** perform: `:157` asserts `/app/../login` resolves to `/login` and is denylisted on that basis, while `/accounts/../app/home` resolves to the shell. |
| 2 No cross-origin case | **ACCEPTED** | `:166`: `https://other.example.test/login` is denylisted and `https://other.example.test/app/home` is the shell, pinning that the policy reads `pathname` only and cannot start depending on the origin. |
| 3 No root-path case / 6 No empty-pathname case | **ACCEPTED, merged** | `:138` adds `/` to the boundary table (neither denylisted nor shell), and `:174` asserts an origin with no path yields `/` and the same classification. |
| 4 `NAVIGATION_DENYLIST[i]` not paired with `DENYLISTED_NAVIGATION_PATHS[i]` | **REJECTED — already covered** | The AC7 case at `:228` iterates `NAVIGATION_DENYLIST.entries()` and tests pattern `i` against path `i`'s base, `path + '/sub'`, `path + 'x'` and `'/app' + path`. A sorted or otherwise reordered derivation makes pattern 0 (`/.well-known`) face path 0 (`/oauth2/`) and fails. Verified by mutant M2, which the case catches. |
| 5 Meta-test for the comment scanner | **REJECTED** | Kimi marks it "may be overkill" and it is: it would write a temporary file to test the test's own regex, with no production code in the loop. The limitation is recorded in the self-review instead. |
| 7 Production wiring can still read the cache first | **CARRIED FORWARD, not fixable here** | Already the lead self-review finding and recorded in phase 9: task 12 must import `NAVIGATION_DENYLIST` and assert its precache manifest holds no match. |

## Mutation runs (11, all against the final 61-case file)

| Mutant | Caught by |
|---|---|
| M1 drop `/accounts` from the list | 5 cases |
| M2 reorder the list | 2 — AC7's order comparison and AC1's sub-path table |
| M3 `startsWith` instead of a segment boundary | 4 |
| M4 `includes` instead of an anchored prefix | 6 |
| M5 drop the `(?:/\|$)` boundary | 7 |
| M6 drop the regex escape | exactly 1 — the `/xwell-known` row, which exists for it |
| M7 answer every navigation with the shell | 29 |
| M8 drop the navigate check in `isDenylistedNavigation` | 4 |
| M9 read the cache before the denylist check (harness) | 2 |
| M10 order-blind drift parser **and** a reordered list | 1 — paired guard-strength check, not a defect probe |
| M11 drop the navigate check in `isShellNavigation` | 5 |

**11 applied, 11 caught, 0 survived.** Each was applied to the real file, run, then reverted with `git checkout --`;
the working tree was verified clean after every run.

*Process note for later tasks: the harness reverts with `git checkout -- <files>`, which silently discarded the six
uncommitted phase-11 cases on the first re-run and made three mutants look unchanged. The cases were re-applied and
committed before the final run, and the numbers above are from that run. Commit before mutating.*

## Traceability matrix

| Requirement / decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R57** a navigation to any of the eleven identity/backend paths is never answered from cache | Yes | `navigationDenylist.ts:17-29` (the verbatim list), `:55-58` (`isDenylistedNavigation`) | `:80` (×11), `:89` (×11), `:112` (no cache read for all 22) | No | No |
| **R57** …and never receives the application shell | Yes | `navigationDenylist.ts:65-69` — the denylist is evaluated before the shell pattern | `:80` (asserts `isShellNavigation` false for all eleven), `:181` (mutual exclusion over 50 paths) | No | No |
| **R57** the list is `agents.md`'s, unaltered | Yes | `navigationDenylist.ts:17-29`, `as const`, in order | `:213` (drift guard, order included), `:228` (one pattern per path, by index) | No | No |
| **L17** service-worker boundaries: the fallback is bypassed for those paths | Yes, as policy | the module is the policy; the fallback itself is task 12 | `:112`, `:190` via the Workbox-semantics harness (`:47`) | **Production wiring is task 12** | **Yes — D1/D5, declared** |
| **L17 / design.md §5** no API or identity response is cached by this task | Yes, by construction | the module holds no cache reference at all | `:190` (`put` never called), `:250` (no `caches` in module code) | No | No |
| **L14** every SPA route is under `/app` | Yes | `navigationDenylist.ts:32` | `:102` (×4), `:273`, `:145` (`/application` negative) | No | No |
| **L12** phase additivity, T01–T08 untouched | Yes | two new files only; `git show --stat` | whole suite: 209/210 | No | No |
| **L6 / L10 / L3 / L16** no hand-written fetch, no env key, no absolute URL, no token storage | Yes | nothing of the kind in the module | `:250` (twelve banned tokens) | No | No |
| **`agents.md:90`** API routes are NetworkOnly | **Not this task** — task 11 | — | `:199` (×4), `:207` keep this module navigation-only so it cannot pre-empt it | No | No |
| **`agents.md:91-92`** update policy, no `skipWaiting`/`clients.claim` | **Not this task** — task 12 | — | — | No | No |

## Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| AC1 eleven paths and sub-paths reach the network | **PASS** | `:80`, `:89`, `:97` |
| AC2 the shell still answers `/app` navigations | **PASS** | `:102` |
| AC3 no cache read for a denylisted navigation | **PASS** | `:112` |
| AC4 segment-boundary semantics, both directions | **PASS** | `:145` (17 rows), `:151`, `:157`, `:166`, `:174`, `:181` |
| AC5 nothing cached for a denylisted navigation | **PASS** | `:190` |
| AC6 navigation-only | **PASS** | `:199`, `:207` |
| AC7 the list matches `agents.md`, in order | **PASS** | `:213`, `:228` |
| AC8 R57, L17 and `agents.md` cited in source | **PASS** | `:242` |
| AC9 no banned global; shell prefix is `/app` | **PASS** | `:250`, `:273` |
| AC10 the suite stays green, no T01–T08 edit | **PASS with one environmental failure** | 209/210; the failure is `lintRejectsHandWrittenBackendFetch` AC4 |

## The one failing test, stated plainly

`frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` AC4 expects `oxlint`'s output to contain
`Found 0 warnings and 0 errors`. This host's `oxlint@1.87.0` prints no summary line at all — confirmed in T07 by
running it with all task changes stashed (fails identically) and against a deliberately introduced real `fetch`
violation (still no summary line). It is environmental, predates this task, and is unaffected by it. **It is not
counted as passing anywhere in these artifacts.**

## Verdict

**PASS.** R57 and L17 are implemented as a verbatim, drift-guarded, segment-accurate navigation policy, with 61
cases and 11 of 11 mutants caught. One declared deviation: by gate decision D1 the policy is not yet wired into a
registered worker, so the end-to-end guarantee is asserted through a Workbox-semantics harness rather than a running
worker, and **task 12 must build its navigation route from these exports and assert its precache manifest contains
no denylisted URL**. Without that, T09's guarantee can be bypassed with every case still green.
