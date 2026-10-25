# frontend · T14 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Kimi's phase 8 **corroborated all seven** of my phase-7 findings and added four. **All eleven are now applied.**
Two of the new findings were confirmed by running code before being accepted; one carried a false premise, noted
below.

## Disposition

| # | Finding | Source | Disposition |
|---|---|---|---|
| S1 | comment stripper eats a line after `//` inside a string literal | mine, corroborated ("the most important defect") | **FIXED** — `codeWithoutComments()` |
| S2 | positive CSPRNG assertion runs over raw source | mine, corroborated | **FIXED** — asserted over stripped code |
| S3 / New 2 | `toContain('URL')` brittle against a rename | mine; Kimi gave the fix | **FIXED** — anchored patterns, plus a case |
| S4 | `.length` counts UTF-16 code units | mine, corroborated as **not a defect** | no change; reasoning recorded |
| S5 | derivation never exercised on a long verifier | mine, corroborated as a real gap | **FIXED** — 128-character case |
| S6 | `generatePkcePair` re-validates its own verifier | mine, corroborated as deliberate and sound | no change |
| S7 | no runtime leak assertion | mine, corroborated | **FIXED** — behavioural case added |
| New 1 | the environment check runs after `beforeAll` | Kimi | **FIXED** — premise verified first |
| New 3 | nothing pins the CSPRNG byte count | Kimi | **FIXED** — spy on the call site |
| New 4 | `generatePkcePair` not tested for distinct pairs | Kimi | **FIXED** |

**44 → 48 cases.** Five added; the forbidden-name list went from fifteen string entries to fourteen patterns
(`http` and `https` merged into one `/https?/` rule).

## The fixes that needed verifying first

### New 1 — confirmed, and it was a real diagnostic loss

Kimi's claim is that the outer `beforeAll` generates 2,000 verifiers — which calls `getRandomValues` — before the
nested `it` that checks Web Crypto is present, so a missing API throws a stack trace and the readable message never
appears. **Verified** with a throwaway test proving an outer `beforeAll` runs before a nested `describe`'s case.

Fixed by asserting the two capabilities **at the top of `beforeAll`, before the sample is built**, with the message
there. The standalone case stays as documentation.

### S1 — the fix is verified against the case that demonstrated the hole

```
const x = 'a//b'; localStorage.setItem('k','v');
  OLD stripper hides localStorage : true     <- the hole
  NEW stripper hides localStorage : false    <- closed
NEW stripper still removes block and full-line comments : true
```

`codeWithoutComments()` removes block comments wholesale and a line comment only where `//` begins the trimmed
line. It cannot consume code, and the module's doc comments — which name `Buffer`, `sessionStorage` and `plain` on
purpose — are still stripped, so the module may go on explaining itself.

### S2 — verified the same way

```
module that only mentions crypto.getRandomValues in a doc comment:
  now fails the positive assertion : true
```

### New 2 — accepted, with its premise corrected

The recommended `/\bURL\b/` is right and is applied: it does not match `base64URLFromBytes` (no word boundary
between `4` and `U`, or between `L` and `F`) and still matches `new URL(`, `URL.createObjectURL` and
`globalThis.URL`. A case now asserts both halves — that the module's own `base64UrlFromBytes` is present and the
rule does not fire on it.

**But the finding's stated reason is wrong**: it says the substring check "does not catch `globalThis.URL` or
`window.URL`". It does — `'globalThis.URL'.includes('URL')` is `true`. The substring form's only real problem is
the false positive, which is what my S3 said and what the fix addresses. Recorded because the premise would
mislead anyone reading the review later.

### New 3 — accepted as isolation, and its own severity confirmed

Kimi notes AC1 would catch a changed byte count indirectly. Measured, it would:

| bytes | base64url length |
|---|---|
| 31 | 42 — fails AC1 |
| **32** | **43** |
| 33 | 44 — fails AC1 |

So the finding is low-value as stated, and it says so. Added anyway because it pins the contract with the CSPRNG at
the call site rather than inferring it from the output: `vi.spyOn(crypto, 'getRandomValues')`, called **once**, with
a `Uint8Array` of **32** bytes.

## Gates after the fixes

| Check | Result |
|---|---|
| `vitest run src/auth` | **48/48** |
| 8 consecutive reruns | **48/48 every time** — the statistical cases stay stable with the new cases present |
| `vitest run` (whole suite) | **460/462** — 414 + 48, delta exactly 48 |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |

The two failures remain T07's `lintRejectsHandWrittenBackendFetch` AC4 and AC2, environmental, untouched.

## Kimi's closing note is wrong again, for the fourth time

The review ends: *"the suite could not be executed on this host because `frontend/node_modules` is incomplete
(`vite-plugin-pwa` is missing), continuing the T12 OQ-C blocker."* **T12 closed that in its phase 6**
(`ab9ec5c9`). The same claim was rejected with evidence in T12 phase 3, T13 phase 3, and T14 phase 3 (D-0). The
suite has been run at every phase of this task, and the numbers in this artifact are measured. Nothing in the
review's findings depends on the claim, so it costs nothing here — but it is the fourth repetition and is recorded
as such rather than passed over.

## Still open, for the owner

**OQ-1 — unchanged, and now one task from biting.** T01 `:130` forbids any source under `src/**` from naming
`import.meta.env`; `design.md` §4c and R58 expressly permit `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`,
`VITE_OIDC_REDIRECT_URI` and `VITE_API_BASE_PATH`. T14 reads no key and is unaffected. **T15 or T16 will.**
