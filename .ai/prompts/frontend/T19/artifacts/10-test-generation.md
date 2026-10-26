# frontend · T19 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md`, the frozen brief |
| **Produces** | `artifacts/10-test-generation.md`, `frontend/src/auth/callbackValidatesNonce.test.tsx` |

| | |
|---|---|
| **Named test** | `callbackValidatesNonce` (`package.md:175`) — **97 cases, all passing** |
| **Whole suite** | **792 passed / 1 failed (793)** — the one is the known environmental `oxlint` **AC4** |
| **Types / lint** | `tsc --noEmit` clean · `oxlint` exit 0 |
| **Mutants** | **15 run, 15 killed, zero survivors** |

---

## Shape of the file

**Two layers, deliberately.** The decision is exercised **directly** for the shape matrix — a
component harness there would add noise and hide which input produced which outcome — and **through
the screen** for the outcome, ordering and storage boundary.

The reason for the second layer is Phase 6's **D-2**: the check can be skipped entirely by a test
double that resolves the wrong shape. Before the seam was corrected, `response.idToken` threw a
`TypeError` before the comparison ran, so every nonce assertion passed having tested nothing.
**Every block therefore carries a control proving its assertion is reached**, not merely satisfied.

| Block | Cases | ACs |
|---|---|---|
| the comparison is exact | 13 | AC1, AC2, AC5 |
| bound to storage, not to a constant | 5 | **AC6**, AC7 |
| the claim must be a string that is there | 10 | AC3, AC4 |
| the token itself must be readable | 25 | **AC18**, AC19 |
| something must be stored to compare against | 5 | AC8, AC9 |
| nothing is written, removed or cleared | 2 | AC12 |
| the screen acts on the decision | 13 | AC1, AC10, AC15 |
| no token kept, no cause disclosed | 3 | AC11, AC13 |
| what the source may not contain | 21 | AC5, AC14, AC19 |

**AC6 shares no literal between its rows.** Task 18's surviving mutant compared storage against a
hardcoded literal that *every fixture happened to seed*, and passed 125 of 126 cases. Each row here
seeds a different value, asserts the match, then changes only the store and asserts the flip.

---

## Two of my own scans were wrong, and the tempting repair was to weaken the source

The first run of the source scans failed twice — **on correct code**.

- The length scan `/\.length\s*[=!<>]/` tripped on `segments.length !== 3`, which is the check that
  **rejects an unsecured two-segment JWT**. Satisfying the scan would have meant deleting a security
  check.
- The signature scan matched the word `verify` in the module's own comment, *"The payload is DECODED,
  not verified."* Satisfying it would have meant rewording the sentence that documents the decision.

Both repairs would have made the code worse to make a wrong test green. Instead the scans were
narrowed to the property they actually mean: comments are stripped before scanning, and the length
scan now forbids a length comparison **of the nonces** (`/\b(stored|claimed|nonce)\w*\.length/i`)
rather than of anything. A control asserts the comment the scan must now look past is still present.

Recorded because the failure mode is subtle: **a scan that is too broad teaches you to damage the
thing it was written to protect.**

---

## Mutants — 15 run, 15 killed

Tests were committed before the first mutant ran; the harness reverts with `git checkout --`, which
has silently destroyed uncommitted cases twice in this package.

| # | Mutant | Cases failed |
|---|---|---|
| M1 | invert the comparison | 38 |
| M2 | compare against a constant | 14 |
| M3 | read the `state` key instead of `nonce` | 32 |
| M4 | drop the non-string guard — **the F1/I1 fix** | 7 |
| M5 | drop the absent-claim check | 1 |
| M6 | drop the non-string claim check | 8 |
| M7 | remove the `try`/`catch` around the read | 1 |
| M8 | accept a two-segment unsecured JWT | 1 |
| M9 | drop `TextDecoder`, read bytes as latin-1 | 4 |
| M10 | drop the empty-string normalization | 1 |
| M11 | delete the nonce on a failed check | 2 |
| M12 | clear storage on a failed check | 1 |
| M13 | revert to `.then(…).catch(discard)` — **the F3/I2 fix** | 4 |
| M14 | consult the decision but never act on it | 11 |
| M15 | render the rejection reason | 1 |

**Four survived the first run.** Each was a real gap, and the most important one was a case I had
written believing it already closed.

### M9 — the non-ASCII case was not testing what it claimed to

F4 and I3 both said the `TextDecoder` path was unfalsifiable, and Phase 9 fixed the helper so a
non-ASCII payload could finally be built. I wrote the case, it passed — **and the mutant that drops
`TextDecoder` still survived.**

The case carried its non-ASCII in a *neighbouring* claim and an **ASCII nonce**. Reading the bytes
as latin-1 mangles `name`, but the payload still parses and an ASCII nonce still matches, so nothing
the comparison looks at changes. **The non-ASCII has to be in the nonce itself.** Four rows now put
it there — Greek, CJK, accented Latin, emoji — and M9 fails 4 cases.

This is the ninth-and-a-half appearance of this package's recurring defect, in yet another form: not
an assertion against its own constant, and not a path with no fixture, but **a fixture that exercises
the path without exercising the property**.

### M12, M13, M15 — three narrower gaps

- **M12** — the storage-survival assertion existed only for the pure function, so clearing
  `sessionStorage` from the *screen* survived. Now asserted through the screen too.
- **M13** — `.then(onFulfilled, discard)` was unfalsifiable because, by design, nothing in the
  discard path can throw. A case now mocks `setSignInNotice` to throw and asserts **one** attempt,
  not two. The defensive form is now load-bearing rather than decorative.
- **M15** — the no-reason-rendered scan read only the test container, so writing the reason into
  `document.title` survived. Now checked against the whole document and the tab title, since a tab
  title is as visible as copy (L4).

---

## Carried to Phase 12

- **Kimi's I5** — `codeExchange.ts` records that the real token response carries no `refresh_token`,
  the empirical basis for **L3**, in a comment rather than a test. **Task 20** owns pinning it.
- **Kimi's I8** — T18's mock now reads the stored nonce, so a `buildAuthorizeRequest` regression that
  stopped writing it would be caught here (AC8) rather than there. Noted, not a defect.
- **The expected suite total is 793**, asserted in prose. Kimi's T18 Gap 6 asked that this be
  enforceable by CI; still open, still gating nothing.
- The environmental failure is **AC4** of `lintRejectsHandWrittenBackendFetch` — `oxlint@1.87.0`
  prints no summary line on this host. **Not** the `pnpm` failure, which is AC2 and passes under the
  `npx -y pnpm@12.14.0` invocation.

---

## Next

Phase 11 — test review. **Kimi 2.7.**
