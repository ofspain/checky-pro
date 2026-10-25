# frontend · T15 — Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` |
| **Produces** | `artifacts/13-pr-preparation.md` |

---

## Commits

| Commit | Phase |
|---|---|
| `…` | 0–2 — repository understanding, specification extraction, implementation brief |
| `…` | 3 — design challenge (Kimi 2.7) |
| `…` | 4–5 — frozen brief, implementation plan |
| `fec56a2d` | 6 — authorize request shape |
| `6749e99b` | 7 — self-review |
| `b53fd46b` | 8 — independent review (Kimi 2.7) |
| `b799b54a` | 9 — all eight review findings applied |
| `f5c2a7e4` | 10 — the two surviving mutants closed |
| `18b6daca` | 10 — test generation manifest |
| `1d5fa84c` | 11 — test review (Kimi 2.7) |
| `c6a6144b` | 11 — six of Kimi's seven findings applied |
| `8eaf6a12` | 12 — specification verification, PASS |

---

## Proposed PR description

### T15 — the OIDC authorization request

Assembles the request that starts sign-in: exactly the three registered scopes, `state`, `nonce`,
`code_challenge_method=S256` and the PKCE challenge from T14 — and persists the three items the callback tasks
(17–20) will read back.

**Every value is derived, not configured.** The SPA and every backend route share one origin through the Caddy
edge, so the authorize endpoint is this page's own origin and the redirect URI is built from the route table. The
module reads no environment key — which it also may not, since T01's suite forbids any source under `src/**` from
naming the compile-time environment object or an absolute address.

#### What ships

| File | |
|---|---|
| `frontend/src/auth/authorizeRequest.ts` | new, 98 lines |
| `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` | new, 50 cases |
| `frontend/src/auth/pkce.ts` | +9 / −4 — `randomUrlSafeToken` extracted so `state`, `nonce` and the verifier share one CSPRNG path |

Nothing else changes. No config, no `package.json`, no dependency, no file from T01–T13.

#### Verification

- `src/auth` **117 / 117** · whole suite **529 / 531** · `tsc` clean · `oxlint` clean
- The two failures are T07's known `spawn pnpm ENOENT` — bare `pnpm` is not on the build host's PATH. Pre-existing
  and unrelated; `npx -y pnpm@12.14.0` is the working recipe.
- **28 mutants run, 28 caught.**

#### The one thing a reviewer should not skip

`code_challenge_method: 'plain'` is caught by this test suite and **by nothing else on the platform.**
`RegisteredClientSeeder.java:60` sets `requireProofKey(true)`, so the server demands a challenge — but `S256`
appears nowhere in the auth service's main source, and Spring Authorization Server accepts `plain` with no switch
to forbid it. The mutant that makes this change passes every backend check. If that case is ever removed as
redundant, the SPA can downgrade to no PKCE protection at all, silently.

#### Two mutants that changed the work

- **M19** — a ninth parameter, `login_hint=someone@example.com`, putting an email address into a URL that lands in
  browser history, the edge access log and any referrer. **Nothing in the suite noticed** except the
  exact-eight-parameter assertion added in phase 9. Every value check, the leak matcher and the scope matcher all
  passed it. A finding that looked like tidiness turned out to be the only guard against a PII leak.
- **M10 and M12** — `const base = '/app'` instead of `APP_BASENAME`, and `/oauth2/authorise` instead of
  `/oauth2/authorize`. Both survived the first mutation pass for the same reason: **the assertion was compared
  against the very constant it was testing.** `APP_BASENAME` *is* `/app`, so hardcoding and deriving produce an
  identical string; and `parsed.pathname === AUTHORIZE_ENDPOINT_PATH` cannot fail on a misspelling. Fixed by
  moving the authority outside the module — the route table is mocked to `/console` and the redirect is watched to
  follow it, and the endpoint spelling is checked against the `/oauth2/…` literals the auth service's own
  integration tests drive against a live Spring Authorization Server.

This is the fifth appearance of that defect class in the frontend pipeline (T12 AC7, T13 AC8, T14 AC1, T15 S1/S2,
T15 M10/M12). It is now the first thing the mutation pass looks for.

#### Honest limits

- The verifier leak matcher is a substring search. A **base64-re-encoded** verifier and one **split across two
  parameters** both pass undetected — demonstrated in the suite rather than described, so the limit is a measured
  fact. The real defence is structural: the verifier is absent from `AuthorizeRequest`, so a caller cannot put it
  in a URL, a log or a React prop by accident.
- **No browser runs these tests.** The module uses only `crypto.subtle`, `getRandomValues`, `btoa`,
  `sessionStorage`, `URLSearchParams` and `location.origin`, all of which jsdom provides — but "works in jsdom" is
  not "works in Safari".
- **Nothing here proves the server accepts this request.** Every parameter is checked against the registered
  client by *reading* `application.properties` and `RegisteredClientSeeder.java`, not by exchanging anything. The
  round trip is T16's to demonstrate against the running stack.
- The origin-equality assertion added in phase 11 is not independently load-bearing; no mutant is killed by it
  alone.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

---

## Not in this PR

- **OQ-1** stays open for the owner and gates nothing. T01 forbids `import.meta.env`; `design.md` §4c allows four
  public keys. T15 needs none, and T16/T20 likely will not either. The §4c allowlist is a ceiling, not a plan.
- T12's OQ-1 (a refresh cannot promote a waiting worker) still needs an owner decision before the prompt UI.
- `design.md` §4c still heads the allowlist "PROPOSED, pending sign-off (Q6)" although Q6 closed 2026-10-10.
- `package.md:172` maps `serviceWorkerUpdateWaitsForReload` to R57, which does not describe it;
  `package.md:321-323` forced-update-for-rollback is unowned.
- `segmentPrefixPattern` is duplicated between `navigationDenylist.ts` and `apiRequestPolicy.ts`.
- `apiRequestPolicy.ts:46` lists `/app` in `SHELL_DOCUMENT_PATHS` although `/app` 404s.
- T01 asserts against a development bundle; it is on the do-not-modify list.
- The two T07 lint failures need `pnpm` on the host PATH, or T07's AC2 changed to `npx -y pnpm@12.14.0`.

---

## Next task

**T16 — the sign-in screen. The first visible UI in the project.** It consumes `buildAuthorizeRequest` directly
and is the first task whose output is looked at rather than asserted, and the first chance to put the SPA in front
of the full backend stack in `infra/local`.
