# frontend · T30 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/02-task-implementation-brief.md` + `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

Kimi returned **ten findings**: one Blocker, two Major, seven Minor. **Eight accepted** (two with their
recommended form refused), **one rejected on fact**, and **one accepted with its premise replaced by a
measurement that found something worse.** Every cited `file:line` was checked against source, and the three
findings that turn on live server behaviour were measured against the running stack rather than reasoned about.

**The review's own best finding was the Blocker, and the measurement's was bigger than any of them.** A
bearer-less `GET /userinfo` does not return 401; it returns **302 to `/login`**, which `fetch` follows by
default into **HTTP 200 `text/html`**. Details in F5.

---

## Dispositions

### F1 · Blocker — ACCEPTED, verified exactly as cited

`idTokenNonce.ts:53-81` is as described: `split('.')` at `:61`, `globalThis.atob` at `:71`, `JSON.parse` at
`:75`. AC5's scan as briefed would have failed on committed, spec-required code (R7 nonce validation) the
moment it was written. Kimi found this by reading, and it is the single most useful thing in the review.

**Two corrections to the recommended fix.** The real code also uses `TextDecoder` and `Uint8Array.from`, which
Kimi's three-form row omits, so a row listing only `split`/`atob`/`JSON.parse` would leave two decode forms
unaccounted for. And a **measurement settles the scope of the allowlist**: `grep` for `atob`, `JSON.parse`,
`.sub` and `split('.')` across all production source returns **`idTokenNonce.ts` and nothing else**. The
allowlist is exactly one row, and that is an assertion, not an assumption:

```
PERMITTED_TOKEN_READS = [{
  file: 'auth/idTokenNonce.ts',
  forms: ['split', 'atob', 'TextDecoder', 'Uint8Array.from', 'JSON.parse'],
  claim: 'nonce',
  why: 'R7 nonce validation only. No PII and no verification state is read.',
}]
```

Upper **and** lower bound, as T29's timer allowlist is: a row for a decode that has since been deleted leaves
budget behind, which is how an allowlist stops being evidence. A control removes the row and asserts the scan
flags the file (AC10, T22-D14).

### F2 · Major — ACCEPTED

The substance was already in the Phase 2 brief ("`GET /userinfo` is **not** public, so unlike
`POST /oauth2/token` it needs a bearer and needs renewal"), but Kimi is right that the brief named no set, and
the factory cannot distinguish the two operations without one. Adopted in the recommended form:

```ts
export const OIDC_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set(['POST /oauth2/token']);
```

passed to `boundary.middleware(OIDC_PUBLIC_OPERATIONS)` inside `createOidcApi`.

### F3 · Major — ACCEPTED, and EXTENDED by measurement

Kimi is right that RFC 9457 does not govern SAS's protocol surface. **Measured, 2026-10-31**, with a malformed
bearer:

```
HTTP/1.1 401 Unauthorized
Content-Length: 0
Www-Authenticate: Bearer error="invalid_token",
  error_description="An error occurred while attempting to decode the Jwt: Malformed token",
  error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
```

Neither `application/problem+json` nor an OAuth2 error **body** — the body is **empty**, and the detail is in
a **header**. That internal detail ("while attempting to decode the Jwt") is exactly what AC8 must not
surface, and **a requirement written about bodies would miss it entirely.** Neither the Phase 2 brief nor
Kimi's amendment mentions the header. AC8 is rewritten to cover it.

### F4 · Minor — ACCEPTED IN SUBSTANCE, carve-out REFUSED as describing nothing

`sub` belongs on the forbidden list; an account UUID is linkable. But Kimi's proposed exception — "permitted
only for transport-bound signing/audience checks performed by the boundary" — describes machinery that does
not exist. **Measured:** `sessionBoundary.ts` passes the token through as an opaque string and decodes
nothing, and the only decode anywhere in production is `idTokenNonce.ts`, which reads `nonce`. So `sub` is
forbidden **outright, with no exception**, which is both simpler and stronger than the carve-out.

### F5 · Minor — ACCEPTED, premise REPLACED, and this is the most consequential item in the phase

Kimi predicted: bearer-less call → 401 → silent renewal → failure → the reader gets a 401, making "no session"
indistinguishable from "renewal failed". **Measured, and it is not what happens:**

```
GET /userinfo        (no Authorization header)
→ 302 Found,  Location: /login,  Set-Cookie: JSESSIONID=…

GET /userinfo -L     (following redirects, as fetch() does by DEFAULT)
→ HTTP 200,  content-type: text/html,  1103 bytes of the SAS login page
```

Three consequences, none of which the review anticipated:

1. **The 401 path never runs.** The boundary's single-flight renewal is keyed on 401, and there is no 401.
2. **`openapi-fetch` sees HTTP 200** and attempts to parse the `/login` HTML as `UserInfoResponse`. A missing
   session becomes a parse failure or an undefined body on a successful status — the worst available shape.
3. **An unauthenticated call creates a SAS session** (`Set-Cookie: JSESSIONID`), a side effect on a read.

So the answer to Kimi's question is decided by the measurement rather than by preference. **Frozen:**
`readProfile` **short-circuits** when the boundary reports no current token, returning a defined no-session
failure without a request; and the request is issued with **`redirect: 'manual'`**, so a 3xx is never followed
into an HTML 200. A 3xx is treated as no-session, not as success.

### F6 · Minor — ACCEPTED

Frozen as a constraint: **T30 owns the file's imports, setup, helpers and every `describe('T30: …')` block.**
T31 adds only `describe('T31: …')` blocks and may touch T30's blocks solely to repair a compilation error
caused by a renamed export. Same precedent as T24 → T25.

### F7 · Minor — REJECTED on fact

Kimi's premise is that `lintRejectsHandWrittenBackendFetch` "scans production source for backend route
prefixes" and that `/userinfo` may be missing from its denylist. **There is no denylist of routes.**
`.oxlintrc.json` bans the *identifiers*: `no-restricted-globals` on `fetch` and `XMLHttpRequest`, plus
`no-restricted-properties` for `fetch`/`XMLHttpRequest` on `window`, `globalThis`, `self`, `top`, `parent`,
`opener` and `frames`. The URL is never examined, so a hand-written `fetch('/userinfo')` is **already**
rejected, as is a hand-written fetch to anything at all.

Adopting the recommendation would mean adding prefixes to a list that does not exist. AC3's control stands as
briefed. A control is added recording this measurement, so the finding is not re-opened.

### F8 · Minor — ACCEPTED

AC6/AC7 reuse T29's console watcher over all six channels (`log`, `info`, `warn`, `error`, `debug`, `trace`)
with a sentinel per channel, plus T29's `encodings()` normalisation — a profile surviving in a URL is the
likely leak shape, and a raw-to-raw comparison reads clean on it.

### F9 · Minor — ACCEPTED IN SUBSTANCE, FORM REFUSED

The concern is right: "handles the aspirational shape" and "matches live behaviour" must not be the same
passing test. **But `it.skip` is the wrong instrument.** The suite is jsdom with no network; a test named
"live `/userinfo` returns only `sub`" could never run in it, and a permanently skipped test is not a pin — it
is a comment that reports green. Frozen instead:

- **AC9** asserts a `{"sub"}`-only response is a **success**, because that is what the live server returns.
- **AC11** asserts a full-profile fixture parses correctly, so the reader is proven against the shape O6 will
  produce.
- The O6 dependency is a named constant with a comment, and **closing O6 changes nothing in this suite** —
  which is the correct outcome, because a frontend unit suite is not where a backend gap is detected.

### F10 · Minor — ACCEPTED

Frozen: `readProfile(): Promise<Profile>`, `Profile` derived from the generated `UserInfoResponse`. Failure is
a **rejected promise** carrying a neutral, detail-free `ReadProfileError`. A control proves that an error
carrying server detail fails AC8.

---

## The frozen brief

Sections unchanged from Phase 2 except as the dispositions above require. Only the changes are restated.

### Acceptance Criteria (frozen)

- **AC1** — `oidc.yaml` declares `GET /userinfo` authenticated (no `security: []`), response schema requiring only `sub`, every other claim optional. (R20a, L6)
- **AC2** — the generated client exposes it; `generatedClientCompiles` passes; the generated file is byte-identical to the generator's output. (L6)
- **AC3** — `readProfile` reaches `/userinfo` only through the generated client. **Control:** the L6 rule rejects a hand-written `fetch`, and a second control records that it does so by banning the identifier, not by matching a URL (**F7**). (L6)
- **AC4** — the call carries the in-memory token as a bearer via the boundary, and a 401 is handled by the boundary's single-flight renewal, not by the reader. `OIDC_PUBLIC_OPERATIONS` contains exactly `POST /oauth2/token` (**F2**). (L3, L18)
- **AC5** — **no production module reads PII or verification state from the access token or `id_token`**: no `atob`, no `JSON.parse` of a JWT segment, no `split('.')` on a token, no `TextDecoder`/`Uint8Array.from` decode, and no read of `email`, `name`, `given_name`, `family_name`, `preferred_username`, `picture`, `email_verified` **or `sub`** (**F4**) from a token. Proven over the whole comment-stripped tree against `PERMITTED_TOKEN_READS`, which is an upper and lower bound and holds exactly one row (**F1**). (**R20c**)
- **AC6** — the profile never persists: absent from `sessionStorage`, `localStorage`, IndexedDB, `document.title`, `window.name`, the URL and all six console channels after a successful read, raw and percent-decoded (**F8**). (L16)
- **AC7** — the profile is never logged or sent to analytics, proven with a sentinel per console channel (**F8**).
- **AC8** — a failed read surfaces no detail, no stack and no cause-naming copy, **whether the failure arrives as an RFC 9457 body, an OAuth2 error body, or — as measured — an empty body with the detail in a `WWW-Authenticate` header** (**F3**). The header's `error_description` and `error_uri` reach no surface.
- **AC9** — a response of exactly `{"sub":"…"}` is a **success**. This is live behaviour under O6, not a degenerate case.
- **AC10** — every instrument is shown to report DIRTY first, including removing the one `PERMITTED_TOKEN_READS` row; a scan that reads nothing is refused rather than reported clean. (T22-D14)
- **AC11** — a full-profile fixture parses correctly, so the reader is proven against the shape O6 will produce (**F9**).
- **AC12, new from F5** — **no session means no request.** With no current token, `readProfile` returns a defined no-session failure and issues no request at all. The request is made with `redirect: 'manual'`, and a 3xx response is a no-session failure, never a success. A control proves that following the redirect instead yields HTTP 200 `text/html`, which would otherwise be parsed as a profile.

### Constraints (added)

- **`redirect: 'manual'` is a security constraint, not a nicety** (F5). Following the redirect sends the tab's
  request to a login page and returns its HTML with a success status.
- **Detail can arrive in headers.** AC8 covers `WWW-Authenticate` (F3).
- **The test file's ownership split** is T30's `describe('T30: …')` blocks versus T31's (F6).
- **Generated code is never hand-edited.**

### Declared deviations

| ID | Deviation |
|---|---|
| **T30-D1** | `/userinfo` returns only `{"sub"}` on the live stack (`spec/auth-service/design.md` **O6**). The reader is built and proven against both the live shape and a full-profile fixture; the end-to-end profile read cannot be demonstrated by anyone until O6 closes. Not T30's to fix. |
| **T30-D2** | `/userinfo` is added to `contracts/api/oidc.yaml`, which reaches outside `frontend/` and regenerates a shared client. Authorized by the owner on 2026-10-31 (OQ-T30-1, Option A) against that file's own "a decision of its own, not a drive-by". |
| **T30-D3** | No screen consumes the profile. `/app/account` is unbuilt, so T30 delivers a reader and its proof, with no display. |
| **T30-D4** | The session boundary is built but **not wired by the composition root** (`mountShell.tsx` is unowned, T29-D2), and no production `renew()` implementation exists. `readProfile` takes a boundary as a parameter rather than constructing one, so the wiring gap stays where it already is instead of being half-closed here. |

### Open Questions

**No blockers.** OQ-T30-1 decided (Option A). OQ-T30-2 recorded as `spec/auth-service/design.md` O6.
OQ-T30-3 answered: a reader and its proof, no display.

---

**Phase 4 complete. Brief FROZEN. Approved to proceed** to Phase 5 — implementation plan.
