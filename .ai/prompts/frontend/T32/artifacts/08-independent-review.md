<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T32 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/emailVerifiedClaimIsNotAccountState.test.ts` | **20/20 passed** |
| `python3 -I .ai/prompts/frontend/T32/artifacts/07-mutation-harness.py` | **11/11 killed, 0 broken, 0 survivors** |
| `oxlint src` | **exit 0, no output** |
| `tsc --noEmit` | **exit 0** |

No production or test code was changed during this review.

---

## Findings

### I1 — `USERINFO_READ` is a literal substring match that misfires on legitimate call shapes
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts:210` defines `USERINFO_READ = "GET('/userinfo'"` and `:303` uses `target.code.includes(USERINFO_READ)` to decide whether a file is a `/userinfo` caller.
· **Assessment:** A legitimate `/userinfo` consumer written with double quotes — `api.GET("/userinfo")` — does **not** contain the substring `GET('/userinfo'` and would be judged off-channel, failing AC2. The same applies to a call whose arguments are wrapped or indented (`api.GET(\n  '/userinfo'`) or a call through a path constant (`const USERINFO = '/userinfo'; api.GET(USERINFO)`). The self-review's S1 correctly identifies this as the weakest line; the examples above show it is not hypothetical.
· **Recommendation:** Add variants for double quotes and optional whitespace to the channel detector, or explicitly freeze the permitted call shape in the brief and document that any other shape must be added to the allowlist. Better: use a regex that matches `api\.GET\(\s*['"]/userinfo['"]` rather than a single literal substring.

### I2 — `TOKEN_DECODE` misses realistic formatting variations and new decoding libraries
· **Severity:** **Major**
· **Evidence:** `:192` defines `TOKEN_DECODE = '\\batob\\b|\\bjwt[Dd]ecode\\b|\\bdecodeJwt\\b|split\\([\'"]\\.[\'"]\\)'`. The `split(...)` arm requires no spaces: `split('.')` matches, `split( '.' )` does not.
· **Assessment:** If a future file decodes a JWT with `split( '.' )` (spaces inserted by a formatter), `TOKEN_DECODE` will not recognize it as a decoder. AC1's "exactly one file" test will still pass because it only sees `auth/idTokenNonce.ts`, and the new decoder will not be checked for claim reads. The same gap exists for a library like `jose` using `decodeJwt(token)` (covered) or a generic `decode(token)` (not covered), or `Buffer.from(token.split('.')[1], 'base64')` (the `split('.')` is matched, but only if unspaced).
· **Recommendation:** Add controls that prove the scan **misses** these variations intentionally or widen the pattern to cover them. At minimum, allow optional whitespace around the argument: `split\(\s*['"]\.['"]\s*\)`. A dirtier-proof would create a synthetic decoder using `split( '.' )` and assert it is **not** recognized, documenting the accepted risk.

### I3 — A string literal can spoof the `/userinfo` channel check
· **Severity:** **Major**
· **Evidence:** `strip()` at `:70-110` removes comments but keeps string and template literals. `offChannel()` at `:301-305` treats any file whose stripped code contains `GET('/userinfo'` as a `/userinfo` channel, regardless of whether the text is a real call or a string literal.
· **Assessment:** A file can evade AC2 by including a string such as `"See api.GET('/userinfo')"` while actually reading `email_verified` from a token. The literal-match design trades comment-spoofing for string-spoofing; the self-review S1 notes the comment-spoofing risk but not the string-spoofing one. Since `strip()` preserves strings precisely so claim names inside literals are not lost, the channel detector inherits the same preserved-string surface.
· **Recommendation:** Make the channel check require the `GET('/userinfo'` text to appear outside any string literal. This needs either (a) a second pass over raw source that tracks quote state, or (b) a post-`strip` heuristic that checks the surrounding characters are call-like (`api.GET('/userinfo', ...)` rather than `'...GET('/userinfo')...'`). Document whichever trade-off is chosen.

### I4 — Test-scan helpers are duplicated again instead of shared
· **Severity:** **Minor/Major**
· **Evidence:** T32's test file reimplements `strip`, `refuseEmpty`, `sourceRoot`, `productionSources`, `hitsOf`, `synthetic`, `stanza`, and the surface-sweep helpers. The self-review logs this as OQ-T32-4.
· **Assessment:** The duplication is deliberate and bounded, but it is still a maintenance hazard. A bug fix or behavior change in T29/T30/T31's helpers (e.g. how URLs are stripped, how test files are excluded, how surfaces are collected) will not propagate to T32. The file's header says "a helper edited for one task would silently change what another task's scan means" — but the opposite is also true: a helper *not* edited when it should be creates drift.
· **Recommendation:** Move the scan helpers to a shared test-utility module such as `src/testing/scanHelpers.ts`, owned collectively or versioned, so improvements propagate and divergence is visible. This is the same debt T31 accumulated and T32 is now extending.

### I5 — AC8's self-referential phrase test is a word-count guard
· **Severity:** **Minor**
· **Evidence:** `:479-486` checks that the file header contains `source-side`, `not data-flow guards`, `forward guard`, and `OQ-T32-1`.
· **Assessment:** The intent is to prevent the limitation disclosure from being deleted. But the test is satisfied by any rewrite that keeps those four substrings, even if the meaning is gone or garbled. A self-referential assertion is the weakest form of guard and does not prove the limitation is understood.
· **Recommendation:** Replace the four separate substring checks with a single required sentence, or accept that this is a disclosure guard rather than a behavioral one and document it as such. The self-review's S6 already notes this; it should be recorded as accepted with a comment rather than left as a silent weakness.

### I6 — AC3 pins contract prose by exact phrase
· **Severity:** **Minor**
· **Evidence:** `:368-375` asserts that the `/userinfo` field description contains `NOT the access`, `token-claims.md`, and `T32`.
· **Assessment:** This pin is load-bearing — MR08 proves it fires — but a meaning-preserving rewording of the contract line would fail the test. The self-review's S5 notes this and leaves it unresolved.
· **Recommendation:** Keep the pin on the presence of a disclaimer and the `T32` citation, but relax the exact phrase `NOT the access` to a pattern such as `/not the access|not.*access token/i`. This preserves the signal while reducing brittleness to editorial changes.

### I7 — AC2's channel rule doesn't prove a `/userinfo` file is the *source* of the value
· **Severity:** **Minor**
· **Evidence:** `offChannel()` permits any file that both (a) names `email_verified` and (b) contains `GET('/userinfo'`.
· **Assessment:** A file could contain an unrelated `/userinfo` call and also read `email_verified` from a token or a prop. Static analysis cannot do data-flow, so this is an accepted limitation, not a bug. But it should be stated in the file header alongside the other limitations, not only implied by the phrase "source-side guard".
· **Recommendation:** Add one sentence to the header making explicit that AC2's channel rule is a file-level heuristic: it permits a file that calls `/userinfo`, not a specific value-flow.

### I8 — The surface sweep doesn't test mixed-case or substring needles
· **Severity:** **Minor**
· **Evidence:** AC7's `NEEDLES` are `['email_verified', 'emailVerified', 'PENDING_VERIFICATION']`. The dirty-proof writes exactly these strings to surfaces and console channels.
· **Assessment:** The sweep lowercases both surface text and needles, so mixed case is covered. But it does not test that a *substring* such as `verified` or `pending` is or is not reported. The task's concern is the specific flag, not the word "verified" generally, so this is correct. However, a surface containing `email_verified_flag` would be caught (substring match), which is the desired behavior.
· **Recommendation:** None. The needle list is appropriately specific.

### I9 — MR05's 154 failures make it a coarse kill signal
· **Severity:** **Minor**
· **Evidence:** MR05 breaks the nonce decode and causes ~154 suite failures.
· **Assessment:** The self-review's S4 notes this. The kill is legitimate but undiscriminating: it says little about T32's own cases beyond that at least one fired. The value of MR05 is the allowlist lower bound, which is worth keeping.
· **Recommendation:** None, but record in the artifact that MR05 is a structural pin, not a precision kill.

### I10 — No mutant covers a camelCase read outside the decoding file
· **Severity:** **Minor**
· **Evidence:** MR01/MR02 cover `email_verified`/`emailVerified` inside `idTokenNonce.ts`. MR04 covers `email_verified` in `client.ts`. There is no mutant for `emailVerified` in a non-decoding file.
· **Assessment:** This is deliberate, as the self-review S2 explains: a consumer reading `emailVerified` from `readAccount` is legitimate, and a mutant that reads it elsewhere would be an intended survivor. The limitation is documented.
· **Recommendation:** None, but ensure the limitation stays prominent in the artifact and the file header so a future reader does not assume AC2 covers camelCase consumer code.

---

## What the implementation gets right

- **Phase 3's blocker is resolved.** The instrument keys on provenance, not spelling, because `oidc.yaml` spells the permitted `/userinfo` field exactly like the prohibited claim.
- **AC1 is bounded and re-proven**, not inherited from T30/T31. The decode-file set is asserted as exactly one file, and the permitted decoder's lower bound is checked.
- **AC2 has three dirty-proofs**: it fires on an off-channel mention, stays silent on a permitted `/userinfo` mention, and stays silent on the verification link flow.
- **AC7 is implemented as a dirty-proof**, not a vacuous "no hits" report.
- **AC8 asserts the limitation disclosure**, making it harder to delete quietly.
- **The mutation harness learns from T31**: names are checked before count, BROKEN fails the run, and contracts are repo-relative.
- **MR11 is deliberately inherited-only**, documenting that verification-state caching is T31's concern, not T32's.

---

## Summary

T32's implementation is sound and the mutation score is independently reproducible. The highest-priority item is **I1** (the literal `USERINFO_READ` match), followed by **I3** (string-literal spoofing of the channel check). Both are in the same AC2 channel rule and both can be fixed by making the channel detector regex-based and quote-aware. **I2** is the next most important because it is a gap in AC1's decoder bound. The duplication finding (**I4**) is the main maintenance debt to address before the package grows further.

**Phase 8 complete.**
