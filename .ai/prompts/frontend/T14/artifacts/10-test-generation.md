# frontend · T14 · Phase 10 — Test Generation, Mutation & Coverage

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

## Where the suite stands

| | |
|---|---|
| `pkceVerifierMeetsRfc7636.test.ts` | **48 cases** (44 in phase 6, +5 in phase 9, −1 from merging two forbidden-name entries) |
| `vitest run` (whole suite) | **460/462** — 414 before the task, +48; the same two environmental T07 failures |
| `tsc --noEmit`, `oxlint` | clean |
| 8 consecutive reruns of `src/auth` | 48/48 each |

No case was added in this phase. The five phase-9 additions came from review and earn their place below.

## The mutation pass: 17 mutants, 14 caught

Tests committed before the first mutant (T09's lesson). Each applied to real source, run, reverted with
`git checkout --`.

| # | Mutant | File | Result | Caught by |
|---|---|---|---|---|
| **M1** | validator uses base64url's 64 characters, not the RFC's 66 | `pkce.ts` | **CAUGHT** (2) | the `.`/`~` case and the 128-character case — **both came from review** |
| **M2** | 24 entropy bytes instead of 32 | `pkce.ts` | **CAUGHT** (11) | the CSPRNG spy, the 4-bit tail, length, uniqueness |
| **M3** | standard base64: keeps `+`, `/`, padding | `pkce.ts` | **CAUGHT** (13) | thirteen, incl. AC4 and the unreserved-character case |
| **M4** | base64url but padding left on | `pkce.ts` | **CAUGHT** (11) | AC4, length, the unreserved-character case |
| **M5** | the method constant becomes `plain` | `pkce.ts` | **CAUGHT** (1) | exactly the method case |
| **M6** | `plain`: the challenge *is* the verifier | `pkce.ts` | **CAUGHT** (3) | Appendix B, challenge≠verifier, the 128-character case |
| **M7** | SHA-1 instead of SHA-256 | `pkce.ts` | **CAUGHT** (2) | Appendix B, and the 128-character case |
| **M8** | derivation hashes anything handed to it | `pkce.ts` | **CAUGHT** (1) | exactly AC9 |
| **M9** | off-by-one: a legal 43-character verifier rejected | `pkce.ts` | **CAUGHT** (13) | the boundary table and much else |
| **M10** | upper bound widened to 1024 | `pkce.ts` | **CAUGHT** (1) | exactly the 129-character row |
| **M11** | `Math.random` in place of the CSPRNG | `pkce.ts` | **CAUGHT** (3) | the spy, the positive scan, the forbidden-name scan |
| **M12** | one shared buffer reused across calls | `pkce.ts` | **SURVIVED** | **equivalent mutant — see below** |
| **M13** | one entropy bit dropped per byte (`& 0x7f`) | `pkce.ts` | **CAUGHT** (2) | Appendix B, **and the χ² bias case** |
| **M14** | χ² threshold raised to 100000 | test | **SURVIVED** (guard weakening) | — closed by M13 |
| **M15** | the S1 comment-stripper hole restored | test | **SURVIVED** (guard weakening) | — closed by M16/M17 |
| **M16** | a real leak hidden behind `//` inside a string, same line | `pkce.ts` | **CAUGHT** (1) | `never names localStorage` |
| **M17** | M16 **and** M15 together | both | **SURVIVED** | — *this is the proof*, below |

**14 of 17 caught. One survivor is an equivalent mutant and two are guard weakenings**, both of which are closed by
mutating the thing they guard.

## M15 and M16/M17: the phase-9 fix is load-bearing, demonstrated

A guard weakening cannot fail anything by construction, so following T11's precedent the meaningful mutant goes on
the guarded property. Here that is "a forbidden name cannot hide from the scan":

| Run | Stripper | Leak present | Result |
|---|---|---|---|
| **M16** | the phase-9 fix | yes | **CAUGHT** — `never names localStorage` |
| **M17** | M15's broken version | yes | **SURVIVED** |

The leak is a single line — `const note = 'a//b'; localStorage.setItem('pkce', note);` — placed inside
`generatePkcePair`. **The same real leak is caught by the fixed stripper and invisible to the broken one.** So S1
was not a theoretical tidy-up: the version shipped in phase 6 would have let a verifier be written to
`localStorage` without a single case failing. That is the strongest result in this pass.

## M14 is closed by M13

The χ² threshold's job is to notice a loss of entropy. **M13 does exactly that** — masking one bit per byte with
`& 0x7f` fails `shows no bias across the uniform prefix` (and Appendix B). So the bias guard is known to work
rather than merely known to pass, which is what the frozen brief's D-6 claimed and this measures.

## M12 survived because it changes nothing — not because the suite is blind

The mutant replaces the per-call `new Uint8Array(32)` with one module-level buffer reused on every call. Measured
over 5,000 generations:

```
shared-buffer version: distinct verifiers 5000 of 5000
fresh-buffer version : distinct verifiers 5000 of 5000
```

`getRandomValues` refills the buffer and `base64UrlFromBytes` reads it **synchronously, with no `await` between**,
so nothing can observe it mid-flight and the output is identical. **It is an equivalent mutant**, and no test can
distinguish it.

It is worth keeping on the record for the reason it *would* matter: if `generateCodeVerifier` ever became async
between the fill and the encode, or if the buffer were handed out rather than read, the shared buffer would become
a real defect — two concurrent callers could receive the same verifier. The per-call allocation is cheap insurance
against a change nobody would otherwise connect to it. Reported as survived rather than dressed up as caught,
following T11 and T13.

## What each acceptance criterion rests on

| AC | Pinned by | Mutant proof |
|---|---|---|
| AC1 43–128, unreserved characters | the validator plus eleven unreachable inputs | M1, M9, M10 |
| AC2 CSPRNG, all distinct | the spy at the call site, uniqueness over 2000 | M11, M2 |
| AC3 S256 per Appendix B | the published vector | M6, M7, M13 |
| AC4 base64url, unpadded | the character-class assertion | M3, M4 |
| AC5 genuinely S256, not `plain` | challenge≠verifier, avalanche, the literal-typed constant | M5, M6 |
| AC6 no leak | the source scan **and** the runtime spies | **M16** |
| AC7 no bias | χ² < 139, all-64-appear, the 4-bit tail | M13, M2 |
| AC8 suite delta | 414 → 462, exactly +48 | — |
| AC9 invalid verifier rejected | three rejection cases | M8 |
| AC10 browser-safe | no Node global, `btoa` present | — (see below) |

## Coverage, honestly

Enumerated, not measured (`@vitest/coverage-v8` is not a workspace dependency). What this suite does **not** prove:

1. **AC10 has no mutant.** Introducing `Buffer` into the module would be caught by the forbidden-name scan, but the
   *consequence* it guards against — the module working under Vitest and failing in a real browser — cannot be
   demonstrated here, because the suite has no browser. The scan is a proxy for a property only Playwright could
   test, and T14 ships no e2e test. Stated rather than implied.
2. **The χ² case validates no arithmetic written here**, because `btoa` does the mapping. It is a regression guard
   against a future reimplementation plus a record of the entropy achieved — said in the test file itself, not only
   in an artifact. M13 shows it has real reach; M14 shows it cannot defend itself.
3. **One sample, one host.** AC7's numbers were derived in phase 4 against Node's `randomBytes` and confirmed in
   phase 6 against jsdom's `getRandomValues` (15 runs) and again here (8 runs). They remain properties of a CSPRNG,
   not a proof about every platform.
4. **Nothing here tests the authorize request.** "Its S256 challenge is sent on authorize" is half of T14's task
   line and belongs to **T15** by `package.md` §8's mapping (phase 1, OQ-1). If T15 does not send
   `PKCE_CODE_CHALLENGE_METHOD`, nothing in this task notices — the constant's literal type is the only mechanism
   carrying the obligation across the boundary.
