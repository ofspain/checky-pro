# frontend · T14 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

No code written in this phase. **T14 opens Phase 1 — Auth & account** (`tasks.md:23`); T01–T13 (Foundation) are
complete, suite **412/414**. It is the **first task to create `src/auth/`**.

## 1. Architecture summary

- **Modules today.** `src/app/` (shell, router, mount), `src/api/` (generated-client wrapper, 401 renewal, rate-limit
  notice, cross-tab renewal), `src/i18n/`, `src/pwa/` (worker + its policies, registered since T12), `src/main.tsx`.
  **`src/auth/` does not exist yet** — `design.md:203` plans it as "OIDC client, session owner, guards (L1–L3, L16,
  L18)".
- **Persistence.** None server-side. Client-side storage is forbidden except L16's carve-out, which names
  **`state`, `nonce`, `code_verifier` and the post-login return target** in `sessionStorage`, single-use, deleted at
  callback. T14's artefact is one of the four values in that carve-out.
- **Events.** No outbox. Cross-tab coordination is BroadcastChannel + Web Locks (`crossTabRenewal.ts`, L18).
- **Security posture relevant here.** The SPA is a **public** OIDC client: no client secret in the bundle (L1,
  `agents.md:19-20`), tokens in memory only (L3), interactive login is SAS-hosted and the SPA renders no password
  form (L2).

## 2. Existing code this task touches

**Nothing is modified.** T14 adds its first module under a new directory.

| File | Why it matters to T14 |
|---|---|
| `src/api/sessionBoundary.ts` | defines **`SessionTokens { current(); renew() }`** — the interface `src/auth/` must eventually implement. `renew()` is documented as *silent re-authentication on the SAS session cookie*, which means **a second PKCE exchange**, so whatever T14 builds is used on the renewal path too, not only at sign-in. |
| `src/api/crossTabRenewal.ts` | `LocalSession extends SessionTokens` adds `adopt(token)`. Same point: the auth module is the missing implementation of an interface that already exists and is already tested. |
| `src/app/routes.tsx` | `callback` and `sign-in` are already declared public route paths (`:8-18`); no screen is attached. T14 attaches none either. |
| `src/app/bundleContainsNoSecretEnvKeys.test.ts` | T13's rule. Relevant below (§5) — the verifier must not be derived from, or leak into, anything baked at build time. |

**New:** one module under `src/auth/` plus `src/auth/pkceVerifierMeetsRfc7636.test.ts` (`package.md:173` maps the
test name to R6).

## 3. Established patterns to follow

- **Smallest honest module, stated rule in the comment with its spec citation.** `navigationDenylist.ts`,
  `apiRequestPolicy.ts` and `serviceWorker.ts` are each a published constant plus a predicate, with the rule quoted
  from the spec and an anchor. T14 should be the same shape: generate, derive, publish, decide nothing else.
- **Derive, never restate**, with a **drift guard** against the spec sentence (T09's eleven paths, T11's twelve
  prefixes, T12's two sentences, T13's four keys). The RFC 7636 character set and the 43–128 bound are *the RFC's*,
  not the spec's, so phase 2 must decide what the drift guard anchors to — see §5.
- **Default-deny / exact negation** rather than a denylist of known-bad shapes (T11, T13).
- **Measure, don't assume.** T12 settled the worker scope against the running stack; T13 settled `envPrefix`,
  absolute URLs and the `latin1` question by building and reading. Two premises for T14 are settled below the same
  way.
- `expect.getState().testPath` to locate the repo root under jsdom.
- **Mutation testing as a phase-10 deliverable**, with the standing lessons: commit tests first; mutate the guarded
  source, not the guard; a guard against a condition the build cannot produce is still exercised by feeding the
  condition to the matcher directly.

## 4. Testing conventions

Vitest 5.0.5 + jsdom 30.1.3. File named exactly after `package.md` §8 → `pkceVerifierMeetsRfc7636`. No
Testcontainers, no `Clock`, no ArchUnit.

**Settled by measurement, not assumed** — the one thing that could have blocked this task:

| Probe | Result |
|---|---|
| `globalThis.crypto` / `getRandomValues` | present, works (`nonzero=true`) |
| `globalThis.crypto.subtle` / `.digest` | **present and working** — `SHA-256('abc')` returns 32 bytes beginning `0xba`, which is correct |
| `sessionStorage`, `TextEncoder`, `btoa`, `Buffer` | all present |

So **Web Crypto needs no polyfill, shim or `happy-dom` switch** under this environment, and the S256 half of the
task is directly testable. I had expected `crypto.subtle` to be missing (it historically is under bare jsdom); it is
not, because Vitest's jsdom environment leaves Node's `webcrypto` on the global.

A statistical property (uniformity of the generated characters) is testable but needs a stated tolerance; that is a
phase-2 decision, not a convention that already exists here.

## 5. Known gaps / unknowns

**The backend requires PKCE but does not require S256 — so the S256 half of R6 is the SPA's alone to keep.**
Measured, not inferred:

- `RegisteredClientSeeder.java:60` sets `.requireProofKey(true)` for `checky-spa`, with
  `ClientAuthenticationMethod.NONE` (`:53`) and the authorization-code grant (`:54`). So an authorize request with
  **no** `code_challenge` is rejected by the server.
- But **`S256` appears nowhere in the auth service's main source** — only in its tests
  (`SasLoginIntegrationTest.java:676`, `EndToEndLifecycleIntegrationTest.java:647`). Spring Authorization Server
  accepts `plain` as well, and offers no switch to forbid it. **A verifier sent with
  `code_challenge_method=plain` would be accepted by this backend**, which puts the verifier in the authorize URL in
  clear. Nothing downstream will catch that for us; T14/T15 are the only guard.
- The real redirect URIs are configured: `application.properties:52-53` →
  `http://localhost:5173/app/callback` and `/app/signed-out`, which match `routes.tsx`'s declared paths.

**L13's confirmed stack names no OIDC library.** Vite, React, TypeScript, React Router, TanStack Query, Zustand,
`vite-plugin-pwa`, Vitest, Playwright — and nothing else (`design.md:50-51`). `package.json` has no
`oidc-client-ts`, `oauth4webapi` or equivalent. The task statement ("the code verifier is 43–128 characters from the
RFC 7636 unreserved set") reads as though **we generate it ourselves**, and adding a library would extend a LOCKED
decision. I am treating hand-rolled as the default and logging the alternative rather than deciding it.

*(Observation, not this task's business: TanStack Query and Zustand are in L13 but not yet in `package.json`.)*

**Two real design questions for phase 2 — I am not deciding them here:**

1. **Which generation scheme.** RFC 7636's unreserved set is 66 characters (`A-Z a-z 0-9 - . _ ~`); base64url
   output uses only 64 of them. The backend's own reference helper takes 32 random bytes and base64url-encodes them
   without padding, giving exactly **43** characters — the RFC's minimum, and ~256 bits of entropy.
   - base64url is **bias-free** (64 divides 256) and trivially correct, but never emits `.` or `~`, so a test
     asserting "characters from the unreserved set" would pass over a strictly smaller alphabet.
   - Drawing from all 66 characters by `byte % 66` introduces **modulo bias** (256 is not a multiple of 66). That is
     a genuine, mutation-testable defect and the thing I would most expect a naive implementation to get wrong.
   - Phase 2 must pick one and say which, because the test's character-set assertion means different things under
     each.
2. **What the drift guard anchors to.** Every prior task guards a sentence in `spec/frontend/*`. Here the normative
   numbers (43–128, the unreserved set, S256) come from **RFC 7636**, which is not in the repository, and the spec
   states them only in `tasks.md`'s task line. Guarding `tasks.md` is possible but unusual — no previous task guards
   it. Unresolved.

**Boundary with T15, which I do not know the intended split of.** T14's statement ends "…and its S256 challenge is
**sent on authorize**", while T15 ("Authorize request shape") owns `state`, `nonce`,
`code_challenge_method=S256` and the exact scopes. Those overlap on the same request. My reading is that T14 owns
**generating the verifier and deriving the challenge** (plus proving the derivation is really S256), and T15 owns
**the assembled authorize URL**; but "sent on authorize" could be read as requiring T14 to build the request too.
Phase 1 must settle it against `package.md` §8, and the risk of guessing wrong is a duplicated or orphaned
assertion, not a security hole.

**Carried in from T13 — the one open owner decision that lands on this section.** `design.md` §4c declares four
public environment keys, three of which are **exactly** what an OIDC client needs (`VITE_OIDC_ISSUER`,
`VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`), and R58 permits them in the bundle — but **T01 `:130` forbids any
source under `src/**` from naming `import.meta.env` at all**. T13 left this open deliberately (its D-11: the
allowlist is a ceiling, not a plan). **T14 itself does not need a key** — a verifier needs randomness, not
configuration — so this task can proceed without the answer. The first auth task that wants the issuer or client id
will not. Logged for phase 1 and for the owner.

## References

`spec/frontend/tasks.md:23`, task 14; `requirements.md:21-22` (R6); `design.md:8-10` (L1), `:11-12` (L2), `:13` (L3),
`:50-51` (L13), `:52-55` (L14), `:58-60` (L16), `:61-62` (L18), `:203` (§6 `auth/`); `agents.md:18-24`, `:37-38`,
`:78-83`; `package.md:110`, `:173` (§8 mappings);
`frontend/src/api/sessionBoundary.ts:7-14`, `frontend/src/api/crossTabRenewal.ts:4-7`,
`frontend/src/app/routes.tsx:6-18`;
`services/auth/src/main/java/com/themistra/auth/token/RegisteredClientSeeder.java:53-60`,
`services/auth/src/main/resources/application.properties:52-53`,
`services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java:649-662`.
