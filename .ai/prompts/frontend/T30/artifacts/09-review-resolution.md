# frontend · T30 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/07-self-review.md` + `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Kimi returned **eight findings**. **All eight accepted** — two with their recommended form refused, one
accepted as a measurement rather than a change. Every citation was checked against source first, and the two
that turn on library or boundary behaviour were **measured** rather than reasoned about.

**This was the strongest of Kimi's three reviews on this task, and I1 was a defect I had shipped.** The fixes
are applied, committed and pushed; **46 cases, no survivors** — see the correction below for the score.

> **CORRECTION, entered 2026-10-31 during phase 11.** The mutation score stated below is wrong, and it
> was wrong when it was written. Checking every anchor against source showed that `GET_CALL` had been stale
> since the I1 fix in this very phase rewrote the `api.GET` call, so **MP06 and MP07 printed
> `BROKEN anchor missed` rather than `KILLED`.** A `BROKEN` mutant never reaches the survivors list, so the
> run still ended `survivors: none` and was reported as 28 of 28. **It was 26 killed and 2 never applied.**
> The kills were real; the denominator was not. Phase 11 refreshed the anchors, added a `preflight()` that
> refuses to start until every anchor matches exactly once, and the corrected run is **34 of 34 killed, no
> survivors, nothing BROKEN** — MP06 alone now fails 21 cases. This is T29's drifting-baseline lesson one
> level up: a harness that scores an unapplied mutant as a non-survivor lies by omission.


---

## I1 — ACCEPTED. The one High-confidence finding, and it earned the rating

`readProfile` awaited `api.GET(...)` with no `try/catch`. `openapi-fetch` resolves an HTTP error status but
**rejects** when the underlying `fetch` throws.

**Measured before fixing** — a throwaway probe with `fetch` rejecting:

```
CONSTRUCTOR: TypeError
MESSAGE:     Failed to fetch: net::ERR_NAME_NOT_RESOLVED at <an internal hostname>
HAS_STACK:   true
```

That exact `TypeError` escaped `readProfile` with its message and stack intact. An internal hostname and a
browser error code reaching a caller is precisely what AC8 forbids — and **no status check could have caught
it, because no response existed.** Every assertion in AC8 was about a response, so the whole clause had a
hole shaped exactly like a network failure.

Fixed with a `try/catch` converting anything thrown into `ReadProfileError('unavailable')`. Three shapes are
now driven: a `TypeError`, a `DOMException` abort, and a thrown string. `MP24` restores the defect and
`MP25` reports the failure with its message; both are killed.

## I4 — ACCEPTED, and sharper than it first reads

The `/userinfo` prose said "authenticated" and `POST /oauth2/token` declares `security: []`, so the two
**looked** different. In OpenAPI 3 they were not. An operation with no `security` inherits the global
requirement; `oidc.yaml` declares no global requirement and no `securitySchemes`; so the result was **no
requirement at all** — semantically identical to the `security: []` it was being contrasted with.

**The distinction existed only in the text**, which means AC1's assertion could only ever have been textual —
and Phase 6 §2 had already congratulated itself on making that assertion line-wise, without noticing it was
asserting something with no OpenAPI meaning.

Fixed properly: `components.securitySchemes.bearerAuth` is declared and `/userinfo` requires it, so the
document says a bearer is required. AC1 now asserts a **positive requirement** rather than the absence of its
opposite. `MP28` removes it again and is killed.

## I2 — ACCEPTED

`Profile` was hand-written and happened to match `UserInfoResponse`. Matching today is not the property worth
having: a contract gaining `middle_name`, or changing `picture` to an object, would leave a hand-written copy
compiling and silently wrong. **Phase 4 froze this as F10 and the first draft quietly did not honour it** —
which is worth stating plainly rather than presenting the fix as an improvement.

Now `export type Profile = oidcComponents['schemas']['UserInfoResponse']`.

## I5 — ACCEPTED, and the first version of the fix was too weak to catch its own mutant

A 429 through `createOidcApi` was untested, so nothing held the new factory to R23. A case was added — and
`MP27`, which swaps the registration order, **survived it.**

The reason is the interesting part. With a 429 on the *first* call there is only one response to see, so both
orderings show the notice. `client.ts`'s own comment names the case that distinguishes them: *"a 429 on the
session boundary's re-send after a renewal."* So the case now drives **401 then 429**. openapi-fetch runs
`onResponse` in reverse registration order, so a notice registered first runs **last** and sees the final
429; registered after the boundary it runs first, sees only the original 401, and the visitor is rate-limited
with no copy saying so. `MP27` is now killed.

## I3 — ACCEPTED as option (a), and the first mutant for it was not a defect

`ReadProfileError.message` is English and is not catalogue copy, so a screen rendering `error.message` would
render a string L11 requires to be in the catalogue. The trap is fair: `error.message` is the obvious thing
for a first consumer to reach for.

Documented in the class, and asserted by **inversion**: none of the three messages appears anywhere in the
catalogue, so rendering one cannot be mistaken for rendering commissioned copy. A consumer maps `failure` — a
closed set of three, for exactly this purpose — onto a key of its own.

`MP26` first changed `'No session.'` to `'Sign in to continue.'` and **survived, correctly**: nothing requires
an internal message to have particular words, and the inversion test asks whether a message *is* catalogue
copy, not whether it sounds like it. **A mutant with no defect behind it proves nothing in either direction** —
the same judgement MP09 needed in Phase 7. Re-aimed at the defect I3 actually names, a message
*indistinguishable* from commissioned copy, using an exact catalogue string. Killed.

## I8 — ACCEPTED

The contract declared only a `302` while the client handles the whole 3xx range. Resolved by **documenting**
rather than narrowing the client: a redirect away from a protected endpoint means the session is gone whichever
code SAS picks, and a client that accepted 302 while parsing a 303 as a profile would be a strange thing to
have built on purpose. 302 is recorded as the only status measured.

## I6 — ACCEPTED as a MEASUREMENT, not as a restructuring

The race is real and correctly described: `readProfile` reads `tokens.current()` synchronously, the boundary's
`onRequest` reads it again when attaching the bearer, and between them a cross-tab renewal failure, a sign-out
or a `clearAccessToken()` can clear it. Verified in source — `sessionBoundary.ts`'s `onRequest` calls
`tokens.current()` itself.

Kimi's own first option is the right one, and a case is now the evidence for it: a token present at the
reader's check and gone at the boundary's read produces **a request with no bearer** — stated, not hidden —
**and still ends in a `no-session` failure**, because `redirect: 'manual'` and the 3xx check make it so.

What remains is a narrow side-effect window: that bearer-less request provokes the `302` and its
`Set-Cookie: JSESSIONID`. Closing it properly means moving the guard into the boundary's `onRequest`, which is
`api/sessionBoundary.ts` — named in the frozen brief as not T30's, and shared by every other client. Recorded
as **T30-D6** rather than half-closed here.

## I7 — ACCEPTED IN SUBSTANCE, FORM REFUSED

The gap is real and my own S3 had named it. But the recommendation cannot be adopted: it asks for a control
asserting the scan **flags** an alternate decode helper (`jwtVerify`, `decode`). **The scan does not flag
them**, so that assertion would simply fail — the recommendation is self-contradictory unless the pattern is
widened first, and widening it to a bare `decode` would hit `new TextDecoder().decode(…)` in commissioned
code, which Phase 6 §4 measured as a class of failure.

So the boundary is recorded as a **negative control** — these are the shapes the scan does not see — and
guarded by something that actually fires: **no JWT-decoding library is a dependency**, checked across both
manifests against seven names. That is the event which makes the gap live, and it is checkable. The day `jose`
or `jwt-decode` enters a `package.json`, that case fails and the pattern must widen before anything else can
proceed.

---

## What the phase cost elsewhere

**An inherited instrument fired on my own prose.** `shellBuildsAndMounts` scans app source for `https?://`
**without stripping comments** (L9, L10, R58), so the measured URL written into a comment failed it. Rephrased
rather than loosening another task's scan: a scanner cannot tell a comment from a constant, and its
conservatism is reasonable. This is the second time in T30 that the package's own documentation collided with
an assertion — Phase 6 §2 was the first — and it is a standing hazard in files this densely commented, not a
coincidence.

## Verification at the gate

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **46 / 46**, exit 0 |
| Whole suite | **1444 passed / 1 failed (1445)** — the known `lintRejectsHandWrittenBackendFetch` environmental failure |
| Mutation | **26 killed, 2 BROKEN (stale anchors), no survivors** — reported at the time as 28 / 28; corrected in phase 11 to **34 / 34** |
| `generatedClientCompiles` | 10 / 10 |

## Deviations

| ID | Deviation |
|---|---|
| **T30-D1** | `/userinfo` returns only `{"sub"}` live (`spec/auth-service/design.md` **O6**). Both shapes are proven; the end-to-end read is nobody's to demonstrate until O6 closes |
| **T30-D2** | `/userinfo` added to `contracts/api/oidc.yaml`, reaching outside `frontend/` and regenerating a shared client. Owner-authorized 2026-10-31 |
| **T30-D3** | No screen consumes the profile; `/app/account` is unbuilt |
| **T30-D4** | The boundary is built but unwired by the composition root, and no production `renew()` exists. `readProfile` takes both as parameters |
| **T30-D5** | `tokensAreNeverWrittenToAnyStorage`'s `this.name =` count raised from three to four. The one existing test file T30 touched |
| **T30-D6, new** | The token-clearance window between the reader's check and the boundary's read is measured and accepted. The outcome is safe; closing the side-effect window needs `sessionBoundary.ts`, which is out of scope and shared |

## Scope confirmation

- **Files created:** `profile/readProfile.ts`, `profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx`.
- **Files modified:** `contracts/api/oidc.yaml`, `libs/ts/api-client/src/generated/oidc.ts` (regenerated),
  `frontend/src/api/client.ts`, `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` (T30-D5).
- **Files NOT modified:** `spec/**` beyond the authorized O6 entry; `services/**`; `api/sessionBoundary.ts`,
  `api/crossTabRenewal.ts`, `api/rateLimitNotice.ts`.

---

**Phase 9 complete. Approved to proceed.** Eight findings accepted, two forms refused on measurement, one
accepted as a measurement. Phase 10 maps the cases.
