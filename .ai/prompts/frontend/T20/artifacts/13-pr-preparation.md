# frontend · T20 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. **T20 is complete: 14 of 14 phases.**

---

## Commit title

```
frontend T20: exchange the authorization code with the stored PKCE verifier
```

## Commit message

```
frontend T20: exchange the authorization code with the stored PKCE verifier

Replace the throwing seam in codeExchange.ts with a real POST /oauth2/token
carrying the stored code_verifier. checky-spa is registered with
ClientAuthenticationMethod.NONE, so there is no client secret and the stored
verifier is the only proof that the code is being redeemed by the tab that
requested it - which is why the suite proves the value travels over two
unrelated stored values rather than proving a verifier field is present.

Describe the token endpoint in a new contract, contracts/api/oidc.yaml, and
reach it through a client generated from it. L6 requires every backend call to
go through a client generated from contracts/api/*.yaml and names one
exception, the notification stream; auth.yaml deliberately excludes the SAS
protocol surface. A second file under the same glob satisfies L6 as written,
so there is no ADR, no new exception and no lint suppression, and auth.yaml's
scope note is untouched.

Send no custom body serializer. openapi-fetch's stock serializer form-encodes
when the request's Content-Type is exactly application/x-www-form-urlencoded,
and an explicit header overrides the JSON default it would otherwise apply.
The comparison upstream is ===, so the value must be byte-identical; a case in
the client package demonstrates that one charset parameter silently yields
JSON.

Give the contract's 400 response no schema. SAS answers there with error and
error_description, and L4 forbids the visitor learning which check failed, so
a generated client that offers no typed way to read that detail enforces the
rule structurally. Every failure - a missing verifier, a refused code, a 5xx,
a dropped connection, an unparseable body, a response with no usable tokens -
becomes one opaque rejection carrying nothing.

Export redirectUri() so the authorize request and the token request derive one
string rather than two that agree today. The authorization server matches it
exactly against the registered value, and two spellings differ only on the day
one of them changes.

Leave the access token's home to task 21: the exchange returns it and stores
nothing. All three sessionStorage items survive both outcomes; deleting them is
task 23.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

**Note on the trailer.** The Phase 13 template still asks for a
`Claude Opus 4.8 (1M context)` trailer. That is stale — it has been carried as a known defect since T18 — and the
session's standing attribution rule supersedes it. The trailer above is the correct one.

## Files changed

**Created**

| File | Lines | What |
|---|---|---|
| `contracts/api/oidc.yaml` | 99 | The token endpoint only. **Requires CODEOWNERS review** (`.github/CODEOWNERS` maps `/contracts/` to `@ofspain`). |
| `libs/ts/api-client/src/generated/oidc.ts` | 96 | Generated, never hand-edited, drift-checked. |
| `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | 918 | The named test (`package.md:111` → R7), verifier half. 69 cases. |

**Modified**

| File | What |
|---|---|
| `frontend/src/auth/codeExchange.ts` | The seam becomes the exchange: guarded read, form body, one rejection. |
| `frontend/src/auth/authorizeRequest.ts` | `redirectUri()` exported. One keyword. |
| `frontend/src/auth/CallbackScreen.tsx` | Comments only — the latch's in-flight window, and the discard being the whole outcome. |
| `libs/ts/api-client/src/index.ts` | `createOidcClient()` beside `createAuthClient()`; `oidc`-prefixed type aliases. |
| `libs/ts/api-client/package.json` | `generate` runs both contracts. |
| `frontend/src/api/generatedClientCompiles.test.ts` | +4 cases: the drift guard, the two-contracts split, form encoding, and the `===` trap. |
| `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` | One `ALLOWED` entry naming the OIDC client. |
| `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | One case replaced: it asserted the seam names an unfinished task, which T20 made false. The replacement pins the stronger property that suite depends on — that the exchange it watches is a real request. |

**1,339 insertions, 33 deletions across 11 files.** No file outside the frozen brief's authorization. `spec/` is
untouched; `.oxlintrc.json` is untouched; `docs/adr/` is untouched (T20-D10).

## Summary

A callback that survives both halves of R8 now completes. The code is exchanged at `/oauth2/token` through a
generated client, the stored verifier goes with it as the sole client proof, and T19's nonce check finally runs
against a token an authorization server really minted rather than a fixture.

The task had **one genuine blocker** and the owner closed it: the token endpoint was in no contract while L6
requires a generated client, and `auth.yaml` excludes the SAS surface on purpose. Of the three exits — a second
contract file, an ADR adding a second exception to L6, or editing `auth.yaml` — the owner chose the first, which
is the only one that satisfies the rule as written rather than amending it. Ten numbered decisions, `T20-D1`
through `T20-D10`, are frozen in `artifacts/04-frozen-task-brief.md`.

## Testing performed

- **Suite: 892 passed / 1 failed of 893.** The failure is `lintRejectsHandWrittenBackendFetch` AC4 at `:113` —
  this host's `oxlint@1.87.0` prints no summary line — **and no other**, read from the failure list rather than
  inferred from counts.
- **`vitest` exit 1**, which is that single failure and **no unhandled rejection**: no `Errors` line. AC17 exists
  because T19 found an unhandled rejection reported *below* a line saying every test passed.
- `tsc --noEmit` clean for `frontend` and `libs/ts/api-client`. `oxlint` exit **0**.
- **Mutation: 24 mutants, 24 killed, no survivors**, baseline 0 failed of 326.
- The named suite's **69 cases** cover AC1–AC23; AC19 is left to the case that already pins it
  (`serviceWorkerNeverCachesApiResponses.test.ts:169-175`) rather than duplicated.

**Three findings came from the work rather than from a review, and all three are in the artifacts:**

1. **A mutant survived.** Removing the `try/catch` around the verifier's storage read killed nothing: the function
   is `async`, so an unguarded read rejects too. The case asserted only "it rejects" and so could not see the
   guard at all. It now compares the rejection's message and name against the one message every failure produces.
2. **Three of my own assertions were inert.** `takeSignInNotice()` in the screen cases read a *different module
   instance* from the one the screens hold, because `arriveAt` imports the route graph dynamically and
   `vi.resetModules()` runs between cases. They would have passed whatever production did. Removed; the notice is
   now proven through the rendered page.
3. **The mutation harness was flattering itself.** A mutant whose run failed to parse was scored `KILLED` because
   its total differed from the baseline. It now scores `BROKEN`, and the one mutant that benefited is genuinely
   killed after the fix.

## Specification references

- **Task:** `spec/frontend/tasks.md` task **20** — "Code exchange sends the verifier."
- **Requirements:** **R7** (exchange at `/oauth2/token` with the PKCE verifier; the in-memory clause is task 21),
  **R8** (a bad `state` completes no exchange — preserved and newly falsifiable).
- **LOCKED decisions:** **L1** public client/PKCE/exact-match redirect, **L3** tokens never stored and no refresh
  token, **L4** enumeration safety, **L6** generated client, **L9** single origin, **L10** no secret or env key,
  **L11** catalogue-only strings, **L13** confirmed stack, **L14** `/app` routes, **L16** the sessionStorage
  carve-out, **L17** NetworkOnly, **L18** one renewal per tab.
- **Named test:** `callbackValidatesNonceAndKeepsAccessTokenInMemory` (`package.md:111`), verifier half; task 21
  adds the in-memory half.
- **Contracts:** `contracts/api/oidc.yaml` (new), `contracts/api/auth.yaml` (unchanged, and its scope note is
  load-bearing).

## Carried forward

**To task 21, which owns the access token:**
- `expires_in` is dropped at the boundary and L18's renewal needs it; add it to `TokenResponse` when building the
  thing that holds the token (Phase 7 F3).
- **Do not put the session boundary's renewal middleware on the OIDC client.** Renewal is silent
  re-authentication ending in a token exchange, so a 401 hook here would make a failed exchange trigger a renewal
  that performs another exchange. Now enforced by a scan, but task 21 is the task that will be tempted
  (Phase 7 F4).

**To task 24, which owns the return target:** when the exchange rejects after the callback screen has unmounted,
the visitor is pulled to the sign-in start. T20 did not introduce this and the brief gives it no AC; it is pinned
as current behaviour, not endorsed.

**Open, gating nothing:** `credentials: 'omit'` on the token request would be a defensible tightening — `fetch`
sends the same-origin SAS session cookie, which the endpoint does not need; CI enforcement of the suite total and
of the `oxlint` failure's identity (T18 Gap 6 — still the one claim in this package asserted from a single host);
`contracts/api/token-claims.md` measured false on email/name; discovery advertising one scope for a three-scope
client; the ID token has no contract document (T19 OQ-4); `design.md` §4c's stale "PROPOSED, pending sign-off"
heading and its now-false "auth T19 is not built" line; `package.md:172` mapping `serviceWorkerUpdateWaitsForReload`
to R57; and this template's stale trailer.

---

## T20 — COMPLETE. 14 of 14 phases. Phase 12 verdict: **PASS**.
