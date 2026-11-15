<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). -->

# frontend · T46 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Task statement (verbatim, `spec/frontend/tasks.md:80`):**
> **Code points, not UTF-16.** Length is counted in Unicode code points. Verifies
> `passwordLengthCountsCodePoints`. Cites R1.

## 1 · Architecture summary

The prompt's section headings are written for a Spring service; the honest frontend reading of each:

| asked | what exists here |
|---|---|
| **modules** | A React 19 + TypeScript SPA under `frontend/src`, built by Vite 8, routed by react-router 8 under the `/app` basename. Four layers: `app/` (routes, shell, and the `app/ui` design system), `features/<area>/` (screens and their logic), `auth/` + `api/` (the OAuth flow and the generated client), `i18n/` (one catalogue, one i18next instance per tab). |
| **persistence** | None in this task's reach. L3 forbids storage on the registration journey, and T43's AC10 case proves the whole journey writes neither `localStorage` nor `sessionStorage`. |
| **events/outbox** | Not applicable. The only cross-tab messaging is the renewal/sign-out `BroadcastChannel` in `api/`, far from this task. |
| **security** | Registration is public: no bearer, `security: []` in the contract, and T43's AC8 proves the screen renders with no session and reads no token. R1's check is **advisory** — the server is the authority — which is the governing fact of this task, not a footnote. |

## 2 · Existing code this task touches

### The whole of it: `frontend/src/features/account/passwordLength.ts` — 25 lines, three exports

```
countCodePoints(value: string): number   →  [...value].length
ADVISORY_MINIMUM_CODE_POINTS = 12
ADVISORY_MAXIMUM_CODE_POINTS = 128
```

Written by **task 43**, which needed a count, under decision **D6**: it returns *the count, not a verdict*.
Phase 3 of that task proposed `isTwelve(password): boolean`; the freeze declined it because task 44 needs
128, task 45 needs "outside 12 to 128" and **task 46 needs the counting unit itself** — all three would
have had to rewrite a predicate, which L12 forbids.

Its header already names this task in as many words:

> *"Task 46 (`passwordLengthCountsCodePoints`) is the task that makes that claim in general; this function
> is written here, by the task that first needs a count, in the unit that task will come back and assert."*

**New:** `registrationRejectsPasswords…`-style named suite for `passwordLengthCountsCodePoints`. The file
does not exist. `package.md:183` maps the name to R1.

### What already asserts something about this function

| where | what it claims | whose claim it is |
|---|---|---|
| `registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:394-410` | **totality** — `''`→0, a lone surrogate `'\uD83D'`→1, `'a\uD83Db'`→3, `'👍'`→1, `'👍'.repeat(128)`→128 | task 43's (D16), and its comment says explicitly this is *"NOT TASK 46'S WORK DONE EARLY"*: 43 owns totality, 46 owns the counting unit |
| `…:295-305` | `'👍'.repeat(12)` is **24 UTF-16 units in the DOM and 12 code points to the counter**, plus the two constants' values | task 43's, through the DOM |
| `…:307-332` (T44 AC2) | 128 emoji are **256 units and 128 code points** | task 44's, through the DOM |
| `registrationRejectsPasswordsOutside…test.tsx:211` | every out-of-range row asserts `countCodePoints(password)` equals the row's count, including **11 astral = 22 units** | task 45's, through the DOM |

Two facts the counting work has already produced, which this task inherits rather than rediscovers:

1. **The bug inverts across the range.** A `.length` check over-counts, so at the *lower* bound it pushes a
   short password **into** range (false accept, and at 11 astral it makes the screen fall **silent**), and
   at the *upper* bound it pushes a valid one **out** (false reject).
2. **`'👍'` is one code point and two UTF-16 units**, so `'👍'.repeat(128)` is 256 units — a number that was
   wrong in seven places across T44's phases 0–2 and in four places in an adversarial review before anyone
   measured it.

### Consumers

| file | references |
|---|---|
| `features/account/RegisterScreen.tsx` | 5 — the submit-time check and the two interpolated bounds |
| `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | 18 |
| `features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | 18 |
| `i18n/catalogue.ts` | 2 — prose only, in the comment explaining the template |
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | 1 |

`passwordLength.ts` has been **read-only to every task since 43**. T44's freeze and T45's both list it under
"do not modify".

## 3 · Established patterns to follow

| | |
|---|---|
| **Named suites are self-contained** | One file per `package.md` §8 name, no shared test-util module. This is **FU-T43-2**, recorded three times and still open; T45's freeze decided duplication over extraction on the grounds that a task has no mandate to add a module. |
| **A claim is not shipped until a mutant kills it** | Every task carries a `NN-mutation-harness.py` with one row per claim, each predicting `own` / `inherited` / `unfalsifiable`, canaries scored apart, and attribution by failing **case name** rather than count. T45's is 48 rows. |
| **Tests are committed before the harness runs** | The harness reverts `frontend/src` with `git checkout --` + `git clean -fq`. |
| **The harness runs with the local Docker stack stopped** | FU-T42-1 is load-sensitive, and phase 9 of T45 measured that it can only invert a row with no genuine kill of its own. |
| **Comments carry the reasoning, and a false one is a defect** | Three comments were corrected during T45 for saying something measurement contradicted. |
| **Negative assertions ship with a control** | A query that finds nothing must be shown finding something. |
| **Source scans where the DOM cannot see** | Provenance, copy-spelling and layer violations are invisible to rendered output, so they are asserted against the file text with comments stripped. |

## 4 · Testing conventions

The prompt's JUnit/Testcontainers/`Clock` conventions are the backend package's. Here:

| | |
|---|---|
| runner | **Vitest 5** on **jsdom**, `npx -y pnpm@12.14.0 exec vitest run`. The **exit code** is the verdict, never the pass count. |
| known failure | **1** environmental: `src/api/lintRejectsHandWrittenBackendFetch.test.ts` — this host's `oxlint@1.87.0` prints no summary line. That file also holds the repo's only end-to-end build gate. |
| current totals | whole suite **1964 / 1965**; `tsc --noEmit` 0; `oxlint src` 0 |
| shape | every existing suite touches the DOM, a stubbed global, or the filesystem. **No suite in this package is a plain unit test of a pure function** — the closest are the function-level assertions on `pkce`'s helpers inside `authorizeRequestsExactlyTheDeclaredScopes.test.ts`, which still stub `crypto`. A suite for `countCodePoints` would be the first file here with no environment to arrange, which changes what its mutants can look like. |
| TypeScript | `target`/`lib` **ES2022** (with OQ-T31-4 open: type-fest's `reference lib="esnext"` silently lifts that ceiling for all of `src/`). |

## 5 · Known gaps and unknowns

| | |
|---|---|
| **What a suite for a four-token function can measure** | `[...value].length` has no branches, no state and no environment. The usual instruments — a control proving a query works, a mutant on a conditional — mostly have nothing to bite on. **I do not know yet** what the harness rows for this task look like beyond swapping the spread for `.length`; phase 2 and phase 3 have to answer it, and the risk of a suite that restates the implementation is the central design problem here. |
| **Whether the constants are in scope** | The task statement says *"length is counted in Unicode code points"* and nothing about 12 or 128. Three existing cases already assert the constants' values. **I do not know** whether T46 should pin them again, and a duplicate pin that restates another task's claim is exactly what T173's review called out. Phase 1 decides. |
| **Grapheme clusters** | A flag, a skin-toned emoji or a family emoji is several code points and one *perceived* character. R1 says code points, and T45's catalogue comment records the deliberate decision to say "characters" to the visitor anyway. **Whether T46 should assert the difference** (rather than just the UTF-16 one) is a real question: it is the other direction in which "length" is ambiguous, and nothing in the package measures it. |
| **Normalisation** | `'é'` is one code point composed or two decomposed, so the same typed password can count 1 or 2. Nothing in this codebase calls `.normalize()` — and one suite actively *forbids* it in the callback path (`callbackWithMismatchedStateIsDiscarded.test.tsx:1075`). Whether R1's count is normalisation-sensitive is **not stated in the spec**, and I do not know the owner's intent. |
| **Lone surrogates** | Already pinned by T43's totality case. Whether T46 restates it or cites it is phase 1's. |
| **`Intl.Segmenter`** | Not used anywhere in `frontend/src`. If grapheme clusters enter scope, it is the mechanism — but that would be a production change, and no task has authorised one. |

**Phase 0 complete.** No code written; `passwordLength.ts` read, not modified.
