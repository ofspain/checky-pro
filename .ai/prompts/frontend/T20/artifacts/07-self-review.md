# frontend · T20 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` and the code it describes |
| **Produces** | `artifacts/07-self-review.md` |

Eight findings. None blocks Phase 8. Two are handoffs that would cost a later task real work if they went
unrecorded, and one corrects the Phase 5 plan.

---

### F1 · AC4's "exactly five fields" rests on a test, not on the type, the moment anything spreads into the body

`contracts/api/oidc.yaml`'s `TokenRequest` sets `additionalProperties: false`, and `openapi-typescript` turns that
into a closed object type (`src/generated/oidc.ts:32-42`). TypeScript's excess-property check then rejects a sixth
field — **but only for an object literal**. `body: { ...fields, state }` compiles, and so does a body built in a
variable and passed in. The literal in `codeExchange.ts` is written out in full today, so the guarantee holds; what
does not hold is the belief that the compiler is enforcing it.

**Decision: no code change; Phase 10 carries the weight.** The AC4 case asserts the **serialized body**, parsed back
and compared field by field, not the object handed to the client. That catches a spread, a rename and a serializer
change, which no type can. Recorded here so nobody later reads the closed schema as a guarantee it is not.

### F2 · The replaced T18 case now scans T20's source, coupling that suite to T20-D3

`callbackWithMismatchedStateIsDiscarded.test.tsx:1093-1101` asserts `exchangeSource` contains `createOidcClient` and
`POST('/oauth2/token'`. The property being pinned is right and matters — every "no exchange happened" assertion in
that suite is vacuous while the exchange is a stub — but the evidence chosen is the *name of the factory T20-D3
picked*. If the publication shape is ever revisited, a T18 case fails for a reason that has nothing to do with T18.

**Decision: left as written, deliberately, for Phase 8 to weigh.** The weaker alternative is to scan only for the
endpoint path, which survives a renamed factory but would also pass against a hand-written `fetch` to that path —
and that is precisely what L6 forbids. I would rather be coupled to a decision this brief froze than pin a property
loosely enough to admit the thing the rule bans. Kimi should say if it disagrees.

### F3 · `expires_in` is dropped at the boundary, and task 21 needs it

`TokenResponse` is `{ idToken, accessToken }`. The response carries `expires_in` (typed in the contract,
`generated/oidc.ts:52-53`) and T20 discards it. That was the right call for T20 — returning a field nobody reads is
a guess task 21 has to undo — but L18's silent renewal needs to know when the token dies, and the only places that
knowledge exists are the wire and this function.

**Decision: no change; recorded as a handoff.** Task 21 adds `expiresInSeconds` to `TokenResponse` when it builds
the thing that holds the token. The cost of recording it here is one line; the cost of not recording it is task 21
rediscovering it from a 400 in a browser.

### F4 · The OIDC client must never carry the session boundary's renewal middleware

`createOidcClient()` is built per call and registers no middleware, so today the token request bypasses
`src/api/sessionBoundary.ts` entirely. **That is required, not incidental.** The boundary owns single-flight 401
renewal (`agents.md:64-65`), renewal is silent re-authentication that *itself* ends in a token exchange
(`agents.md:25-34`), and a renewal hook on this client would make a failed exchange trigger a renewal that performs
another exchange. Nothing in the code says so, and task 21 is the task most likely to wire a boundary onto every
client it finds.

**Decision: recorded as a constraint on task 21.** Not a comment in `codeExchange.ts`: the risk lives where a
boundary would be attached, not where the client is built. This is the single most expensive thing in this review to
have to learn later.

### F5 · AC19 needs no new test — the Phase 5 plan implied one that already exists

`serviceWorkerNeverCachesApiResponses.test.ts:169-175` already names `['token', '/oauth2/token']` in an `it.each`
asserting `network-only`, and it passes. `/oauth2` is in `NETWORK_ONLY_PATH_PREFIXES`
(`apiRequestPolicy.ts:25-26`) and `isNetworkOnlyRequest` is the negation of the cacheable set
(`apiRequestPolicy.ts:76-78`), so the token response is NetworkOnly by construction and nothing T20 did could
change it.

**Decision: Phase 10 asserts AC19 by citing that case, and writes no second one.** A duplicate would be a second
place to update and no new information.

### F6 · Nothing yet proves the absence of a client credential (AC6)

The probe asserted method, path, content type, body and return value. It did **not** assert that no `Authorization`
header, no `client_secret` field and no cookie-borne credential goes with the request. `checky-spa` is registered
with `ClientAuthenticationMethod.NONE`, so the absence is the whole security story of this task: the verifier is the
only proof, and a request that also carried a credential would mean one exists to be stolen.

**Decision: Phase 10 owes it**, and the brief already requires it ("the absence of any client credential"). Flagged
because a passing probe can read like full coverage, and it was not.

### F7 · The contract documents 200 and 400 only, while the code treats every status alike

SAS can answer with other statuses — a 401, a 429 from an edge, a 5xx. The contract names none of them. The code is
indifferent, by design: anything that is not a 200 with usable tokens becomes the same rejection (T20-D9), so no
behaviour depends on the omission.

**Decision: no change.** Documenting statuses the SPA cannot distinguish would add contract surface that nothing
reads and invite a future reader to read it. The 400 is documented because it is the one SAS defines for this
endpoint and because its *schemalessness* is load-bearing — it is how the ban on `error_description` is enforced
structurally rather than by memory.

### F8 · An array reaches `usableTokens`'s cast

`typeof [] === 'object'`, so an array payload passes the first guard and is cast to `Record<string, unknown>`. It
then fails on `id_token` being `undefined` and returns `null`, which is the correct outcome — but the cast is
reached by a value it does not describe.

**Decision: no change.** Narrowing further would add a branch no input can distinguish from the one it already
takes. Noted so a reviewer who spots it knows it was seen. Phase 10 includes a 200 whose body is an array, so the
claim is pinned rather than argued.

---

## What I checked and found sound

- **R8's ordering survives a real transport.** `CallbackScreen.tsx:107-110` still returns before any exchange, and
  the nonce check still sits inside the fulfillment at `:124`, where it cannot see a callback the state check
  refused. T18's 134 cases and T19's 123 pass unchanged.
- **One outcome, by construction.** Six distinct faults reach `fail()`, which throws one fixed string. There is no
  second rejection shape to diverge, and the 400's schemalessness means the server's own words have no typed route
  into the SPA at all.
- **The storage read is guarded and normalizing**, matching `idTokenNonce.ts` rather than inventing a second
  convention, and nothing is written, deleted or cleared — the three L16 items survive both paths (AC9).
- **No module-level mutable state, no `console.`, no absolute address, no `import.meta.env`, no new dependency.**
  `oxlint` exits 0, so the exchange trips neither L6 rule: the generated client is the whole point of T20-D1.
- **`libs/ts/api-client` typechecks** with two clients and two generated modules, and the existing `auth` re-exports
  keep their unprefixed names, so no current import changed meaning.

## Open questions

**None new.** OQ-2 (where the access token lives) and OQ-3 (pinning the refresh-token absence against a live
server) stay deferred with the owners Phase 4 named. F3 and F4 are handoffs to task 21, not questions.

---

## Next — Phase 8 — independent code review. **Kimi 2.7.**
