# frontend · T14 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code written in this phase. Every file below traces to the frozen brief's **Files to Create**; nothing else is
added.

## Files to create

| File | Content |
|---|---|
| `frontend/src/auth/pkce.ts` | the module — four functions, three constants, one type (D-11) |
| `frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts` | the named test (`package.md:173`) |

**First file under `src/auth/`.** No `index.ts`, no barrel: no prior module in this repository has one
(`src/pwa/`, `src/api/` both export directly), and T15 imports from the module path.

## Files to modify

**None.** Confirmed against the brief's *Files NOT to Modify*: no route, no config, no `package.json`, no T01–T13
test, no `spec/`.

## Public API (signatures, exactly as D-11 locks them)

```ts
export const PKCE_CODE_CHALLENGE_METHOD = 'S256' as const;
export const PKCE_VERIFIER_MIN_LENGTH = 43;
export const PKCE_VERIFIER_MAX_LENGTH = 128;

export type PkcePair = {
  verifier: string;
  challenge: string;
  method: typeof PKCE_CODE_CHALLENGE_METHOD;
};

/** True when `candidate` is a well-formed RFC 7636 §4.1 verifier. Never throws. */
export function isValidCodeVerifier(candidate: string): boolean;

/** 43 characters, base64url of 32 CSPRNG bytes (D-7). */
export function generateCodeVerifier(): string;

/** base64url(SHA-256(ASCII(verifier))), unpadded. Rejects an invalid verifier rather than hashing it (D-3). */
export function deriveCodeChallenge(verifier: string): Promise<string>;

/** A fresh verifier with its challenge and the method name, for the authorize request T15 assembles. */
export function generatePkcePair(): Promise<PkcePair>;
```

`generateCodeVerifier` and `deriveCodeChallenge` are public **because AC3 cannot be satisfied otherwise** — the
Appendix B vector must be fed to the derivation directly, which a combined-only API forbids.

## Private helpers

| Helper | Why private |
|---|---|
| `UNRESERVED_PATTERN` | the RFC 7636 §4.1 character class, anchored, as a module constant. Private: the rule is published through `isValidCodeVerifier`, not as a regex T15 could re-apply differently. |
| `base64UrlFromBytes(bytes: Uint8Array): string` | `btoa` + `+`→`-`, `/`→`_`, strip `=` (D-4). One implementation, used by both the verifier and the challenge, so the two cannot disagree. |

**No lookup table** — D-4 rejects a hand-rolled encoder, so there is none to get wrong.

## Entities / repositories / services used

**None.** No persistence, no HTTP, no DOM, no storage, no configuration. The module's only outside contact is
`crypto.getRandomValues` and `crypto.subtle.digest`. State changes: **none** (frozen brief).

## Tests required

One file, `pkceVerifierMeetsRfc7636.test.ts`. Cases grouped by the criterion each serves:

| # | Case | AC |
|---|---|---|
| 1 | the environment exposes `crypto.getRandomValues` and `crypto.subtle.digest`, asserted up front with a readable message | D-5 |
| 2 | **RFC 7636 Appendix B**: `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` → `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM` | AC3 |
| 3 | the challenge contains no `=`, `+` or `/` | AC4 |
| 4 | a one-character change in the verifier changes the challenge; the challenge never equals the verifier | AC5 |
| 5 | `PKCE_CODE_CHALLENGE_METHOD` is exactly `'S256'`, and `generatePkcePair().method` is it | AC5, D-10 |
| 6 | a generated verifier: length 43, within `[MIN, MAX]`, every character unreserved, `isValidCodeVerifier` accepts it | AC1 |
| 7 | **the validator against invalid input** — lengths 42 and 129 rejected; 43 and 128 accepted; `+`, `/`, `=`, a space, `é`, an empty string rejected | AC1 |
| 8 | **the validator accepts `.` and `~`** — a hand-crafted 43-character verifier containing both | AC1, **D-2** |
| 9 | 2000 verifiers: all distinct, all valid | AC1, AC2 |
| 10 | **χ² < 139** over positions 0–41 of those 2000 | AC7 (1) |
| 11 | all 64 base64url characters appear in that sample | AC7 (2) |
| 12 | position 42 only ever takes one of the 16 values 4 bits allow | AC7 (3) |
| 13 | `deriveCodeChallenge` **rejects** a 42-character verifier, and one containing `+` | **AC9**, D-3 |
| 14 | source scan: the module names `crypto.getRandomValues`, and none of the fifteen forbidden names (D-9) | AC2, AC6, AC10 |
| 15 | `generatePkcePair()` returns a pair whose challenge is `deriveCodeChallenge(pair.verifier)` — the pair is self-consistent | AC3, D-11 |

Cases 9–12 share **one** 2000-verifier sample built in a `beforeAll`, so the suite generates it once rather than
four times.

**No case covers `null`/`undefined` input** — deliberate, per D-8; `tsc --noEmit` is the gate.

## Execution order

The repository's analogue of "schema first" is **the published rule first, then what consumes it**:

1. **`pkce.ts` constants and `UNRESERVED_PATTERN`** — the RFC 7636 §4.1 rule, with its citation. Nothing else can
   be written without it.
2. **`isValidCodeVerifier`** — the published rule as a predicate. It is what cases 7 and 8 test directly, and what
   `deriveCodeChallenge` consults.
3. **`base64UrlFromBytes`** — the one encoder both halves use.
4. **`generateCodeVerifier`** — 32 bytes via `crypto.getRandomValues`, through the encoder.
5. **`deriveCodeChallenge`** — validate, encode ASCII, `crypto.subtle.digest`, through the same encoder.
6. **`generatePkcePair`** — compose 4 and 5; decides nothing of its own.
7. **The test file**, in the case order above: case 2 (Appendix B) first, because it is the one case a wrong hash
   cannot pass and therefore the fastest signal that step 5 is right.
8. **Gates**: `vitest run src/auth`, then `tsc --noEmit`, then `oxlint`, then the whole suite for AC8's delta.

Steps 1–6 are one file and will be written in that order within it, so the file reads rule-first the way
`apiRequestPolicy.ts` and `navigationDenylist.ts` do.

## Risks carried into phase 6

1. **The χ² threshold and the 16-value tail are phase-4 measurements, not guesses** — but they were measured with
   Node's `randomBytes`, and phase 6 runs under jsdom's `crypto.getRandomValues`. Both are CSPRNGs and base64url is
   deterministic, so the distribution cannot differ; **phase 6 must still confirm case 10 passes repeatedly** rather
   than assume the transfer.
2. **`btoa` on a `Uint8Array`** needs a binary string first (`String.fromCharCode`). Trivial at 32 bytes, but the
   spread form is a stack risk at large sizes — not reachable here, worth a comment so nobody copies it into a
   file-sized encoder.
3. **ASCII encoding of the verifier** — RFC 7636 §4.2 specifies ASCII. `TextEncoder` emits UTF-8, which is
   identical for an unreserved-set string, so it is correct *because* the character set is restricted. Worth a
   comment, since it would silently break for a non-ASCII verifier — which `isValidCodeVerifier` rejects, closing
   the loop.

## Open Questions

**No blockers.** OQ-1 (T01 `:130` vs §4c) is carried for the owner and does not touch this task: T14 reads no
configuration key.
