# frontend · T30 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` + `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

T30 has production code — `profile/readProfile.ts`, 131 lines — so unlike T29 this is not a verification-only
task. But the tests were written alongside the reader in Phase 6 and hardened twice since, so this phase is
still a **manifest**: every case below is already written, committed and green. What Phase 10 adds is the
mapping — each of the **46 cases** to the acceptance criterion and requirement it discharges, and each of the
mutants to the instrument that kills it. **Phase 11 took the file to 53 cases, 25 instruments and 34
mutants; the additions are mapped in the section at the end of this document, and the mutation score stated
here is corrected there.**

`frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` — **1201 lines, 46 cases,
nine `describe` blocks, 12 of them controls, over 20 instruments.**

T30 owns the file's imports, setup, helpers and every `describe('T30: …')`. **T31 adds only
`describe('T31: …')` blocks** and may not edit a T30 block except to repair a compilation error caused by a
renamed export (Kimi F6, frozen in Phase 4).

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0, no output |
| The file alone | **46 / 46**, exit 0 |
| Whole suite | **1444 passed / 1 failed (1445)** — `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental on this host |
| Mutation | **26 killed, 2 BROKEN (stale anchors), no survivors** — reported at the time as 28 / 28; corrected in phase 11 to **34 / 34** |
| `generatedClientCompiles` | 10 / 10 |

---

## The manifest

Controls are marked **C**. A control proves an instrument can report DIRTY; it is **not** a claim about the
product. Twelve of forty-six is a lower ratio than T29's thirty-two of fifty-six, and the reason is simply
that T30 has a product to assert things about: a reader with twelve behaviours, where T29 had an absence.

### AC1 / AC2 — the contract declares `/userinfo`, and the generated client exposes it · R20a, L6

| # | Case | Discharges |
|---|---|---|
| 1 | declares `GET /userinfo` as AUTHENTICATED, unlike `POST /oauth2/token` | AC1. **Asserts a positive requirement** — `bearerAuth` declared under `securitySchemes` and required by the operation (Kimi I4). The earlier form asserted the absence of `security: []`, which in OpenAPI 3 was not a distinction at all |
| 2 | requires only `sub`, so the live sub-only response is a valid one | AC1 — `required: [sub]`, every other claim optional. T30-D1 |
| 3 | documents the 302 and the 401, because neither is what a reader would assume | AC1 — both measured, both schema-less (Kimi I8) |
| 4 | exposes the operation on the generated client, typed | AC2 — the path is reachable through generated types, not a string |

### AC3 — the reader reaches `/userinfo` only through the generated client · L6

| # | Case | Discharges |
|---|---|---|
| 5 | names no transport of its own | AC3 — five banned forms, plus a **shape** claim: `/userinfo` occurs once, as a quoted literal argument to `api.GET`, never assembled. The first draft asserted the path was *absent*, which was wrong — the schema path IS openapi-fetch's first argument |
| 6 **C** | the L6 rule bans the IDENTIFIER, not a list of routes — so `/userinfo` needs no entry | Kimi **F7, rejected on fact**. `.oxlintrc.json` uses `no-restricted-globals` on `fetch`/`XMLHttpRequest` and `no-restricted-properties` on seven window objects. There is no route denylist to add `/userinfo` to, so the recommendation had nothing to act on. This case is the measurement that settles it |

### AC4 — the boundary supplies the bearer and owns the 401 · L3, L18, R23

| # | Case | Discharges |
|---|---|---|
| 7 | sends the tab in-memory token as a bearer | AC4 — through a **real** `createSessionBoundary`, not a stub |
| 8 | lets the BOUNDARY renew on 401 and re-send, which the reader never sees | AC4 — the reader has no renewal logic; the boundary's single flight does it |
| 9 | surfaces a FINAL 401 as unauthorized, after the boundary has already tried | AC4 — one renewal, not two |
| 10 | surfaces a 403 as unauthorized too, and renews for neither reason twice | AC4. **Added because MP21 escaped** — no case drove a 403 |
| 11 | preserves R23 for the first authenticated OIDC call: a 429 shows the notice | R23, Kimi **I5** |
| 12 | shows the notice for a 429 on the RE-SEND, which is what the registration order is for | R23. **Added because MP27 escaped case 11**: with a 429 on the first call there is only one response to see, so both registration orders pass. 401→429 is the shape that separates them |
| 13 **C** | a 200 leaves the rate-limit notice alone, so the check is not always true | the `isShown()` assertion is falsifiable |
| 14 | holds exactly `POST /oauth2/token` public, and not `/userinfo` | AC4, Kimi **F2** — `OIDC_PUBLIC_OPERATIONS` as an upper and lower bound |

### AC5 — no production module reads PII or verification state from a token · **R20c**

Two instruments **compose** here, and the composition is the point. `email`, `name` and `sub` are ordinary
words this package uses constantly, so a claim scan over the whole tree is meaningless on its own. The decode
scan bounds the file set to exactly one — **asserted, not assumed** — and the claim scan then runs only over
that set.

| # | Case | Discharges |
|---|---|---|
| 15 | decodes a token in exactly one place, and that place is allowlisted with a reason | AC5, Kimi **F1**. The one place is `auth/idTokenNonce.ts`, R7 nonce validation, committed and spec-required |
| 16 | reads no forbidden claim off a decoded token, anywhere | AC5 — eight claims including **`sub`** (Kimi **F4**, accepted; the carve-out it proposed was refused in form) |
| 17 | the allowlist is an upper AND a lower bound: one row, and its forms all still exist | AC5 — a stale allowlist row is itself a finding |
| 18 **C** | removing the row makes the scan report the file it protects | AC10 — the allowlist is load-bearing |
| 19 **C** | the scan flags every shape of reading a claim off a token | AC10 — five synthetic decodes |
| 20 **C** | the decode scan stays SILENT on ordinary string and JSON work | AC10 — five legitimate non-decodes. The false-positive half, which is the half that gets a scan loosened later |
| 21 | RECORDS the decode pattern BOUNDARY, and guards it with the dependency list | Kimi **I7, substance accepted, form refused**. The recommendation asks for a control asserting the scan *flags* `jwtVerify` — it does not, so that assertion would simply fail. Recorded as a negative control plus a guard that **does** fire: no JWT-decoding library is a dependency, checked across both manifests against seven names |
| 22 **C** | a scan that reads nothing is refused, not reported clean | **T22-D14** — the empty-tree refusal, through `refuseEmpty` |

### AC6 / AC7 — the profile never persists and is never logged · L16

| # | Case | Discharges |
|---|---|---|
| 23 | leaves no claim on any surface after a successful read | AC6 — six surfaces, raw and percent-decoded |
| 24 **C** | every swept surface is actually read, proven with a sentinel on each | AC10 — a sweep that reads five of six surfaces passes everything |
| 25 **C** | a claim surviving PERCENT-ENCODED in a URL is caught too | AC6, Kimi **F8**. `URLSearchParams` turns a space into `+`, which a naïve decode misses |
| 26 **C** | all six console channels are watched, not only the ones a flow happens to use | AC7, Kimi F8 — a sentinel per channel |
| 27 | writes nothing to either store, even on the failure paths | AC6 — the failure paths, which are the ones a cache would be added to |

### AC8 — a failed read surfaces no detail, not even from a header · F3, R9457-adjacent

| # | Case | Discharges |
|---|---|---|
| 28 | carries nothing from the `WWW-Authenticate` header, which is where the live detail is | AC8, Kimi **F3**. Measured: a malformed bearer returns **401 with an empty body** and `error_description="…while attempting to decode the Jwt: Malformed token"` in the header. A body-only assertion would have passed over the only place the detail actually was |
| 29 | names no cause and reaches no surface, for every failure shape | AC8 — all three failures, all six surfaces, all six channels |
| 30 | converts a THROWING transport into a neutral error, keeping neither message nor stack | Kimi **I1**, the one High-confidence finding and a real defect. openapi-fetch resolves an HTTP error status but **rejects** when `fetch` throws; measured, a raw `TypeError` escaped with an internal hostname and `net::ERR_NAME_NOT_RESOLVED`. No status check could have caught it, because no response existed |
| 31 | does the same for an abort and for a CORS-style failure | I1 — a `DOMException` and a thrown string, so the `catch` is not shaped around one constructor |
| 32 | the error message is INTERNAL: none of the three strings is in the catalogue | Kimi **I3**, option (a), asserted **by inversion**. A consumer maps `failure` — a closed set of three — rather than rendering `error.message` |
| 33 **C** | an error carrying server detail WOULD be caught | AC10 — a leaky `Error` beside a `ReadProfileError` |

### AC9 / AC11 — a sub-only response is a success, and a full profile parses

| # | Case | Discharges |
|---|---|---|
| 34 | treats `{"sub"}` alone as a SUCCESS, because that is what the server returns today | AC9. **Not a degenerate case** — measured live, 2026-10-31 |
| 35 | parses the full profile O6 will produce, so closing it needs no change here | AC11, Kimi **F9** |
| 36 | rejects a body with no usable sub, which is the one claim the contract requires | AC9 — four unusable bodies |
| 37 | the O6 dependency is a stated fact here, not a skipped test | Kimi F9's `it.skip` form **refused**. A skipped test is not evidence of anything and rots silently; the dependency is asserted as a fact about today's server with the reproduction recorded in `spec/auth-service/design.md` **O6** |

### AC12 — a missing session is not a 401, and a redirect is not a success · new from F5

The clause exists because the measurement contradicted the brief. With **no** bearer, `/userinfo` returns
**302 → `/login` with `Set-Cookie: JSESSIONID`**, and followed, **200 `text/html`**, 1103 bytes of login page.
There is no 401 — so the boundary's renewal never runs, and a default-`fetch` client would parse a login page
as a profile.

| # | Case | Discharges |
|---|---|---|
| 38 | issues NO request at all when the tab holds no token | AC12 — the call list asserted empty |
| 39 | asks the transport not to follow redirects | AC12 — `redirect: 'manual'` reaches the transport |
| 40 | treats a 3xx as no session, whatever the body looks like | AC12 — three 3xx shapes (Kimi **I8**) |
| 41 | treats a FOLLOWED redirect as no session, which is the shape fetch produces by default | AC12 — the measured 200 `text/html`, caught via `response.redirected` |
| 42 | MEASURES the race Kimi **I6** names: a token cleared after the check still ends in no-session | I6 accepted **as a measurement**. A bearer-less request IS issued — stated, not hidden — and the outcome is still `no-session`. **T30-D6** |
| 43 **C** | a 200 that is NOT a redirect still succeeds, so the check is not just rejecting everything | AC10 — the redirect guard is falsifiable |

### AC10 — every instrument has been shown to report dirty · T22-D14

| # | Case | Discharges |
|---|---|---|
| 44 | names one dirtying violation per instrument | the **20-instrument** table. Declared in the file as a **signpost, not evidence** — it cannot fail for the right reason. The proof is the control cases each row names |
| 45 | refuses a scan that reads nothing, through the one guard every walker calls | T22-D14 — `refuseEmpty` is a single choke point, not a per-scan habit |
| 46 **C** | comment stripping is what makes the decode scan survivable at all | AC10. This file's own header names `atob`, `JSON.parse` and every forbidden claim a dozen times while explaining why they are forbidden; so does `readProfile.ts`. The literal-aware stripper keeps a quoted `'…/atob-explainer'` that the two-regex version would have eaten |

---

## Mutants

28 mutants, **28 killed, no survivors**, every one by this file. The harness
(`artifacts/07-mutation-harness.py`) **asserts its own baseline** before scoring anything — `1` expected
whole-suite failure and `0` in T30's own file — and **exits non-zero on a survivor**, because T29 learned that
an anomalous baseline silently converts a kill into a published false negative.

| Family | Mutants | Killed by |
|---|---|---|
| A token read instead of `/userinfo` | MP01–MP05 | AC5 cases 15–17. MP01 is the subtlest — `sub` off the access token, which is why F4 mattered. **MP05 was written to escape**: a decode in a module nothing imports yet. The scan walks the tree, not the import graph, so it is killed |
| The reader builds its own request | MP06–MP08 | AC3 case 5, AC4 case 14 |
| Failure detail leaks | MP09–MP11 | AC8 cases 28, 29. **MP09 first appeared to escape and was INERT** — `ReadProfileError` looks its message up by key, so a detail-carrying key yields `undefined`. The property is now pinned *and* the mutant re-aimed at a plain `Error` |
| No session | MP12–MP15 | AC12 cases 38–41 |
| The sub-only shape | MP16–MP17 | AC9 cases 34, 36. MP17 fills the missing claims in rather than leaving them absent |
| The profile survives the read | MP18–MP19 | AC6 cases 23, 27 |
| Bearer and 401 ownership | MP20–MP21 | AC4 cases 8–10. MP21 was a **real escape**, fixed by adding case 10 |
| Contract mutants | MP22, MP23, MP28 | AC1 cases 1–3. Applied and reverted separately from the source mutants, because they require regenerating the client |
| Phase 8 instruments | MP24–MP27 | cases 30–32, 12. **MP26 was not a defect** — an internal message's wording breaks nothing, and the inversion test asks whether a message *is* catalogue copy, not whether it sounds like it; re-aimed at an exact catalogue string. **MP27 was a real gap**, fixed by case 12 |

**The inert-versus-escape judgement came up three times in T30** — MP09, MP26 and, in the opposite direction,
MP27. It is the same lesson T28's MB27 recorded, and it does not get easier with practice: a survivor is a
question, not a verdict.

---

## Production code under test

`frontend/src/profile/readProfile.ts` — 131 lines, one exported function, one exported error class, one
derived type.

| | |
|---|---|
| `Profile` | **derived** — `oidcComponents['schemas']['UserInfoResponse']` (Kimi **I2**, restoring Phase 4's F10, which the first draft quietly did not honour) |
| `ReadProfileFailure` | a closed set of three: `no-session`, `unauthorized`, `unavailable` |
| `ReadProfileError.message` | looked up **by key** from a `Record<ReadProfileFailure, string>`, so it structurally cannot carry detail. This is what made MP09 inert |
| Transport | `api.GET('/userinfo', { redirect: 'manual' })` inside a `try/catch` that converts any throw into `unavailable` |

Also changed: `contracts/api/oidc.yaml` (**T30-D2**, owner-authorized), the regenerated
`libs/ts/api-client/src/generated/oidc.ts`, `frontend/src/api/client.ts` (`createOidcApi`,
`OIDC_PUBLIC_OPERATIONS`), and one inherited pin in
`frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` (**T30-D5**).

## What is NOT in this file

- **Account status from `/me`** — the second half of the file's name is **T31's**. T30 built the `/userinfo`
  half and the shared scaffolding.
- **A screen that renders the profile** — `/app/account` is unbuilt (**T30-D3**).
- **A production `renew()`** and the composition root's wiring of the boundary (**T30-D4**). `readProfile`
  takes both as parameters, so the wiring task changes callers, not the reader.
- **The end-to-end read of a full profile** — blocked on `spec/auth-service/design.md` **O6** for everyone,
  not just T30 (**T30-D1**).
- **Closing the I6 side-effect window** — needs `api/sessionBoundary.ts`, out of scope and shared by every
  client (**T30-D6**).

---

## Correction and additions, entered 2026-10-31 after phase 11

**The mutation score above was overstated when written.** `GET_CALL` had been stale since the I1 fix in phase
8 rewrote the `api.GET` call, so MP06 and MP07 printed `BROKEN anchor missed` rather than `KILLED`. A `BROKEN`
mutant is not a survivor, so the run ended `survivors: none` and was reported as 28 of 28. **It was 26 killed
and 2 never applied.** The harness now refuses to start until every anchor matches its file exactly once.

Kimi's phase 11 returned eight recommendations, all eight accepted, **two of them production defects**. The
file is now **53 cases, 25 instruments, 34 / 34 mutants killed**, suite **1451 passed / 1 failed (1452)**.

| # | Case | Discharges |
|---|---|---|
| 47 | puts the access token itself on no surface and in no console channel | AC6, L3. `tokensAreNeverWrittenToAnyStorage` owns this claim for the sign-in and callback flows but **never calls `readProfile`** — T30 introduced a flow holding the token that nothing swept. Killed MP33 |
| 48 | collapses a malformed JSON body into unavailable, with no parse detail | AC8. **Measured already correct, and not by the arm Kimi names**: the parse happens inside the awaited `api.GET`, so the `SyntaxError` is caught by the I1 `try/catch`, not by `data === undefined` |
| 49 | carries no cause on any failure path | AC8. `cause` is what a maintainer closing I1 differently would reach for, leaving `message` and `stack` clean while the original `TypeError` rode along. Five rejecting paths. Killed MP32 |
| 50 | rejects a JSON null body as unavailable rather than throwing a raw TypeError | AC9, **a live defect**. Measured: `TypeError: Cannot read properties of null (reading 'sub')` escaped `readProfile` with its stack. Killed MP31 |
| 51 | treats an OPAQUE redirect as no session, which is what a browser actually returns | AC12. `redirect: 'manual'` returns `type: 'opaqueredirect'`, `status: 0`, no body — so the status arm and the `redirected` arm are **both dead in a real browser** and the third was the only one working, undriven. Killed MP29 |
| 52 | issues no request for an EMPTY token either | AC12. Killed MP30 |
| 53 | rejects a FOLLOWED redirect even when it carries a perfectly valid profile | AC12. Makes the check **order** load-bearing: the existing case carried HTML, which a body-first order would also reject, for the wrong reason. Killed MP34 |

| Family | Mutants | Killed by |
|---|---|---|
| Phase 11 instruments | MP29–MP34 | cases 47, 49–53. MP29 and MP31 are the two that matter: the arm a real browser reaches, and a defect that shipped |

**Two production fixes** landed: `data === null` in the shape guard, and `''` alongside `null` in the token
guard. **T30-D7, new:** `document.cookie` and `indexedDB` are declared unswept — jsdom gives an empty cookie
jar and no `indexedDB`, so sweeping either reports clean for an environmental reason. T29 declared this at its
own sweep; T30 had inherited the list without the caveat.

---

**Phase 10 complete.** 53 cases mapped to 12 acceptance criteria, 25 instruments and 34 killed mutants.
