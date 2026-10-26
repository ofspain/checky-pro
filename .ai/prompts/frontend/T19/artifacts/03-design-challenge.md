# frontend · T19 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

---

## Findings

### Finding 1 · OQ-1 is treated as "flagged for Phase 4" but the entire brief assumes reading (i)

- **Issue:** The TIB marks OQ-1 as blocking and says it will be decided at the Phase 4 approval gate. Yet every subsequent section — Scope, Dependencies, Files to Modify, Acceptance Criteria, Required Tests — is written under the assumption that **T19 owns the decision and task 20 owns transport**. If the owner chooses reading (ii) instead, the brief is wrong in at least five places. A genuinely blocking decision should not have the document proceed as if it is already resolved.
- **Severity:** High. This is the central sizing decision for the task.
- **Evidence:** §Scope "Out" says "The token request itself — its URL, parameters and the `code_verifier` are **task 20**." §Dependencies lists `exchangeAuthorizationCode(grant): Promise<void>` as existing. §Files to Modify says `codeExchange.ts` — "only so far as T19 needs an ID token to validate." AC1 says "A matching `nonce` is accepted and the flow continues" — but "accepted" is undefined if T19 does not receive a token.
- **Recommended brief amendment:** Make OQ-1 a fork, not a flag. Add two sub-sections to §Scope and §Files to Modify: one for reading (i) and one for reading (ii). Or, if the brief wants to commit to (i) now, remove the "blocking" label and state the commitment explicitly. As written, the brief asks the Phase 4 gate to ratify a decision it has already made.

---

### Finding 2 · The seam `exchangeAuthorizationCode` returns `Promise<void>`, but T19 needs an ID token

- **Issue:** Under OQ-1 reading (i), T19 validates the nonce but task 20 performs the exchange. The existing seam returns `Promise<void>`. For T19 to validate a nonce, something must yield an ID token. The brief says `codeExchange.ts` may be modified "only so far as T19 needs an ID token to validate," but it does not say what that seam looks like. If `codeExchange.ts` is modified to return a token, then T19 is no longer "only the decision" — it is consuming the result of an exchange. If a new module is introduced between `CallbackScreen` and `codeExchange`, the brief does not describe it.
- **Severity:** High. This is an unresolved interface question that blocks implementation planning.
- **Evidence:** `src/auth/codeExchange.ts:18` declares `export async function exchangeAuthorizationCode(_grant: AuthorizationCodeGrant): Promise<void>`. `CallbackScreen.tsx:105` calls `exchangeAuthorizationCode({ code, state }).catch(discard)`. The TIB §Dependencies table says the seam "exists, deliberately empty."
- **Recommended brief amendment:** Define the seam contract explicitly. Option A: `exchangeAuthorizationCode` returns `Promise<{ idToken: string }>` starting in T19, and task 20 fills its body with real transport. Option B: introduce a separate `decodeIdToken(token: string): { nonce: unknown }` that T19 owns, and keep `exchangeAuthorizationCode` as task 20's transport seam. Option C: T19 receives a raw token response object from a new seam. Whichever is chosen, state it and show the type signature.

---

### Finding 3 · No JWT decoder is specified, but "decode and compare" requires one

- **Issue:** OQ-2 recommends "decode and compare" on the grounds that no JWT library is in the confirmed stack (L13). But decoding a JWT, even without signature verification, requires base64url decoding and JSON parsing of the payload. The brief does not say whether T19 may implement this inline, import a minimal decoder, or treat the ID token as an opaque string until task 20. L13 says "no JWT library is in it," but it does not say "no base64url/JSON decoder may be used to read a JWT." This ambiguity will force the implementor to make a stack decision that may violate L13.
- **Severity:** Medium-High. This is a tooling boundary that affects what dependencies are added.
- **Evidence:** §Dependencies lists "JWT decoding library: none." OQ-2: "Recommended: decode and compare." L13: "Confirmed stack — nothing new may be added to it casually. **No JWT library is present**."
- **Recommended brief amendment:** Decide and state whether T19 may use the browser's `atob` / `btoa` plus `JSON.parse` to decode the JWT payload, or whether a dedicated JWT library is required (which would need an ADR). If inline decoding is allowed, add a constraint that only the payload is decoded — no signature verification, no JWS dependency — and that the decoder must reject malformed tokens rather than throw.

---

### Finding 4 · AC11 "no token anywhere" is unenforceable if task 20 performs the exchange

- **Issue:** AC11 requires that a rejected sign-in retain no token anywhere. Under OQ-1 reading (i), task 20 owns the exchange and would be the one receiving the token response. T19 only sees the ID token if task 20's seam passes it back. But if the real token response contains both an access token and an ID token, and task 20's seam returns only the ID token to T19, the access token has already entered the process. T19 cannot guarantee that task 20 does not retain it. The brief's scope excludes "Holding the access token in a module variable — **task 21**" and "Proving no token is written to any storage — **task 22**," which means T19 is not responsible for those. Yet AC11 says "no token anywhere," which overlaps with tasks 21 and 22.
- **Severity:** Medium. The security property is real, but the boundary is muddy.
- **Evidence:** §Scope "Out" lists task 21 and task 22. AC11: "A rejected sign-in retains **no token** anywhere: storage, module variable, URL or log." §Purpose: "T19 is the first task that can hold one."
- **Recommended brief amendment:** Narrow AC11 to "no ID token is retained by T19's code path" or "no token returned to T19 is retained anywhere T19 can control." Explicitly state that task 20's transport layer and task 21's in-memory hold are responsible for their own tokens. Alternatively, if T19 is to enforce AC11 across the whole exchange, move the exchange into T19's scope (reading ii).

---

### Finding 5 · The comparison shape table includes "a string equal to the `state`" which is ambiguous

- **Issue:** The Required Tests "Shape" section lists "a string equal to the `state`" as a nonce-claim shape to reject. This is confusing: if the stored nonce happens to equal the callback URL's `state`, and the ID token's `nonce` claim equals that same value, then by definition the nonce matches. The test must be either (a) the token's `nonce` claim equals the URL `state` but not the stored nonce, or (b) the stored nonce was accidentally set to the URL `state` value. The brief does not clarify which. A test that rejects "nonce equals state" would be wrong if the nonce genuinely matches.
- **Severity:** Medium. This is an ambiguous test requirement that could lead to an incorrect test.
- **Evidence:** §Required Tests, "Shape" row: "claim absent · `null` · a number · an object · an array · **a string equal to the `state`**."
- **Recommended brief amendment:** Replace "a string equal to the `state`" with "a string equal to the URL `state` but not the stored nonce" or "a nonce claim that matches a value stored under the wrong key (e.g. `state` written under `nonce`)." Clarify that the test proves the comparison uses the `nonce` key, not that it rejects coincidences.

---

### Finding 6 · AC6 "at least two unrelated stored values" is not enough after T18's lesson

- **Issue:** AC6 requires the comparison to be bound to storage "over at least two unrelated stored values." T18 showed that even two values are insufficient if one of them is a constant shared by every fixture. The brief uses the same wording as T18's AC5, which had to be upgraded in Phase 9 to two *unrelated matching values* after a mutant survived 125/126 cases. T19's AC6 should be stronger from the start: the matching value must not be a hardcoded literal shared by the success-path fixtures.
- **Severity:** Medium. This is a test-quality issue that could allow a subtle constant-comparison mutant to survive.
- **Evidence:** §Constraints: "Nine times in this package an assertion has been compared against the very constant it tests; T18's variant — a constant every fixture happened to share — survived **125 of 126 cases**." AC6: "The comparison is bound to the **stored** value… over **at least two unrelated** stored values."
- **Recommended brief amendment:** Reword AC6: "The comparison is bound to the stored value, proven over at least two unrelated stored values where the matching case does not reuse a single hardcoded literal across all success-path fixtures."

---

### Finding 7 · The ID token contract is absent, making AC3 and AC4 untestable without assumptions

- **Issue:** AC3 requires rejecting an absent `nonce` claim, and AC4 requires rejecting a non-string claim. But the brief itself notes that "the ID token's claim set differs from the access token's" and that no contract document exists. The implementor must assume the token is a JWT with a JSON payload containing a `nonce` field. If the token is malformed or the payload is not JSON, the decoder may throw. The brief does not say whether a malformed ID token is treated as a nonce failure (discard) or as an unhandled exception.
- **Severity:** Medium. This is an edge case that the brief ignores.
- **Evidence:** §Dependencies: "ID-token claim contract: **does not exist**; `contracts/api/token-claims.md` is the access-token contract." AC3, AC4 describe claim shapes but not token-shape failures.
- **Recommended brief amendment:** Add an edge case: "A malformed ID token (not three base64url segments, or a payload that is not valid JSON) is treated as a nonce validation failure and discarded, not as an unhandled exception." This is consistent with AC9's "throwing read" handling and L4's neutral outcome.

---

### Finding 8 · T19 must update T17/T18 tests, but the brief does not specify how to preserve their intent

- **Issue:** AC16 says T17's and T18's no-nonce-read assertions must be "corrected, not deleted, and still prove what they were written to prove about `state` and `code_verifier`." The brief does not say what the corrected assertion should be. T18's test currently asserts: on the comparison path, `state` is read and `nonce` and `code_verifier` are not. After T19, `nonce` **will** be read on the success path. The corrected assertion should likely split into two phases: before the state match (still no reads) and after the state match (state and nonce read, verifier not read). The brief should specify this.
- **Severity:** Medium-High. This is a concrete test-update task that is underspecified.
- **Evidence:** `callbackWithMismatchedStateIsDiscarded.test.tsx:647-663`: "reads the state key and no other once the comparison is reached." AC16: "T17's and T18's assertions are **corrected, not deleted**, and still prove what they were written to prove."
- **Recommended brief amendment:** Add to §Files to Modify / §Required Tests: "Update T18's `reads the state key and no other once the comparison is reached` to `reads state and nonce once the state matched, and still never reads code_verifier`. Add an assertion that before the state match, no nonce read occurs." Show the expected corrected assertion text.

---

### Finding 9 · The `SessionTokens` interface is the wrong abstraction for an ID token

- **Issue:** The TIB lists `SessionTokens` (`src/api/sessionBoundary.ts:11`) as a dependency, with `current(): string | null`. This interface is for access tokens used in bearer headers. The ID token is not used as a bearer token; it is only validated and then discarded (or held separately by task 21). If T19 uses `SessionTokens` to hold the ID token, it confuses two different tokens with different lifecycles. If T19 does not use it, why is it listed as a dependency?
- **Severity:** Low-Medium. This is a design smell, not an immediate bug.
- **Evidence:** `src/api/sessionBoundary.ts:11-14`: `interface SessionTokens { current(): string | null; renew(): Promise<string>; }`. TIB §Dependencies lists it as "declared, no production implementation (task 21)."
- **Recommended brief amendment:** Remove `SessionTokens` from T19's dependencies, or clarify that T19 does not implement or consume it. If T19 needs to pass an ID token to task 21, define a separate, task-21-facing interface rather than overloading `SessionTokens`.

---

### Finding 10 · AC10's "byte-for-byte indistinguishable" comparison does not account for the nonce check running after a token was obtained

- **Issue:** AC10 requires the nonce-failure outcome to be byte-for-byte identical to R8's discard. That is correct for visible output. But the timing differs: a nonce failure occurs after a network round-trip to `/oauth2/token`, whereas an R8 discard occurs synchronously. L4 says timing must be identical "for unknown, missing, locked, suspended, and deleted accounts, and for bad, expired, and already-used tokens" — but a nonce failure happens after the SPA has already shown the callback holding screen for a network duration. The brief asserts "same timing" without acknowledging this network delay.
- **Severity:** Low. The SPA cannot control the network timing, and L4's timing rule applies to the SPA's own rendering, not to server response timing. But the brief should not claim identical timing without this caveat.
- **Evidence:** `agents.md:41-43`: "The SPA cannot control server response timing. The timing requirement applies to the SPA's own rendering only." TIB AC10: "A rejection is **indistinguishable** from R8's discard: same copy, same path, same `REPLACE`, same empty query." (no timing claim, but §Constraints says "The outcome discloses nothing about which check failed (L4)," which implies timing is part of indistinguishability.)
- **Recommended brief amendment:** Add a note to AC10: "Visible outcome only. Network timing is outside the SPA's control and is excluded by `agents.md`."

---

### Finding 11 · The mutation list includes "delete an L16 item on success" but not "delete an L16 item on failure"

- **Issue:** The Required Tests mutation list includes deleting an L16 item on success, but the security-relevant mutant is deleting an item on **failure**. A nonce failure must not delete `state`, `nonce`, or `code_verifier` because task 23 owns deletion. The T18 tripwire specifically tests that the same callback URL matches twice because deletion is deferred. T19 should have a mutation for "delete on nonce failure" as well.
- **Severity:** Low-Medium. This gap could miss a regression that eagerly cleans up after a nonce failure.
- **Evidence:** §Required Tests "Mutation" row: "invert the comparison · compare against a constant · compare against the stored `state` · drop the absent-claim check · remove the `try`/`catch` · **delete an L16 item on success** · move the nonce check above the `state` check." AC12: "T19 writes, deletes and clears **nothing** in `sessionStorage`."
- **Recommended brief amendment:** Add to the mutation list: "delete an L16 item on nonce failure" and "clear sessionStorage on nonce failure."

---

### Finding 12 · AC14's "no `import.meta.env` read" is inherited but T19 may need a public config key for the issuer

- **Issue:** AC14 forbids `import.meta.env` reads. T19 does not perform the network exchange under reading (i), so it should not need environment keys. But if T19 decodes the ID token and wants to validate its `iss` claim (even though signature verification is out of scope), it would need the issuer. OQ-2 explicitly rejects signature verification, so `iss` validation is likely also out of scope. The brief should state this explicitly to prevent scope creep.
- **Severity:** Low.
- **Evidence:** §Files NOT to Modify: "`frontend/package.json` (no new dependency without an ADR — L13)." AC14: "T19 adds no absolute address, no per-service URL and no `import.meta.env` read."
- **Recommended brief amendment:** Add to Constraints: "T19 does not validate `iss`, `aud`, `exp`, or any other ID token claim beyond `nonce`. Claim validation beyond `nonce` is task 20's or out of scope."

---

## Summary

| # | Issue | Severity | Section affected |
|---|---|---|---|
| 1 | OQ-1 flagged as blocking but brief assumes reading (i) | High | Open Questions, Scope, Dependencies |
| 2 | `exchangeAuthorizationCode` seam cannot yield an ID token under reading (i) | High | Dependencies, Files to Modify |
| 3 | No JWT decoder specified | Medium-High | OQ-2, Dependencies |
| 4 | AC11 "no token anywhere" conflicts with task 20/21 boundary | Medium | AC11, Scope |
| 5 | "a string equal to the `state`" test shape is ambiguous | Medium | Required Tests |
| 6 | AC6 not strong enough after T18's lesson | Medium | AC6, Constraints |
| 7 | Malformed ID token handling undefined | Medium | AC3, AC4 |
| 8 | T17/T18 assertion update is underspecified | Medium-High | AC16, Files to Modify |
| 9 | `SessionTokens` dependency is inappropriate for ID token | Low-Medium | Dependencies |
| 10 | AC10 timing claim ignores network delay | Low | AC10 |
| 11 | Missing "delete on failure" mutant | Low-Medium | Required Tests |
| 12 | Scope creep risk toward `iss`/`aud` validation | Low | Constraints |

**None of these are blockers to starting the design challenge,** but Findings 1, 2, and 8 must be resolved before the brief can be frozen and implemented. Findings 3, 4, and 7 are the next most important for a clean task boundary.