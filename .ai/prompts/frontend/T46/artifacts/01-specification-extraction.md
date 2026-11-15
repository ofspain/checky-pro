<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). -->

# frontend · T46 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Task** | T46 — Code points, not UTF-16 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

| ID | one sentence |
|---|---|
| **R1** | The SPA checks that the password is **12 to 128 Unicode code points** as advisory guidance only, submits to `POST /accounts` through the generated client either way, and leaves the server as the authority. |

R1 is the only requirement in scope, and this task owns **three words of it**: *"Unicode code points"*. Tasks
43, 44 and 45 built the check, both its bounds and its guidance, and each of them measured the unit only
where it happened to pass through a screen. This task is where the counting unit is the claim rather than
the backdrop — `passwordLength.ts`'s own header has said so since task 43 wrote it.

**What R1 does not say** matters as much here. It does not say *characters*, *graphemes*, or anything about
normalisation. It names one unit, and the unit it names is the one with an exact definition in the standard.

## Locked Decisions

| ID | how it constrains **this** task |
|---|---|
| **L12** *phase additivity* | **The governing constraint.** `passwordLength.ts` is correct today and has been read-only to every task since 43. This task adds a suite; it does not restructure the module, and it does not rewrite the three other suites that already consume it. |
| **L11** *WCAG AA, keyboard, catalogue-only strings* | Only indirectly: this task renders nothing. If a case touches a screen it inherits L11's rules, which is an argument for not touching one. |
| **L6** *generated client only* | Not engaged — this task calls nothing. |
| **L3 / L16** *no token storage, one sessionStorage carve-out* | Not engaged. A pure function reads no storage, which is itself worth one assertion given this package's history with vacuous storage pins. |
| **T173 rules 1–3** | Not engaged — no primitive, no copy, no element. |
| **T44's AC4** *no primitive carries `maxLength`/`minLength`* | Adjacent and already enforced; this task adds nothing to it. The reason the attribute is forbidden is **this task's subject**: it counts UTF-16 units. |

## Files involved

**New**

| | |
|---|---|
| `frontend/src/features/account/passwordLengthCountsCodePoints.test.tsx` *(or `.test.ts`)* | the named suite, `package.md:183`. **The extension is a real decision**: every suite in this package is `.tsx` because every suite renders. If this one renders nothing, `.ts` is the honest extension and would be the package's first. Phase 2 decides. |

**Existing, to read**

| | |
|---|---|
| `frontend/src/features/account/passwordLength.ts` | the subject — three exports, 25 lines, **read-only** |
| `…/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:394-410` | task 43's totality case, whose comment draws the boundary this task must respect |
| `…/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:295-305`, `:307-332` | the two existing through-the-DOM unit claims (12 astral = 24 units, 128 astral = 256) |
| `…/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:211` | task 45's rows, including **11 astral = 22 units**, the row where counting units falls silent |

**Do not modify:** `passwordLength.ts` · `RegisterScreen.tsx` · `catalogue.ts` · `spec/**` · `contracts/**` ·
the three suites above (a change to any of them is a deviation to be logged, not a fix to be made).

## Dependencies

| | |
|---|---|
| **Module under test** | `countCodePoints(value: string): number` → `[...value].length`; `ADVISORY_MINIMUM_CODE_POINTS = 12`; `ADVISORY_MAXIMUM_CODE_POINTS = 128` |
| **Runtime** | Vitest 5 on jsdom. **No environment to arrange** — no DOM, no globals, no filesystem, unless a case chooses to scan source. |
| **Language level** | `target`/`lib` ES2022. String iteration by code point is ES2015; `String.prototype.codePointAt`, `String.fromCodePoint` and `/u` regexes are all available. `Intl.Segmenter` is ES2022 in V8 and **unused anywhere in `src`**. |
| **Contracts** | `contracts/api/auth.yaml` — `password` is a **bare string with no `maxLength` and no `minLength`**, which is *why* R1 is advisory by construction. Relevant as context, not as a dependency. |
| **Precedent for a non-rendering suite** | `auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` asserts `pkce`'s helpers directly — but stubs `crypto`. There is no precedent for a suite with nothing to arrange at all. |

## Acceptance Criteria

| AC | claim | R |
|---|---|---|
| **AC1** | `countCodePoints` counts **code points**, demonstrated where the two units disagree: an astral character is **one** to the counter and **two** to `String.length`, at a size that matters to R1's bounds. | R1 |
| **AC2** | The two units are shown **disagreeing in both directions relative to the bounds** — a password `.length` would push *into* range at the lower bound, and one it would push *out* at the upper. This is the inversion T44 and T45 measured; a suite that proves the unit only at one end re-proves half of what is already covered. | R1 |
| **AC3** | The count is **exact, not approximate**: for a set of strings whose code-point count is known independently of the implementation, the function returns that number. "Independently" is the load-bearing word — a case that computes the expectation with `[...s].length` asserts nothing. | R1 |
| **AC4** | The function is **total**: every string returns a number and nothing throws — including the empty string, a lone surrogate and a very long value. (Task 43's D16 case already holds this; this AC **cites** it rather than duplicating it, unless phase 2 finds it incomplete.) | R1 |
| **AC5** | The counting unit is **the one R1 names**, pinned against the two neighbours it is most often confused with: UTF-16 units (smaller than a code point for astral input) and grapheme clusters (**larger** — a flag or a family emoji is several code points and one perceived character). The second is unmeasured anywhere in this package. | R1 |
| **AC6** | The function has **no dependencies and no side effects**: it reads no storage, no DOM, no clock and no global, so its answer depends on its argument alone. | R1, L3 |
| **AC7** | The module's **public surface is what the consumers use** — the two constants are 12 and 128, and nothing else is exported. (Scope question — see Open Questions.) | R1 |

## Tests required

| | |
|---|---|
| **Named test** | `passwordLengthCountsCodePoints` — a **new** suite, `package.md:183` |
| **Boundaries** | the two bounds **in both units**: 12 code points as 12 units (ASCII) and as 24 (astral); 128 as 128 and as 256; and the two rows where the units disagree *across* a bound — 11 astral (11 code points, **22** units: in range by units, out by code points) and 128 astral (128 code points, **256** units: in range by code points, out by units) |
| **Exactness** | expectations stated as literals or built by a method that is not the implementation — `String.fromCodePoint`, a counted literal, or `codePointAt` walking — never `[...s].length` |
| **Totality** | empty string, lone high surrogate, lone low surrogate, a surrogate pair split across a concatenation, a long value |
| **The third unit** | at least one case naming grapheme clusters explicitly: `'🇬🇧'` is **2** code points, `'👩‍👩‍👧'` is **5** (three people and two zero-width joiners), and R1 counts those as 2 and 5 — not 1 |
| **Purity** | one case asserting the function touches no environment, with the shape this package has learnt to use (a **source** scan, because a behavioural assertion about an absent side effect is the vacuous shape T43 found in T42's storage spy) |
| **Harness** | a fresh copy of T45's, re-aimed. The obvious row is `[...value]` → `value`; phase 2 must find the rows that are **not** obvious, because a function with no branches offers few. |

## Open Questions

| # | question | blocker? |
|---|---|---|
| **OQ-T46-1** | **Does this task own the constants, or only the counting?** The statement says "length is counted in Unicode code points" and names no number. Three existing cases already assert `ADVISORY_MINIMUM_CODE_POINTS === 12` and `…MAXIMUM… === 128`. AC7 as drafted would be a fourth. **Not a blocker** — phase 2 can scope it either way, and the package's own precedent (T173's review on duplicate instruments) argues for citing rather than restating. |
| **OQ-T46-2** | **Is the count normalisation-sensitive, and does the owner mind?** `'é'` is one code point composed (U+00E9) and two decomposed (U+0065 U+0301), so the same typed password can count 1 or 2 depending on the visitor's keyboard and operating system — and the client and the server may disagree if either normalises. Nothing in `src` calls `.normalize()`, and `callbackWithMismatchedStateIsDiscarded.test.tsx:1075` actively forbids it in the callback path. **Not a blocker for the suite** — the behaviour is unambiguous (no normalisation happens) and can be *pinned* as it stands. It is a genuine question for the owner about whether R1 means to be normalisation-sensitive. |
| **OQ-T46-3** | **What can a four-token function's harness measure beyond the one obvious mutant?** `[...value].length` has no branches, no state and no environment. `value.length` is the mutant the task exists for; `[...value].length - 1`, `Array.from(value).length` (**equivalent, and that is the point**) and a widened constant are candidates, but several are either trivial or not falsifiable. **Not a blocker** — it is phase 2's and phase 3's central design problem, and naming it now is what stops phase 6 discovering it. |

**No blockers.** Phase 2 may proceed.

**Phase 1 complete.**
