# frontend · T15 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

No code written in this phase. T15 is the **second** task of Phase 1 — Auth & account, and the second file under
`src/auth/`. T01–T14 are complete; suite **479/481**.

## 1. Architecture summary

- **Modules.** `src/app/` (shell, router, mount), `src/api/` (generated-client wrapper, 401 renewal, cross-tab
  renewal), `src/auth/` (**as of T14**: `pkce.ts`), `src/i18n/`, `src/pwa/`, `src/main.tsx`.
- **Persistence.** None server-side. Client-side storage is forbidden except L16's carve-out, which names exactly
  `state`, `nonce`, `code_verifier` and the post-login return target. **Three of those four are T15's subject**, and
  T14 deliberately stored none of them.
- **Security posture.** Public OIDC client, PKCE (T14), tokens in memory only (L3), interactive login SAS-hosted
  (L2), no password form in the SPA.
- **The single origin is the load-bearing fact for this task.** `infra/local/edge/Caddyfile:12` routes
  `/oauth2/*`, `/.well-known/*`, `/userinfo`, `/connect/*`, `/login*` and the account APIs to `auth:8080`, and
  `/app*` to the SPA — all on **one origin**, `http://localhost:5173`. Its own comment states the intent: *"the
  single origin the browser sees (frontend L9) … Host is passed through unchanged, so auth-service's login
  redirects stay on this origin."*

## 2. Existing code this task touches

| File | Why |
|---|---|
| `src/auth/pkce.ts` | **T15's direct dependency.** `generatePkcePair()` returns `{ verifier, challenge, method }`, with `method` typed as the literal `'S256'`. T15 must send that constant, not a string of its own. |
| `src/app/routes.tsx:6-18` | `APP_BASENAME = '/app'`, and `sign-in`, `callback` and `signed-out` are already declared public route paths. The redirect URI is derivable from these rather than configured. |
| `src/api/sessionBoundary.ts:7-14` | `renew()` is documented as silent re-authentication — it **re-opens `/oauth2/authorize`**. So T15's request builder is used on the renewal path too, not only at sign-in. |
| `src/app/shellBuildsAndMounts.test.tsx:127-133` | T01's rule, and the central constraint on this task. See §5. |

**New:** a module under `src/auth/` plus `src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts`
(`package.md:174` maps the name to R20).

## 3. Established patterns to follow

- **Smallest honest module; the rule stated in the comment with its spec citation.** T14's `pkce.ts` is the closest
  prior art and the pattern to copy: constants, a pure function, nothing that decides policy twice.
- **Publish a literal-typed constant rather than letting callers spell a value** — T14's `PKCE_CODE_CHALLENGE_METHOD`
  exists precisely so T15 cannot send `plain`. T15's scopes deserve the same treatment.
- **Derive, never restate**, with a drift guard against the spec sentence.
- **Non-vacuity is paid for, not assumed.** T12's AC7, T13's AC8 and T14's AC1 each needed a case feeding the
  condition to the matcher directly, because asserting over our own output can pass for the wrong reason. T15's
  "exactly these scopes" assertion has the same shape and will need the same treatment.
- **The source scan + runtime spy pair** (T14): the scan reads text, the spy watches behaviour, and where they are
  asymmetric the test says which is authoritative.
- `expect.getState().testPath` to locate the repo root under jsdom.
- **Mutation testing as a phase-10 deliverable**, and — standing rule, broken once in T14 and recorded —
  **commit the tests before mutating.**

## 4. Testing conventions

Vitest + jsdom. File named exactly after `package.md` §8 → `authorizeRequestsExactlyTheDeclaredScopes`. No
Testcontainers, no `Clock`, no ArchUnit.

Measured in T14 and still true: `crypto.getRandomValues`, `crypto.subtle.digest`, `btoa`, `sessionStorage` and
`TextEncoder` are all present and working under this jsdom. **`Buffer` must not be used** — Vite does not polyfill
it, so it passes under Vitest and fails in a browser.

## 5. Known gaps / unknowns

### The thing I got wrong, corrected by reading the configuration

I reported at the end of T14 that **OQ-1 blocks T15** — that this task needs `VITE_OIDC_ISSUER`,
`VITE_OIDC_CLIENT_ID` and `VITE_OIDC_REDIRECT_URI`, which `design.md` §4c permits and T01 `:130` forbids. **That is
wrong. T15 needs no environment key at all**, and the reason is architectural rather than a workaround:

| Value needed | Where it comes from | Evidence |
|---|---|---|
| **authorize endpoint** | the browser's **own origin** + `/oauth2/authorize` | `Caddyfile:12` routes `/oauth2/*` to auth on the same origin; `compose.yaml:14` sets `AUTH_ISSUER_URI: http://localhost:5173`, i.e. the **edge** origin, not `auth:8080` |
| **`client_id`** | the fixed public identifier `checky-spa` | `application.properties:51` — `themistra.auth.clients.spa.client-id=checky-spa`, with **no `${ENV:…}` override**, unlike the redirect URIs on `:52-53`. It does not vary by environment, and a public client id is not a secret |
| **`redirect_uri`** | `location.origin` + `APP_BASENAME` + `callback` | `routes.tsx:6,9` already declare both parts; the backend's configured value is exactly `http://localhost:5173/app/callback` (`application.properties:52`) |

So all three are derivable at runtime, and **T01's two rules push the same way rather than conflicting here**:
`:130` forbids `import.meta.env` and `:131` forbids any `https?://` literal in production source — over **all of
`src/**` recursively**, `src/auth/` included (`:34-41`). Together they force derivation from `location.origin`,
which is exactly what L9's single-origin edge is for. Deriving is also *more* correct than a baked key: the backend's
redirect URI is environment-overridable (`${SPA_REDIRECT_URI:…}`), and a value derived from the live origin tracks
that automatically, where a build-time constant would not.

**OQ-1 therefore remains open but is not this task's blocker**, and on this evidence it is unlikely to block T16
either — a sign-in screen needs the same authorize URL and the same derivation. T13's frozen position stands: the
§4c allowlist is a ceiling, not a plan.

### Real open questions for phase 1/2

1. **Discovery, or construct the URL directly?** `/.well-known/openid-configuration` is routed
   (`Caddyfile:12`) and would return endpoints on the edge origin. Fetching it is more correct in principle
   (endpoints come from the server) but adds a network round trip before sign-in, an error path, and — a hard
   constraint — **`.oxlintrc.json` forbids naming `fetch` in `src/**`** with no override available (T07's AC5
   asserts no `overrides` key and exactly 2 rules). A discovery fetch would have to go through
   `@themistra/api-client`, which is generated from `auth.yaml`, and **the SAS endpoints are deliberately not in
   that contract** (`design.md:133-135`). I do not know which the author wants; constructing `${origin}/oauth2/authorize`
   is the simpler reading and the only one that needs no new transport.
2. **Where `state` and `nonce` are generated and stored.** L16 permits sessionStorage to hold them; T14 stored
   nothing and put `sessionStorage` in its own forbidden-name scan. T15's task line says the request *carries*
   them, which requires generating them — but whether T15 also **persists** them, or that belongs to the task
   owning the L16 set as a unit, is a scope decision. `package.md:160` maps a separate test,
   `sessionStorageHoldsOnlyTheL16Items` → R29, which suggests the carve-out has its own owner.
3. **The post-login return target.** L1 and `agents.md:21-22` require it to be checked against an allowlist with a
   fallback to the home route. It is the fourth L16 item and is arguably part of "the authorize request shape"
   since it is what `state` protects. T15's task line does not mention it. **I do not know** whether it is in
   scope; `tasks.md` tasks 17 and 18 cover `state` validation at callback, not its construction.
4. **Whether T15 builds a URL or a parameter record.** "Carries" could mean either. A record is far easier to
   assert over and leaves URL assembly to the caller; a URL is what the browser needs. Phase 2's decision.

### Recorded, not a gap

- **The backend accepts `plain`** (`RegisteredClientSeeder.java:60` requires a challenge; `S256` is absent from the
  auth service's main source). T14 published a literal-typed constant for this reason, and **T15 is the task that
  must actually send it** — nothing else enforces S256 end to end.
- **No consent screen:** `requireAuthorizationConsent(false)` (`RegisteredClientSeeder.java:61`), first-party client.
- **The registered scopes are exactly `openid`, `profile`, `email`** (`RegisteredClientSeeder.java:56-58`), matching
  R20 and T15's task line. A fourth scope would be rejected by the server, so this one rule *is* enforced on both
  sides — unlike S256.

## References

`spec/frontend/tasks.md:24`, task 15; `requirements.md:21-22` (R6), `:66-67` (R20); `design.md:8-10` (L1),
`:13` (L3), `:44` (L9), `:52-55` (L14), `:58-60` (L16), `:133-135` (SAS endpoints), `:203` (§6 `auth/`);
`agents.md:19-22`, `:37-38`, `:79-81`; `package.md:110`, `:160`, `:174` (§8);
`frontend/src/auth/pkce.ts`, `frontend/src/app/routes.tsx:6-18`, `frontend/src/api/sessionBoundary.ts:7-14`,
`frontend/src/app/shellBuildsAndMounts.test.tsx:34-41`, `:127-133`;
`infra/local/edge/Caddyfile:12-14`, `infra/local/compose.yaml:14`,
`services/auth/src/main/resources/application.properties:45-53`,
`services/auth/src/main/java/com/themistra/auth/token/RegisteredClientSeeder.java:50-62`.
