# frontend · T14 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md`, the implementation |
| **Produces** | `artifacts/07-self-review.md` |

Adversarial review of my own phase 6. Nothing is changed here; findings are resolved in phase 9 after Kimi's phase
8. Two findings were demonstrated by running code rather than argued.

## S1 — The comment-stripping scan can be made to hide a forbidden name. **Medium.**

Required test 6 strips comments before scanning, because the module's doc comments deliberately name `Buffer`,
`sessionStorage` and `plain` while explaining why each is absent (phase 6, decision 1). The stripper is
`/\/\*[\s\S]*?\*\/|\/\/.*$/gm` — and its line-comment half **eats everything after a `//` that appears inside a
string literal**. Demonstrated:

```
source  : const x = 'a//b'; localStorage.setItem('k','v');
stripped: const x = 'a
localStorage still visible? false
```

So a future edit that puts `//` in a string and a storage call later on the same line passes the scan. Not
reachable today — `pkce.ts` contains **zero line comments** and no string holding `//`, verified — but the guard is
weaker than it reads, which is exactly the class of defect this project has twice shipped and had to correct.

**Resolution for phase 9:** strip block comments, and strip a line comment only where `//` begins the trimmed line.
That has no hole for any comment style the module uses, and cannot consume code.

## S2 — AC2's positive half is satisfiable by a comment. **Medium.**

`expect(moduleSource).toContain('crypto.getRandomValues')` runs over the **raw** file, comments included.
Demonstrated:

```
module that only MENTIONS it in a doc comment and calls Math.random():
  raw-source assertion passes      : true
  stripped-code assertion passes   : false
```

**Honest mitigation:** the combined defect is still caught today, because `Math.random` is in the forbidden list and
that half *is* scanned over stripped code. So no real leak gets through. But the assertion does not mean what it
says, and a future implementation using some third non-CSPRNG source not on the forbidden list would satisfy both
halves. **Resolution for phase 9:** assert the positive over stripped code too.

## S3 — `toContain('URL')` is brittle against a rename. **Low.**

The forbidden list holds the bare string `URL`. The module's private helper is `base64UrlFromBytes` — mixed case,
so it does not match. Renaming it to `base64URLFromBytes` would fail the scan with no defect present, and
scanning case-insensitively instead would fail **today**. The bare form does catch `URL.createObjectURL` and
`URL.canParse`, which `new URL(` would miss, so it is not simply wrong.

**For phase 9 to settle:** keep the bare form and note the fragility in the test, or match `new URL(` plus
`URL.` and keep `location`/`history` bare. I lean to the latter, which keeps the reach and loses the trap.

## S4 — Not a defect, recorded so it is not mistaken for one: `.length` counts UTF-16 code units.

`isValidCodeVerifier` bounds `candidate.length`, which counts code units, while this project elsewhere cares about
the distinction (`passwordLengthCountsCodePoints` → R1). **It cannot matter here**, because `UNRESERVED_PATTERN`
rejects every non-ASCII code point, so for any string that passes validation code units and code points are
identical. The two checks are also order-independent in outcome: a 43-emoji string is 86 code units, which fails
the bound, and would fail the pattern too.

Verified separately: the anchors reject a trailing newline (`/^…$/` without `m` matches only end of input in JS) and
an embedded one.

## S5 — Derivation is never exercised on a long verifier. **Low, a real coverage gap.**

The published contract is that `isValidCodeVerifier` accepts **43–128** and `deriveCodeChallenge` derives for any
valid verifier. Every derivation case in the file uses a **43**-character verifier — Appendix B's, a generated one,
and an altered one. **A derivation that assumed 43 characters would pass the whole suite.** SHA-256 obviously
handles any length, so the risk is low, but the stated contract is untested at its upper bound.

**Resolution for phase 9:** derive a challenge for a 128-character verifier and assert it is well-formed base64url
and differs from the 43-character one's.

## S6 — `generatePkcePair` re-validates a verifier it just generated. **Info, deliberate.**

`generateCodeVerifier` → `deriveCodeChallenge` means the freshly generated value is validated on the way through.
Redundant by construction, kept on purpose: it is one regex over 43 characters, and it means there is exactly one
path to a challenge, with the validation on it. Stated so a reviewer does not read it as an oversight.

## S7 — The no-leak rule is asserted at source level only. **Low.**

AC6 is enforced by scanning the module's text. A runtime assertion would be stronger and is cheap: spy on
`sessionStorage`/`localStorage` and `console`, call `generatePkcePair()`, assert nothing was touched. The source
scan is the established pattern here (T12's four-file scan, T13's bundle scan) and catches the realistic case, but
it cannot catch a leak through a dynamically-resolved global.

**For phase 9:** add the runtime assertion alongside the scan rather than in place of it. The two fail for different
reasons, which is the point.

## What I checked and found sound

- **AC3 is the load-bearing case** and cannot be satisfied by a wrong hash; it is an external, immutable vector.
- **AC7 is reliable under the environment that runs it** — 15 consecutive runs, 44/44, after phase 4 derived the
  threshold against a different CSPRNG. The honest limit (it validates no arithmetic of ours, because `btoa` does
  the mapping) is stated in the test file itself, not only in an artifact.
- **The tail case derives its 16 values** from "the two low bits of the final sextet are always zero" rather than
  hardcoding the measured string, so it asserts the reason.
- **AC1's non-vacuity is paid for.** The validator is fed eleven inputs the generator can never produce, including
  both characters base64url omits — without which a validator written over base64url's 64-character alphabet would
  pass everything else in the file.
- **No file outside `src/auth/` was touched**, and the suite delta is exactly the 44 cases added.

## Summary

Seven findings: **two medium** (S1, S2 — both guard weaknesses in my own test, both demonstrated by running code,
neither currently leaking), four low, one informational. Nothing requires a change to the frozen brief. All are
carried to phase 9.
