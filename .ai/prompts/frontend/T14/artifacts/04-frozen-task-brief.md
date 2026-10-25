# frontend · T14 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` — **frozen; phases 5–13 may use nothing else** |

The phase 2 brief stands, amended by the decisions below. Where a decision contradicts it, the decision wins.
Three were settled by running code rather than by argument — those carry their measurements.

## Disposition of Kimi's phase 3 findings

| # | Finding | Disposition |
|---|---|---|
| 1 | host dependencies missing, suite cannot run — **High** | **Premise rejected with evidence** — D-0. Third occurrence of this same claim. |
| 2 | validator's acceptance of `.` and `~` untested | **Accepted** — D-2 |
| 3 | challenge derivation's rejection of an invalid verifier not a required test | **Accepted** — D-3 |
| 4 | base64url implementation unspecified; `Buffer` is Node-only | **Accepted** — D-4 |
| 5 | `crypto.subtle` availability not guaranteed | **Accepted** — D-5 |
| 6 | AC7's tolerance not stated | **Gap accepted; the proposed fix rejected by measurement** — D-6. **The suggested test fails 40 runs out of 40.** |
| 7 | the `412/414` baseline is unverified | **Premise rejected; recommendation accepted** — D-1 |
| 8 | non-string input to the validator | **Accepted** — D-8 |
| 9 | source scan omits `https`, `URL`, `location` | **Accepted** — D-9 |
| 10 | method constant not pinned as a literal type | **Accepted** — D-10 |
| 11 | the export shape T15 consumes is unspecified | **Accepted** — D-11 |
| 12 | OQ-1 will block T15/T16 | **Accepted as a tracking note** — D-12 |

Ten accepted, two premises rejected, and one accepted gap whose recommended fix would have produced a permanently
red test.

## Decisions

### D-0 — Finding 1's premise is false, for the third time.

The review states `frontend/node_modules` is incomplete, `vite-plugin-pwa` is missing, and `vitest` fails with
`ERR_MODULE_NOT_FOUND`, making phase 6 and the baseline uncheckable. **T12 resolved this in its phase 6**
(`ab9ec5c9`) with `npx -y pnpm@12.14.0`. The same claim was made and rejected in T13's phase 3 (its D-0). Measured
again in this session, immediately before writing this:

```
vitest run                 →  Tests  2 failed | 412 passed (414)
tsc --noEmit               →  clean
oxlint                     →  no violations
```

**There is no blocker.** The only residual host fact is that bare `pnpm` is not on PATH, which is why T07's AC2 case
fails; unrelated to this task and unchanged since T07.

### D-1 — The baseline is a rule, not a literal.

Finding 7's premise is wrong — **412/414 was measured**, twice, at the end of T13. But its recommendation is better
practice, and T13 adopted the same form (its D-1), so AC8 becomes:

> **AC8.** Immediately before T14's own cases are judged, `vitest run` is recorded. The only failures are the two
> known environmental T07 cases (`oxlint` summary line; `spawn pnpm ENOENT`). After T14, the total rises by exactly
> the number of cases T14 adds, the same two failures remain, and no other case changes state. No T01–T13 file is
> modified.

Phase 6 records the real numbers.

### D-2 — The validator must accept `.` and `~`, and a case proves it.

Accepted as the review's sharpest finding. The generation scheme never emits those two characters, so without a
deliberate case the validator's acceptance path for them is never taken — and a validator whose character class was
written as `[A-Za-z0-9\-_]` (base64url, not the unreserved set) would pass every other case. Required test 5 gains:
a hand-crafted 43-character verifier containing both `.` and `~` is **accepted**.

This is the same shape of hole as T12's AC7 and T13's AC8, found by the reviewer this time instead of by a mutant.

### D-3 — Challenge derivation rejects an invalid verifier, and it is a required test.

Accepted. The phase 2 brief stated it under Null handling but did not require a case. Added: calling the challenge
function with a verifier of length 42, and with one containing `+`, **rejects** (a rejected promise, not a silently
derived challenge). A function that hashes anything handed to it would otherwise pass the whole suite.

### D-4 — `btoa` plus the three substitutions. `Buffer` is forbidden in the module.

Accepted, with the choice made. The module encodes with **`btoa`**, then `+`→`-`, `/`→`_`, and strips `=`.
`btoa` is a web standard, and the phase-0 probe measured it present (`btoa: "function"`). **`Buffer` is a Node
global that Vite does not polyfill for the browser**; it would work under Vitest and fail in production, which is
the worst possible failure mode, so the source scan in required test 6 forbids it by name.

**A hand-rolled lookup table is rejected** on the grounds that it is the only version of this code that can have a
bias defect at all: if we do not write a table, there is no table to get wrong. That has a consequence for AC7,
stated honestly in D-6.

### D-5 — The environment's capabilities are asserted, with a readable message.

Accepted. The test file asserts up front that `crypto.getRandomValues` and `crypto.subtle.digest` are functions, so
a future jsdom or Vitest change produces one clear failure rather than a cryptic error inside a derivation. My
phase 0 measured both present; this turns that measurement into a guard rather than an assumption.

### D-6 — AC7 is a chi-square test at 139. Kimi's proposed numbers fail 40 runs out of 40.

**The gap is real: phase 2 said "within a stated tolerance" and stated none.** The recommended fix — 1,000
verifiers, each of the 64 characters within ±10% of the mean — was tested before being adopted, and it **does not
work**:

| What was run | Result |
|---|---|
| ±10% over all 43 characters, n=1000, 40 runs | **failed 40/40**, worst deviation 18.9% |
| ±10% over the first 42 characters, n=1000, 40 runs | **failed 17/40**, worst deviation 15.7% |

The first number is not flakiness, it is a structural impossibility, and it is worth stating because it is a
property of this scheme a maintainer should know: **32 bytes is 256 bits, but 43 base64url characters carry 258, so
the final character encodes only 4 bits and can take exactly 16 of the 64 values** — measured as
`048AEIMQUYcgkosw` over 20,000 verifiers. Any uniformity assertion covering position 42 is permanently red. The
second number shows that even corrected, ±10% at n=1000 is ≈2.6σ per character across 64 characters, i.e. flaky by
design.

So AC7 is specified as a **chi-square goodness-of-fit test**, which is both the correct tool and far more sensitive.
Measured, n=2000 verifiers over positions 0–41:

| Generator | χ² range observed |
|---|---|
| this scheme (base64url, 64 categories, 63 df) | **39.7 – 97.8** over 200 runs |
| `byte % 66` over the full unreserved set — the defect AC7 exists to catch | **540 – 744** over 20 runs |

**Threshold: χ² < 139** (p ≈ 1e-6 at 63 df). It sits 1.4× above the honest maximum and 3.9× below the defect's
minimum, so the test is neither flaky nor blind. For comparison, a ±20% per-character tolerance would also have
been reliable (0/200 failures) but separates poorly: `byte % 66` under-draws 8 of its 66 characters by **22.7%**,
barely outside the bound.

AC7 therefore has three parts:

1. **χ² < 139** over positions 0–41 of 2000 verifiers (bias);
2. **all 64 base64url characters appear at least once** in that sample — 0 samples out of 200 were missing any, so
   this is deterministic in practice, and it is what catches a duplicated or missing entry if anyone ever does
   hand-roll the encoder;
3. **position 42 only ever takes one of the 16 values** 4 bits allow — pins the structural property above, and
   fails if the verifier's length or entropy is ever changed without thought.

**And the honest limit, stated in the brief rather than discovered later:** because D-4 delegates the mapping to
`btoa`, **AC7 does not validate arithmetic we wrote** — there is none. It is a regression guard against a future
reimplementation that introduces bias, plus documentation of the entropy actually achieved. That is worth having and
is not the same as proving our encoder correct. (This is T13's M12 lesson applied before the fact: do not let a
guard imply more than it buys.)

### D-7 — Generation scheme confirmed: 32 random bytes, base64url, 43 characters.

Unchanged from phase 2, and the review did not contest it. Bias-free by construction (64 divides 256), ~256 bits,
and the same shape as the auth service's own reference helper
(`SasLoginIntegrationTest.java:649-662`). The validator still accepts the full legal **43–128** range, because it
validates any verifier — including one from a future scheme — not only our own output.

### D-8 — The validator takes `string`; non-string input is TypeScript's problem.

Accepted as the review framed it. The signature is `(candidate: string) => boolean`, it never throws, and **no case
covers `null`, `undefined` or a number** — the module is authored in TypeScript, the public API is typed, and
`tsc --noEmit` is a gate on every phase. Documented here so a later reviewer does not read the absence as an
oversight.

### D-9 — The source scan is broadened.

Accepted. Required test 6 forbids the module naming any of: `Math.random`, `Date.now`, `localStorage`,
`sessionStorage`, `document.cookie`, `console`, `Buffer` (D-4), `http`, `https`, `URL`, `location`, `history`,
`encodeURIComponent`, `fetch`, `XMLHttpRequest`. **The verifier must never enter a URL, so any URL-construction
primitive in this module is a defect regardless of intent** — and T14 builds no URL at all (that is T15's), so none
of these has a legitimate use here. `sessionStorage` is on the list because T14 stores nothing: L16 is a
prohibition in this module, not an instruction.

### D-10 — The method constant is a literal type.

Accepted: `export const PKCE_CODE_CHALLENGE_METHOD = 'S256' as const;` and the derivation's return type includes
`method: typeof PKCE_CODE_CHALLENGE_METHOD`. T15 cannot then pass `'s256'`, `'plain'` or a widened `string`, and the
compiler enforces what the backend will not — `requireProofKey(true)` makes a challenge mandatory but
Spring Authorization Server accepts `plain` and has no switch to forbid it (phase 0 §5).

### D-11 — The export shape is locked now.

Accepted, to spare T15 a rename:

```ts
export const PKCE_CODE_CHALLENGE_METHOD = 'S256' as const;
export const PKCE_VERIFIER_MIN_LENGTH = 43;
export const PKCE_VERIFIER_MAX_LENGTH = 128;

export type PkcePair = {
  verifier: string;
  challenge: string;
  method: typeof PKCE_CODE_CHALLENGE_METHOD;
};

export function isValidCodeVerifier(candidate: string): boolean;
export function generateCodeVerifier(): string;
export function deriveCodeChallenge(verifier: string): Promise<string>;
export function generatePkcePair(): Promise<PkcePair>;
```

`generateCodeVerifier` and `deriveCodeChallenge` are exported separately because AC3 must feed the Appendix B
verifier to the derivation, which is impossible if only the combined call is public.

### D-12 — OQ-1 is tracked, not resolved.

T14 needs no configuration key and proceeds. The frozen position is T13's: **the §4c allowlist is a ceiling, not a
plan.** The owner must settle T01 `:130` versus §4c **before T15 or T16 consumes `VITE_OIDC_ISSUER`,
`VITE_OIDC_CLIENT_ID` or `VITE_OIDC_REDIRECT_URI`**. Carried into phase 13 as an owner item.

## Acceptance criteria, as frozen

AC1–AC6 stand as phase 2 wrote them, with **AC1 strengthened by D-2** (the validator accepts `.` and `~`), **AC3
and AC5 by D-10** (the method is a literal type), and **AC6 by D-9** (the broadened scan). **AC7 is replaced** by
D-6. **AC8 is replaced** by D-1. Two criteria are added:

- **AC9** — the challenge function **rejects** an invalid verifier rather than hashing it (D-3).
- **AC10** — the module runs in a browser: it names no `Buffer` and no Node-only global (D-4).

## Open Questions

- **No blockers.** Finding 1's blocker does not exist (D-0).
- **OQ-1** (T01 `:130` vs §4c's four keys) — owner, **before T15/T16**. Tracked, not resolved (D-12).
- **Recorded for the owner, no action here:** the backend accepts `code_challenge_method=plain`
  (`RegisteredClientSeeder.java:60` requires a challenge; `S256` appears nowhere in the auth service's main
  source). AC5 and D-10 exist because of this, and T15 inherits the obligation to send the constant.
