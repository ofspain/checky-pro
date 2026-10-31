# frontend · T30 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase prepares the task for merge.

---

## Commit title

```
feat(frontend/T30): read the profile from /userinfo, and prove no token is read instead
```

## Commit message

```
feat(frontend/T30): read the profile from /userinfo, and prove no token is read instead

Add `profile/readProfile.ts` and the named test it answers for,
`profileIsReadFromUserinfoAndAccountStatusFromMe`. R20's first clause
says profile data comes from /userinfo; its third says PII and
verification state never come from the access token. T30 builds the
first and proves the third. R20's second clause - account status from
GET /accounts/me - is T31's, by the split frozen in phase 4.

The third clause is why this task is mostly verification. Reading
`email` or `email_verified` off the access token produces a plausible
value, no error, and no behavioural difference any runtime test could
see, so it is caught by a source scan or by nothing. The scan is two
instruments composed, forced by measurement: `email`, `name` and `sub`
are ordinary words this package uses constantly, so a tree-wide claim
scan proves nothing. A decode scan bounds the files that touch a
token's payload to exactly one - `auth/idTokenNonce.ts`, R7 nonce
validation, asserted as an upper AND lower bound - and the claim scan
runs only over that set.

The task's premise was measured FALSE, which shaped the rest. Against
the running stack with a real interactive token carrying
scope: ["openid","profile","email"], GET /userinfo answers
200 {"sub":"<uuid>"} and nothing else. The scopes ARE granted; Spring
Authorization Server returns `sub` alone unless an OIDC user-info
mapper is configured, and auth-service configures none. Recorded as
spec/auth-service/design.md O6 with reproduction commands, under the
owner's explicit authorization to edit that file. The reader is written
against the shape the CONTRACT declares, so closing O6 needs no change
here, and a case proves the full shape parses.

Two further measurements changed the design rather than decorating it.
A bearer-less GET /userinfo does not answer 401: it answers 302 to
/login with a Set-Cookie: JSESSIONID, and `fetch` follows that into
200 text/html carrying the login page - which openapi-fetch would hand
to a caller expecting a profile, on a success status. There is no 401
anywhere in that sequence for the session boundary's renewal to act on,
so "let the boundary handle a missing session" was not available. Hence
the no-token short-circuit, `redirect: 'manual'`, and a redirect check
that runs before the body is looked at. And a malformed bearer answers
401 with an EMPTY body, putting its detail in a WWW-Authenticate header
- so a no-leak rule written about response bodies would have missed the
only place the detail was.

contracts/api/oidc.yaml gains GET /userinfo, UserInfoResponse
(required: [sub], every other claim optional) and a bearerAuth security
scheme; the client is regenerated, never hand-edited. That file's own
description says adding a path is a decision of its own and not a
drive-by, so it was taken to the owner and authorized first.
api/client.ts gains createOidcApi and OIDC_PUBLIC_OPERATIONS, which
holds exactly POST /oauth2/token: /userinfo is the first OIDC
operation needing a bearer and a renewal.

Verification: 53 cases, 12 of them controls, over 25 instruments each
shown to report dirty first, and 34 of 34 mutants killed with no
survivors and nothing BROKEN. Suite 1451 passed / 1 failed - the one
being the pre-existing lintRejectsHandWrittenBackendFetch, an oxlint
summary-line quirk of this host. tsc --noEmit and oxlint src both exit
0.

Three adversarial reviews found real defects, and the verification is
mostly their doing. Phase 8 found that openapi-fetch RESOLVES an HTTP
error status but REJECTS when the transport throws, so a raw TypeError
carrying an internal hostname escaped the reader - a hole no status
check could have covered, because no response existed. Phase 11 found
two more: a 200 whose body is the JSON literal `null` reached
`typeof data.sub` and threw a raw TypeError out of the function, and
the token guard tested `=== null` so an empty string passed it. Both
are fixed. Both were verified by running the cited line, which mattered
- one of phase 11's stated mechanisms was wrong in a way that changed
the diagnosis, and a recommended control was refused twice as
self-contradictory.

Deviations: T30-D1 /userinfo returns sub alone live (auth-service O6);
T30-D2 the contract change reaches outside frontend/, owner-authorized;
T30-D3 no screen consumes the profile yet; T30-D4 the boundary is built
but unwired, so it and the token accessor arrive as parameters;
T30-D5 one inherited assertion count raised by one; T30-D6 the token
clearance window between the reader's check and the boundary's read is
measured and accepted, because closing it means changing a boundary
every client shares; T30-D7 document.cookie and indexedDB are declared
unswept, since jsdom gives an empty cookie jar and no indexedDB.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/profile/readProfile.ts` | **new**, 153 lines — the reader, `Profile` derived from the contract, `ReadProfileError` neutral by construction |
| `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | **new**, 1379 lines — the named test; T30 owns imports, setup, helpers and every `describe('T30: …')` |
| `contracts/api/oidc.yaml` | `GET /userinfo`, `UserInfoResponse`, `securitySchemes.bearerAuth` (**T30-D2**, owner-authorized) |
| `libs/ts/api-client/src/generated/oidc.ts` | **regenerated**, never hand-edited |
| `frontend/src/api/client.ts` | `createOidcApi`, `OidcApi`, `OIDC_PUBLIC_OPERATIONS` |
| `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | one inherited count, three → four (**T30-D5**) |
| `spec/auth-service/design.md` | **O6** — the only `spec/` edit, explicitly authorized by the owner |
| `.ai/prompts/frontend/T30/artifacts/` | the fourteen phase artifacts, including the mutation harness |

**1757 insertions, 4 deletions** across six code files, in 24 commits.

## Summary

T30 reads the visitor's profile from the OIDC user-info endpoint and establishes, by scanning the tree rather
than by convention, that no module reads PII or verification state out of a token the client happens to be
holding.

The reader is small on purpose: one function, one error class, one derived type, no renewal logic and no token
lifetime of its own. The session boundary supplies the bearer and owns the 401; the reader takes both the
client and the token accessor as parameters, so the composition root stays exactly as unwired as it already is.

What is not small is the evidence, and that is the shape R20 forces. Its third clause cannot fail loudly —
reading a claim off a token returns a plausible answer — so the verification has to be an absence proof, with
every instrument shown to report dirty before it is trusted, and a mutant written specifically to escape
every net by sitting in a module nothing imports.

## Testing performed

| | |
|---|---|
| `profileIsReadFromUserinfoAndAccountStatusFromMe` | **53 / 53**, exit 0 |
| Whole frontend suite | **1451 passed / 1 failed (1452)** — the one pre-existing and environmental |
| `generatedClientCompiles` | 10 / 10 |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0, no output |
| Mutation | **34 / 34 killed**, no survivors, nothing BROKEN, every one by T30's own file |
| Live measurement | `infra/local/bin/up`, `dev-account USER`, then `curl` against `GET /userinfo` with a real token, with a malformed bearer, and with none |

The harness is committed at `.ai/prompts/frontend/T30/artifacts/07-mutation-harness.py` and reverts with
`git checkout -- src/`, so tests are committed before it runs. It **asserts its own baseline and its own
anchors** and exits non-zero on a survivor — both guards exist because both failures happened: T29's baseline
drifted and converted two kills into reported survivors, and T30's own `GET_CALL` anchor went stale when the
phase 8 fix rewrote the call it pointed at, so two mutants printed `BROKEN`, which is not `SURVIVED`, and a
26-killed run was published as 28 of 28.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task **30** |
| **Requirements** | **R20** (a) and (c) — (b) is T31's. Preserved, not changed: **R7**, **R21**, **R23** |
| **LOCKED decisions** | **L3** in-memory token · **L4** neutral copy · **L6** generated client only · **L11** catalogue copy · **L16** storage limits · **L18** one renewal per tab |
| **Named tests** | `profileIsReadFromUserinfoAndAccountStatusFromMe` (built) · `generatedClientCompiles` (passing) · `authorizeRequestsExactlyTheDeclaredScopes` (T15's, untouched) · `emailVerifiedClaimIsNotAccountState` (**T32's**) |
| **Contracts** | `contracts/api/oidc.yaml` (changed, authorized) · `contracts/api/token-claims.md` (read, and a long-standing note about it corrected) |
| **Branch** | `spec/service-specs-and-ai-framework`; `main` stays deployable |

## Open questions carried forward

- **`spec/auth-service/design.md` O6** — no OIDC user-info mapper, so `/userinfo` returns `sub` alone. Blocks
  the end-to-end profile read for every consumer, not just T30.
- **T30-D6** — the token-clearance window; closing it means changing `api/sessionBoundary.ts`.
- **OQ1** — the live SAS `/login` renders no `mfaCode` field while the backend requires it in the same POST.
- Unowned by any task: `mountShell.tsx`, `oidcRoundTripEndToEnd` (no Playwright suite), **L16 clause (b)**.
- **The phase 13 template's trailer is stale** — it asks for `Claude Opus 4.8 (1M context)`, and the
  session's standing attribution is `Claude Opus 5`. Noted in **twelve consecutive tasks** now. The one-line
  source is `.ai/generate.py:476`; regenerating the 382 affected prompt files is a separate, mechanical
  action and not T30's to take.

---

**Phase 13 complete. T30 COMPLETE** — 14 of 14 phases, verdict **PASS**, seven declared deviations, two
production defects found by review and fixed, and one published mutation score corrected.
