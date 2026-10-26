# frontend · T19 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 19):**
> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

Phase 12's verdict is **PASS**, so this phase proceeds.

---

## Commit title

```
frontend T19: validate the ID token nonce against the stored value (R7)
```

## Commit message

```
frontend T19: validate the ID token nonce against the stored value (R7)

Reject a sign-in whose ID token does not carry the nonce this tab
stored. The state parameter ties the callback URL back to this tab,
which tasks 17 and 18 built; the nonce ties the ID TOKEN back to it,
and that is a different hop. A token minted legitimately for some
other login and substituted into this callback carries a perfectly
good state, and nothing but this check notices.

Add idTokenNonce.ts, which decodes the payload and compares the nonce
claim to sessionStorage, returning a reason the tests can distinguish
and the screen deliberately drops. The comparison is exact: no trim on
either side, no case folding, no prefix match, no length compare, no
coercion. A claim that is absent, not a string, or carried by a token
that is not three base64url segments of JSON object is rejected on the
same path - never as an escaping throw, because there is no error
boundary in this application and an uncaught throw would blank the
screen at the end of a sign-in.

Decode without verifying the signature. The token is handed over by
the token endpoint on the edge's own origin, and OIDC Core 3.1.3.7
lets a client that receives it that way rely on the channel instead.
That keeps L13 - no JWT library - and the tests pin it by accepting
tokens that differ only in their header or signature, so a later
reader cannot mistake the omission for an oversight.

Widen the exchange seam to yield an ID token and make it reject while
there is no transport behind it. A seam that must hand back a token
cannot honestly resolve while none exists: the only way to resolve
would be to fabricate one, and a fabricated token in production
source is how a check gets proven against something that will never
happen. Task 20 reverts that by returning a real response.

Route the exchange with .then(onFulfilled, discard) rather than a
trailing .catch, so a failure inside the fulfilment handler is not
then handed to the one outcome a second time. A missing state, a
mismatched state, a refused exchange and a bad nonce are four faults
with one outcome, and they share one discard closure rather than four
copies, which is what makes them indistinguishable by construction
(L4) instead of by assertion.

Split, rather than rewrite, the two task 18 assertions this changes.
A matching state now reads the nonce as well; a discard still reads
nothing but the state, and that row is load-bearing - it is what
would catch a nonce read creeping above the state check, where a
forged callback could reach it.

Measured against the running authorization server rather than assumed:
the token response carries no refresh_token; the id_token echoes the
nonce exactly and OMITS the claim entirely when none was sent; its
claim set differs from the access token's, so the two are not
interchangeable. One fixture in the new suite is a real token that
server minted, so the suite is not arguing only with shapes it built
itself.

Verifies callbackValidatesNonce (package.md:175). The code exchange,
the in-memory access token and the deletion of the stored items remain
tasks 20, 21 and 23.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

**Trailer deviation, as in T17 and T18.** The phase 13 template still asks for
`Co-Authored-By: Claude Opus 4.8 (1M context)`. The attribution in force for this session names
**Claude Opus 5**, and the model that did the work is Opus 5. The template line is stale; the trailer above is
correct. Still an open carry-forward against `.ai/generate.py`.

## Files changed

| File | Change | Lines |
|---|---|---|
| `frontend/src/auth/idTokenNonce.ts` | **new** — the decision: decode the payload, compare the `nonce` claim to the stored value, return a typed reason | +137 |
| `frontend/src/auth/callbackValidatesNonce.test.tsx` | **new** — the named test, 123 cases in eleven blocks, each with a control proving its assertion is reached | +939 |
| `frontend/src/auth/CallbackScreen.tsx` | the call site, inside the exchange's fulfilment and after the state check; `.then(f, discard)` | +36 / −14 |
| `frontend/src/auth/codeExchange.ts` | the seam yields `TokenResponse` and rejects until task 20 | +44 / −8 |
| `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | T18's two affected assertions split per T19-D6; the mock mints a token carrying the stored nonce | +87 / −3 |

Five files, all under `frontend/src/auth/`. **No dependency change** — `frontend/package.json` is untouched across
every T19 commit. No specification file modified.

## Summary

R7's first clause, and only that clause. A callback that survives both halves of R8 is exchanged, and the ID token
that comes back is accepted only if its `nonce` is the value this tab stored. Everything else R7 names — the
exchange at `/oauth2/token` with the PKCE verifier, and holding the access token in memory only — belongs to tasks
20 through 22, by `tasks.md`'s own division.

The work divides into a pure decision and a wiring. The decision is a module with no state, which reads one stored
item through a guarded accessor (`sessionStorage` can **throw**, not merely return `null`, where site data is
blocked) and never writes, deletes or clears anything: deleting the three stored items is task 23's. The wiring is
one line at one call site, inside the exchange's fulfilment, which may not be hoisted above the state check — R8
requires a bad state to discard before any nonce read, and the test asserts that ordering from both sides.

Three defects were found and fixed during review rather than after it. The decision took a `string` and called
`.split` before any guard, so a non-string threw out of a function documented as never throwing — reachable
through task 20's real response, since the endpoint returns no `id_token` whenever `openid` is not among the
authorized scopes. The visitor outcome had stayed correct only because a `.catch` written for network failures
rescued a `TypeError` it was not written for. A trailing `.catch(discard)` could run the single outcome twice. And
the test helper could not build a non-ASCII payload at all, which left the UTF-8 decode unfalsifiable.

## Testing performed

- **`callbackValidatesNonce`** — **123 cases, all passing**, exit 0. Two layers on purpose: the decision is
  exercised directly for the shape matrix, and through the real route table for the outcome, because this task's
  phase 6 found that a test double resolving the wrong shape silently defuses the check. Every block carries a
  control proving its assertion is **reached**, not merely satisfied.
- **Whole frontend suite** — **818 passed / 1 failed of 819.** The one failure is
  `lintRejectsHandWrittenBackendFetch` **AC4**, environmental: it expects the summary line
  `Found 0 warnings and 0 errors`, and this host's `oxlint@1.87.0` prints nothing. `oxlint` over `src/` exits 0.
  **AC2 of that same test is a different fault** — `spawn pnpm ENOENT`, which passes under
  `npx -y pnpm@12.14.0`. The two have been conflated four times in review and are distinct.
- **`tsc --noEmit`** — clean. **`oxlint src/`** — exit 0.
- **Mutation — 19 mutants, 19 killed, no survivors** (baseline 0 failed of 257 collected). Four were added for
  this phase's new guards: a header read insisting on RS256, a module variable retaining the token, a log of the
  claim, and a log of the reason. Four had survived the first run and were closed; the most instructive put a
  non-ASCII value in a claim **beside** the nonce rather than in the nonce itself, so latin-1 mangling changed
  nothing the comparison looks at — the case exercised the path without exercising the property.
- **Measured against the running stack**, not assumed: the token response's keys and the absence of
  `refresh_token`; the id_token's claim set and its omission of `nonce` when none is sent; a replayed code
  returning `400 invalid_grant`. One real minted token is committed as a fixture.

## Specification references

- **Task:** `spec/frontend/tasks.md` task 19.
- **Requirements:** **R7** (`requirements.md:23`) — the nonce clause. **R8** (`requirements.md:25`) is unchanged
  and re-asserted from both sides.
- **LOCKED decisions:** **L1** (the SPA is the OIDC client and validates for itself) — implemented. **L4** (no
  copy distinguishing one failure from another) — held by construction, one `discard` closure for four faults,
  and asserted against rendered text, the tab title and the console. **L13** (no JWT library) — the payload is
  decoded with platform primitives and the signature is not verified. **L16** (the three stored items) — all
  three survive on both the accept and the reject path. **L9**/**L10** (one origin at the edge, no bundled
  issuer) — no address and no `import.meta.env` read, both scanned. **L3** — informed by the measured absence of
  `refresh_token`, but implemented by tasks 20–22. **L6** is not reached: T19 adds no transport.
- **Named test:** `callbackValidatesNonce` → R7 (`package.md:175`).
- **Standing rules:** `spec/frontend/agents.md`, in particular `:44` — response timing is outside the SPA's
  control, which is why AC10 compares the visible outcome and not the network.

## Merge notes

Branch off `main`; `main` stays deployable. Nothing in this task is reachable by a visitor who has not completed a
SAS login, and the only behaviour change for one who has is that a sign-in now ends at the sign-in start with
neutral copy instead of waiting on the callback screen — which is the more truthful of the two while the exchange
genuinely cannot complete. Task 20 restores completion.

**Carried to task 20:** pin the absence of `refresh_token` with a test rather than the comment at
`codeExchange.ts:19-25` (Kimi's I5); resolve L6 against `/oauth2/token`, which is not in `auth.yaml` because OIDC
endpoints are SAS-owned (`design.md:133`) while `.oxlintrc.json` bans `fetch` through every global alias (OQ-5).

**Still open, gating nothing:** CI enforcement of the suite total and confirmation that the `oxlint` AC4 failure is
identical there (T18's Gap 6 — the one claim in this package asserted from a single host); `/oauth2/revoke` for a
rejected token (OQ-3, the author); the ID token has no contract document (OQ-4, `contracts/`); two documented
facts measured false — `contracts/api/token-claims.md` places email and name in the `id_token` or `/userinfo`,
where neither appears, and discovery advertises one scope for a three-scope client; `design.md` §4c's stale
"PROPOSED, pending sign-off (Q6)" heading and its now-false "auth T19 is not built" line; `package.md:172` mapping
`serviceWorkerUpdateWaitsForReload` to R57; the phase 13 template's stale trailer.

---

**T19 is complete: 14 of 14 phases, verdict PASS.**
