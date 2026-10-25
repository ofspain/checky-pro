# frontend · T14 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact, the implementation, `spec/frontend/*` |
| **Produces** | `artifacts/12-specification-verification.md` |

## Phase 11 gaps (Kimi), resolved before verification

| Gap | Decision | Change |
|---|---|---|
| 1 the block-comment stripper has the same hole the line-comment one did | **ACCEPTED — the sharpest gap in the review** | Verified first: `const x = '/*'; localStorage.setItem('k','v');` with a real doc comment later strips to `const x = '` — the storage call is eaten. This is exactly S1 in the other half, and **neither regex form can be made safe**. `codeWithoutComments` is now a scanner tracking string and template state (`:85`). Its residual limit is in the code: an unescaped comment delimiter inside a regex character class still fools it, which needs real parse context and is unreachable here. |
| 2 the runtime storage spy only watched `setItem` | **ACCEPTED, with the asymmetry named** | `:404` now spies every `Storage.prototype` method and every function on `console`. **An indexed assignment onto a Storage object is a property set, not a method call, so no method spy can see it** — stated in the test: for that form the source scan is authoritative and the spies are the second opinion. |
| 3 derivation untested at the invalid upper bound | **ACCEPTED** | `:213` adds `deriveCodeChallenge('a'.repeat(129))` rejecting. The function advertises itself as self-validating, so it may not lean on a caller having consulted the validator. |
| 4 no 128-character verifier mixing unreserved characters | **ACCEPTED** | `:289` adds `'a'.repeat(126) + '.~'` — the upper bound **and** the two characters base64url omits, in one input. |
| 5 the `console` spy covered five methods | **ACCEPTED, merged with gap 2** | Every function-valued property of `console`, discovered rather than listed, with a floor assertion that the spy surface is non-trivial. |
| 6 no negative control for the forbidden-name scanner | **ACCEPTED — and it pays for itself, measurably** | `:341` feeds the same detection logic a synthetic source for each of the fourteen names, plus all four comment-delimiter-inside-a-literal forms. See below. |

Six accepted, none rejected. **48 → 67 cases.**

## Gap 6 turned a guard that could not defend itself into one that can

Every forbidden-name row asserted an **absence** over a file containing none of those names — so a broken pattern,
or a broken stripper, was silently green. The same vacuity as T12's AC7 and T13's AC8, found by the reviewer this
time. The consequence is measurable:

| Mutant | Phase 10 | Phase 11 |
|---|---|---|
| restore the broken comment stripper | **SURVIVED** (M15) — closed only indirectly, by pairing it with a real leak (M16/M17) | **CAUGHT by 3 cases** (M18) |

```
M18: CAUGHT | Tests 3 failed | 64 passed (67)
        - is not blinded by a // inside a string literal, same line (phase 7, S1)
        - is not blinded by a block delimiter inside a string literal (Kimi 11.1)
        - is not blinded by a template literal holding //
```

And M16 — a real `localStorage` write hidden behind a `//` in a string — is now caught by **two** cases rather than
one, because the broadened runtime spy catches it independently of the scan.

## Mutation runs — 18 mutants across phases 10 and 11, 15 caught

| # | Mutant | File | Result | Caught by |
|---|---|---|---|---|
| M1 | validator narrowed to base64url's 64 characters | `pkce.ts` | **CAUGHT** (2) | the `.`/`~` case and the 128-character case — **both from review** |
| M2 | 24 entropy bytes instead of 32 | `pkce.ts` | **CAUGHT** (11) | the CSPRNG spy, the 4-bit tail, length, uniqueness |
| M3 | standard base64: `+`, `/`, padding kept | `pkce.ts` | **CAUGHT** (13) | AC4 and twelve others |
| M4 | base64url with padding left on | `pkce.ts` | **CAUGHT** (11) | AC4, length |
| M5 | the method constant becomes `plain` | `pkce.ts` | **CAUGHT** (1) | exactly the method case |
| M6 | `plain`: the challenge *is* the verifier | `pkce.ts` | **CAUGHT** (3) | Appendix B, challenge≠verifier, the 128-character case |
| M7 | SHA-1 instead of SHA-256 | `pkce.ts` | **CAUGHT** (2) | Appendix B, the 128-character case |
| M8 | derivation hashes anything handed to it | `pkce.ts` | **CAUGHT** (1) | exactly AC9 |
| M9 | off-by-one: a legal 43-character verifier rejected | `pkce.ts` | **CAUGHT** (13) | the boundary table |
| M10 | upper bound widened to 1024 | `pkce.ts` | **CAUGHT** (1) | exactly the 129-character row |
| M11 | `Math.random` in place of the CSPRNG | `pkce.ts` | **CAUGHT** (3) | the spy, both halves of the scan |
| M12 | one shared buffer reused across calls | `pkce.ts` | **SURVIVED** | **equivalent mutant** — 5000/5000 distinct, measured |
| M13 | one entropy bit dropped per byte | `pkce.ts` | **CAUGHT** (2) | Appendix B, **and the χ² bias case** |
| M14 | χ² threshold raised to 100000 | test | **SURVIVED** (guard weakening) | closed by **M13** |
| M15 | the line-comment hole restored | test | *superseded* | the construct no longer exists; **M18** is its successor |
| M16 | a real leak behind `//` inside a string | `pkce.ts` | **CAUGHT** (2) | the scan **and** the runtime spy |
| M17 | M16 **and** the broken stripper together | both | **SURVIVED — by design** | *this is the proof* the phase-9 fix was load-bearing |
| M18 | the regex stripper restored, both holes back | test | **CAUGHT** (3) | the phase-11 negative control |

**15 of 18 caught.** The three that did not are each explained, not excused: **M12** changes no behaviour, **M14**
is a guard weakening whose guarded property is pinned by M13, and **M17** surviving *is the experiment* — it
demonstrates that the version shipped in phase 6 would have let a verifier reach `localStorage` with no case
failing.

## A process failure worth recording

Running M18 before committing the phase-11 edits **destroyed them**: the harness reverts with `git checkout --`,
and uncommitted work goes with it. 67 cases dropped back to 48 and had to be re-applied. The standing rule —
*commit tests before mutating* (T09's lesson, in memory) — is mine and I broke it. Re-applied, committed, and only
then re-mutated; the second run shows a clean tree afterwards. No work was lost permanently, and the numbers in
this artifact are from the committed state.

## Traceability matrix

| Requirement / decision | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R6** (`requirements.md:21-22`) authorization code **with PKCE** | Yes, the primitive | `pkce.ts:60-96` | `:166`, `:224`, `:243` | No | No — the no-password-field half is T16's |
| **L1** (`design.md:8-10`) public client, PKCE, no secret in the bundle | Yes | the module holds no secret and reads no config | `:330`, `:389`, T13's bundle scan | No | No |
| **RFC 7636 §4.1** 43–128 unreserved | Yes | `pkce.ts:23`, `:50-57` | `:267` (11 rows), `:289` | No | No |
| **RFC 7636 §4.2** S256, base64url unpadded | Yes | `pkce.ts:79-88` | `:166` (Appendix B), `:170`, `:204` | No | No |
| **L16** (`design.md:58-60`) the sessionStorage carve-out | Yes, as a **prohibition** | the module stores nothing | `:330`-`:420` (scan + runtime spies) | No | **Declared** — T14 stores nothing; the task writing the L16 set as a unit owns persistence |
| **L3** (`design.md:13`) nothing sensitive persisted | Yes | no storage, no log, no URL | `:341`, `:404` | No | No |
| **L13** (`design.md:50-51`) confirmed stack, no OIDC library | Yes | hand-written, no dependency added | `package.json` untouched | No | No |
| **L12** phase additivity | Yes | two new files, nothing modified | suite 479/481 | No | No |
| `agents.md:79-81` never in a URL, storage, log | Yes | fourteen forbidden names, both guards | `:341`, `:404` | No | No |
| **D-10** the method is a literal type | Yes | `pkce.ts:17` | `:192` (M5) | No | No |
| **D-11** the locked export shape | Yes | `pkce.ts` exports exactly as frozen | `:197`, `:256` | No | No |

## Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| AC1 43–128, unreserved characters | **PASS** | `:224`, `:231`, `:262`, `:267` (11 rows), `:289` (M1, M9, M10) |
| AC2 CSPRNG, all distinct | **PASS** | `:237`, `:243`, `:256`, `:330` (M11, M2) |
| AC3 S256 per RFC 7636 Appendix B | **PASS** | `:166`, `:197`, `:204` (M6, M7, M13) |
| AC4 base64url, unpadded | **PASS** | `:170` (M3, M4) |
| AC5 genuinely S256, not `plain` | **PASS** | `:176`, `:181`, `:192` (M5, M6) |
| AC6 no leak | **PASS** | `:341` (18 rows), `:384`, `:404` (**M16**, M18) |
| AC7 no bias | **PASS** | `:300`, `:313`, `:318` (M13, M2) |
| AC8 suite delta, no T01–T13 edit | **PASS with the two known environmental failures** | 414 → **481**, delta exactly **67** |
| AC9 invalid verifier rejected | **PASS** | `:213` (42, **129**, `+`, empty) (M8) |
| AC10 browser-safe | **PASS** | `:389` — *no mutant; see below* |

### AC8, measured

| | |
|---|---|
| Baseline before T14 | **412/414** at the end of T13 |
| `pkceVerifierMeetsRfc7636.test.ts` | **67 cases** (44 phase 6, +5 phase 9, −1 merge, +19 phase 11) |
| `vitest run` now | **479/481** — 13 files, 1 failed |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |
| 6 consecutive `src/auth` reruns | identical |

`414 + 67 = 481`, the delta is exactly the cases added, and the two failures are the same T07 pair
(`lintRejectsHandWrittenBackendFetch` AC4 — no `oxlint` summary line on this host; AC2 — `spawn pnpm ENOENT`).
**Files changed: two, both new, both under `src/auth/`.** No T01–T13 test, no config, no `package.json`, no `spec/`.

## Coverage, honestly

Enumerated, not measured (`@vitest/coverage-v8` is not a workspace dependency). What this suite does **not** prove:

1. **AC10 has no mutant.** Introducing `Buffer` is caught by the scan, but the property it guards — the module
   working under Vitest and failing in a real browser — needs a browser, and T14 ships no e2e test. The scan is a
   proxy for something only Playwright could check.
2. **The χ² case validates no arithmetic written here**, because `btoa` does the mapping. It is a regression guard
   against a future reimplementation plus a record of the entropy achieved. M13 shows it has real reach; M14 shows
   it cannot defend itself. Said in the test file, not only here.
3. **An indexed assignment onto a Storage object is invisible to every runtime spy.** The source scan is the only
   guard against that form, and the test says so rather than implying symmetry between the two.
4. **The comment scanner is not a parser.** An unescaped comment delimiter inside a regex character class would
   still fool it. Unreachable in this module and stated in the code; a real fix needs parse context.
5. **Nothing here tests the authorize request.** "Its S256 challenge is sent on authorize" is half the task line and
   belongs to **T15** by `package.md` §8's mapping. If T15 fails to send `PKCE_CODE_CHALLENGE_METHOD`, nothing in
   T14 notices — the constant's literal type is the only thing carrying the obligation across that boundary.

## Verdict

**PASS.** R6's PKCE half and L1 are implemented as a hand-written primitive whose correctness rests on RFC 7636's
own published test vector, with 67 cases and **15 of 18 mutants caught**, every survivor explained by measurement
rather than assertion. One declared deviation (L16): T14 stores nothing, so the carve-out is enforced here as a
prohibition and persistence belongs to the task that writes `state`, `nonce`, `code_verifier` and the return target
as a unit. **The owner must settle OQ-1 before T15 or T16 consumes a §4c key.**
