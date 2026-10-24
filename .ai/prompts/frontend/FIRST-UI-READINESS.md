# The first UI: what it is, what it needs, and how to show it

The user's standing contract (2026-10-22, reaffirmed 2026-10-24): **the first screen you can open in a browser
arrives at task 16**, and the task order is not changed to get there sooner. Announce it loudly when it lands.

## What T16 actually is

> 16. **No password field.** The sign-in screen renders no password input. Verifies
> `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. Cites R6.

It is the sign-in screen at **http://localhost:5173/app/sign-in**, and it starts the OIDC authorization-code flow
with PKCE against the real auth-service through the edge. R6 is why there is no password box: the SPA never collects
a password, it hands the browser to SAS's own `/login` page.

## The route from here

| Task | Renders anything? |
|---|---|
| 11 API routes are NetworkOnly | no — worker policy |
| 12 Prompt-to-reload update policy | no — **but this is the task that registers the worker** and edits T01's three absence assertions |
| 13 No secret in the bundle | no — a build assertion |
| 14 PKCE verifier | no — the machinery T16's button uses |
| 15 Authorize request shape | no — `state`, `nonce`, `S256`, exactly `openid profile email` |
| **16 No password field** | **yes — the first visible screen** |

So five tasks stand between here and a screen, and none of them is a UI task. Nothing needs the stack running until
T16, but it is worth keeping it startable, because the first thing anyone will want to do with T16 is sign in for
real.

## Owner decisions taken 2026-10-24, binding on T14–T16

**OIDC configuration is derived at runtime, not baked from `VITE_*` keys.**

`design.md` §4c proposes — and Q6 confirmed — `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`
and `VITE_API_BASE_PATH`, read through `import.meta.env`. But T01 asserts that **no** production source file contains
`import.meta.env` or any absolute URL (`shellBuildsAndMounts.test.tsx:123-124`), so following §4c literally would
mean amending a frozen test *and* adding the keys to `infra/local/compose.yaml`'s `frontend` service, which passes
none today. The decision is to derive instead:

| Value | Where it comes from | Evidence |
|---|---|---|
| issuer / authorize / token / end-session | the SPA's **own origin**, relative paths (`/oauth2/authorize`, …) | L9 — SPA and every API sit behind one edge; `infra/local/edge/Caddyfile` |
| `client_id` | the constant `checky-spa` | `services/auth/src/main/resources/application.properties:51` — hardcoded, **no** env override |
| `redirect_uri` | `location.origin + '/app/callback'` | registered default at `application.properties:52` |
| `post_logout_redirect_uri` | `location.origin + '/app/signed-out'` | registered default at `:53` |
| API base | relative `/api/v1` | edge path map, `design.md` §4c |

Consequences, recorded rather than hidden: §4c's four keys end up **unused**, which deserves a dated note in
`design.md` from whoever next opens the spec (`spec/` is read-only during frontend task execution); T01 stays green
untouched; and R58/L10 ("no secret, no non-public env key in the bundle") become trivially true, which T13 should say
plainly rather than claim as a result it engineered.

## Collisions checked, so T16 does not discover them

1. **T01's "empty shell" assertion does not block a real screen.** `shellBuildsAndMounts.test.tsx:100` asserts
   `shell.innerHTML === ''`, "no text, not even whitespace". Probed on 2026-10-24 by giving the `sign-in` route a
   real element, as T16 will: **T01 still passes 7/7**, because that case mounts at jsdom's default location, which
   is outside the `/app` basename, so the router matches nothing. Probe reverted.
2. **`routes.tsx` is ready for it.** Its own comment says "Screens are attached by their own tasks", and the route
   objects are `{ path }` with no `element` — which is why `/app/sign-in` renders a blank shell today. T16 attaches
   the element; it changes no route path.
3. **The i18n catalogue already exists** (T07: `createI18n()`, typed keys, no global instance) and expects an
   `<I18nextProvider>`. T16 is the first task that must actually provide it, and T07's `RateLimitNotice` becomes
   visible for the first time through the same wiring (that wiring is task 16's per T07's phase 13 note).
4. **The worker is not registered yet** (T09 gate decision D1), and task 12 owns turning it on. A screen at T16 does
   not depend on it.

## Running it in front of the full backend stack

Verified on 2026-10-24. Docker Desktop's VM now has **8.3 GB** (the 8 GB recommendation was applied; the stack needs
about 2.5 GB).

```
open -a Docker                       # the daemon must be up first
infra/local/bin/up --frontend        # whole platform + the SPA dev server
infra/local/bin/status --wait        # auth, crypto, notification, payment -> ready
infra/local/bin/dev-account MERCHANT # a ready account: verified, TOTP enrolled, role granted
infra/local/bin/totp <secret>        # the current 6-digit code
```

| URL | What |
|---|---|
| http://localhost:5173/app/ | the SPA (blank shell until T16) |
| http://localhost:5173/app/sign-in | **the first screen, from T16** |
| http://localhost:8025 | Mailpit — every email the platform sends |
| http://localhost:5173/.well-known/openid-configuration | discovery, through the edge, as the browser sees it |

`infra/local/bin/smoke` runs the whole path (register → verify → PKCE sign-in → MFA → MERCHANT → invoice → pay →
ATTESTED → receipt → HELD → sign-out) and prints `SMOKE PASSED`. Run it before demoing anything, because it fails
loudly on exactly the environmental problems below.

## Traps that have actually bitten, newest first

- **Corrupt anvil chain state stops the whole stack (2026-10-24).** `/anvil/state.json` was truncated mid-flush when
  the Docker VM went down, and anvil refuses to start:
  `failed to parse json file: "/anvil/state.json": EOF while parsing a string at line 1 column 4984832`. It exits 2,
  so `crypto` never starts, then `payment`, `notification` and `edge` never start, and `/app/` answers nothing —
  while `auth` alone reports ready, which looks like a service bug and is not. Fix: `bin/down --reset` (it is
  `down -v`, so it deletes the Postgres volume too) then `bin/up --frontend`. Keeping Postgres while resetting only
  the chain leaves crypto's block cursors ahead of a fresh chain, which is the mismatch the state file exists to
  avoid (`compose.yaml:88-89`).
- **`bin/up` can exit non-zero while most containers are running.** Check the exit code, not the container list, and
  never through a pipe — `bin/up … | tail` reports the exit status of `tail`. That masked this failure twice today.
- **Docker VM clock lag after the Mac sleeps** makes TOTP fail → `infra/local/bin/fix-clock`.
- **`auth-postgres-1` and `auth-kafka-1` are the user's own containers** (ports 5432/9094). Never touch them. Some
  notification/payment integration tests need `auth-kafka-1` on :9094.
- **Pay-to addresses must be EIP-55 checksummed** → `bin/chain new-address`, one per invoice.
- **On the macOS host (Docker down), one frontend test fails environmentally**: `lintRejectsHandWrittenBackendFetch`
  AC4 expects `Found 0 warnings and 0 errors`, and this host's `oxlint@1.87.0` prints no summary line at all.
  Baseline before blaming any change.
