# frontend · T25 · Phase 0 — Repository Understanding

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Return target outside allowlist.** A return target off the allowlist falls back to the home route. Verifies
> `returnTargetOutsideAllowlistFallsBackToHome`. Cites R7, L1. — `spec/frontend/tasks.md:37`

**The headline finding, measured rather than assumed: every rejection family this task names is already refused,
and the mechanism needs no change.** T24 (closed 2026-10-29) built the whole of `auth/returnTarget.ts`, and the
spec splits the verification in two — `tasks.md:36` is "a return target **on** the allowlist is honoured" and
`:37` is "**off** the allowlist falls back". Both cite the same named test. So T24 built the mechanism and the
harness and left T25 fifteen `it.todo` rows. **T25 looks like a pure verification task.** Whether that is the
right reading of the task statement is Phase 1's and Phase 2's to settle, not this phase's.

---

## 1 · Architecture summary

The `frontend/` workspace is a **React 19.3.1 / TypeScript 7.0.2 SPA** built with Vite 8.3.5, tested with Vitest
5.0.5 on jsdom 30.1.3, routed by react-router 8.4.0, linted by oxlint 1.87.0, in a pnpm workspace. It is **not** a
Spring service, so the Phase 0 prompt's JPA/Flyway/Testcontainers/outbox headings have no counterpart here; the
equivalents are given below.

| Concern | Where it lives |
|---|---|
| Route set, the single source | `src/app/routes.tsx` — `APP_BASENAME = '/app'`, `PUBLIC_ROUTE_PATHS` (9), `AUTHENTICATED_ROUTE_PATHS` (11), `appRoutes`, `createAppRouter()` |
| OIDC client | `src/auth/` — `authorizeRequest.ts` (PKCE, the three single-use items, `consumeAuthorizeState`), `codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts` (in-memory holder), `SignInScreen.tsx`, `CallbackScreen.tsx` |
| The post-login destination | `src/auth/returnTarget.ts` — **new in T24**, 139 lines (≈30 code) |
| Persistence | **none.** `sessionStorage` under L16's carve-out is the only client-side state, and access tokens are memory-only (L3) |
| Events / outbox | **none.** No analytics, no telemetry sink |
| Security posture | public OIDC client with PKCE, no client secret in the bundle (L1); `state` and `nonce` validated on every callback; exact-match redirect URIs; allowlisted post-login return target — **the clause this task verifies** |

## 2 · Existing code this task touches

### Already exists — all of it

| File | Role for T25 |
|---|---|
| `src/auth/returnTarget.ts` | the module under test. `RETURN_TARGET_KEY` (`:39`), `allowlistedTargets()` (`:51-59`), `homeTarget()` (`:62-64`), **`isAllowlistedTarget()` (`:70-72`, exported for this task by name — T24 F6)**, `rememberReturnTarget()` (`:88-96`), `takeReturnTarget()` (`:120-139`) |
| `src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | the named test, 1072 lines, 67 cases + **15 `it.todo` rows at `:1055-1070`** which are T25's |
| `src/auth/CallbackScreen.tsx` | `:194-195` — `const target = takeReturnTarget(); if (onScreen.current) navigate(target, { replace: true });` |
| `src/app/routes.tsx` | `HOME_PATH` (`:19`); `AUTHENTICATED_ROUTE_PATHS` (`:35-47`) is what the allowlist is derived from; `PUBLIC_ROUTE_PATHS` (`:22-32`) supplies the "a public route" row |

### New

Nothing, on the evidence of this phase. No file to create, and **no production change identified** — see §5.

### The harness T25 inherits, by name

The named test already carries everything a rejection row needs, which is why T24 wrote the file and stopped:

`arriveAt(url, answer, {strict})` (`:215`) mounts the real route graph on `createMemoryRouter` with a stubbed
`fetch` and returns `{container, router, fetchSpy, holder, unmount}`; `unmount` deliberately does **not** dispose
the router, so a case can still read where it did or did not go · `startSignIn()` (`:260`) runs the real authorize
step and refuses to return a triple it cannot read back · `remember(target)` (`:294`) writes through the real
producer and refuses to continue if the store did not take it · **`seedRaw(value)` (`:301`)** puts a value in by
hand, which is what every T25 row needs, because the producer now filters on write (T24-D15) and would refuse
them · `signInAndLand()` (`:317`) · `watchStorage()`/`removedKeys`/`writtenKeys`/`storedKeys` · `denyReadOf`,
`denyRemovalOf` · `logReturnKeyOrder()` · `deferredAnswer()` · `EXPECTED_ALLOWLIST` (`:365`), derived
independently of the module under test.

### Measured: the predicate's answer for all 21 shapes in T25's families

Run against the real `isAllowlistedTarget` in a throwaway probe (deleted; results recorded here):

| Family | Shape | Answer |
|---|---|---|
| absolute, other origin | `https://evil.example/app/home` | refused |
| absolute, this origin | `http://localhost:5173/app/home` | refused |
| protocol-relative | `//evil.example/app/home` | refused |
| `javascript:` | `javascript:alert(1)` | refused |
| `data:` | `data:text/html,x` | refused |
| backslash | `\\evil.example`, `/\evil.example` | refused |
| traversal | `/home/../admin`, `/../../etc/passwd` | refused |
| encoded traversal | `/home/%2e%2e/admin`, `/home%2f..%2fadmin` | refused |
| prefix match | `/homeward`, `/accou` | refused |
| public route | `/sign-in`, `/callback` | refused |
| nonexistent | `/not-a-route` | refused |
| parameterised, real id | `/invoices/123`, `/receipts/abc` | refused |
| query / fragment | `/home?x=1`, `/home#top`, `/account/sessions?a=1#b` | refused |
| **controls** | `/home`, `/account/sessions` | **legal** |

All 21 refused, both controls legal, with no shape-specific code anywhere: the check is **set membership**
(`returnTarget.ts:70-72`), so each is refused for the same reason.

## 3 · Established patterns to follow

- **Set membership, and nothing else.** `returnTarget.ts`'s head comment is explicit that there is no prefix test,
  no pattern match and no normalisation pass — and that this is the point, because there is then nothing to strip
  and nothing to decode. Any T25 row that wants a shape *handled* rather than *refused* would be a design change.
- **The allowlist is derived from the route table**, read **inside a function**. A module-scope read lands in the
  `routes → CallbackScreen → returnTarget → routes` cycle's temporal dead zone; T16 measured Vite yielding
  `undefined`, and an empty allowlist **fails closed silently**. A brace-depth scan in the named test (`:338`,
  `:753`) keeps it out.
- **`takeReturnTarget()` is total** (T24-D10): `CallbackScreen` calls it inside `.then(onFulfilled, discard)`, and
  a synchronous throw from `onFulfilled` is *not* caught by the second argument.
- **The storage carve-out is two constants in two modules** (T24-D4), deliberately unmerged; task 26's guard must
  enumerate both.
- **Flat config, no YAML**; `agents.md` per service is authoritative and never restated.
- **Commit and push per phase**, every phase.

## 4 · Testing conventions

- **Run recipe:** `npx -y pnpm@12.14.0 exec vitest run` — bare `pnpm` is not on this host. `--reporter=basic`
  does not exist in vitest 5 and is a startup error.
- **Read the exit code, never the pass counts.** An unhandled rejection prints *below* the all-passed line.
- **Baseline today:** `tsc --noEmit` exit 0; suite **1075 passed / 15 todo / 1 failed**, the failure being the one
  known environmental case, `lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0` prints
  no summary line (recorded at T18; CI must pin the failure's identity, which no task owns).
- **A `Storage.prototype` or console spy must be installed before the code under test runs.**
- **`createMemoryRouter` writes neither `window.location` nor `history`.** `useLocation().pathname` **strips** the
  basename; `router.state.location.pathname` keeps it. Sweep `router.state.location`.
- **The module-instance trap:** `arriveAt` imports the route graph dynamically and `vi.resetModules()` runs
  between cases, so a statically imported module is a *different* instance than the screens hold. The named test's
  header explains why its own static imports are nonetheless the right ones.
- **Vacuity is the standing risk**, and the house rule is in the named test's header: every destination case
  asserts the destination **and** that the other destination was reachable from the same harness.
- **Mutation harness after the tests, and the tests are committed first** — it reverts with `git checkout`. It
  compares collected totals *and* exit code, and scores an unparseable run as BROKEN, not KILLED.
- **Two traps T24 learned the hard way, both directly relevant here:** a boundary case whose expected answer is
  the fallback cannot distinguish a wrong answer that is also the fallback (T24's `' /home'` rows were inert
  against an implementation that trimmed); and a stub that denies *every* write makes "nothing was stored"
  unfalsifiable.
- No JUnit, no fixed `Clock`, no Testcontainers, no ArchUnit in this service — the prompt's headings are a
  backend template.

## 5 · Known gaps and unknowns

1. **It is not yet established that T25 requires any production change.** Twenty-one shapes measured, all refused.
   Phase 1 and Phase 2 must decide whether the task is therefore verification-only, or whether something in R7/L1
   is still unmet. **I do not know** which, and will not assume.
2. **I measured the predicate, not the router.** Every answer in §2 is `isAllowlistedTarget`'s. What a *flow* does
   with a hostile value if it ever reached `navigate()` — whether react-router would treat
   `https://evil.example/app/home` as a path or a location — is unmeasured, and it matters for what a T25 row's
   control should assert (that `window.location` never changed, not merely that the pathname is home).
   **I do not know** react-router 8.4.0's behaviour there yet.
3. **The destination renders nothing.** `/app/home` is registered with no element and no task in the 142-task list
   builds a home screen (T24-D11). No T25 row may assert more than the navigation.
4. **A constraint that could be over-applied:** T24 recorded that `bundleContainsNoSecretEnvKeys` forbids a
   scheme-qualified absolute address in production source, comments included (L9, L10, R58). Checked this phase:
   that guard scans the **built production bundle** (`src/app/bundleContainsNoSecretEnvKeys.test.ts:38`, `:214`,
   and `KNOWN_ABSOLUTE_URLS`), and **test files are not in the bundle**. Several existing test files already
   contain `https://` literals. So T25's rows may use real scheme-qualified URLs; only production source may not.
5. **`oidcRoundTripEndToEnd` has no Playwright suite and no owning task** — the largest verification gap in the
   package, and the reason no rejection row can be proven against a real browser.
6. **Three further unowned pieces:** the composition root (`mountShell.tsx`), silent re-authentication, and a
   `/app/home` screen.
7. **No production caller.** `rememberReturnTarget` is called by nothing until task 41 (R28's redirect), so every
   sign-in in the running SPA takes the fallback today. That makes T25's subject the *only* path production
   currently exercises — which is an argument for its thoroughness, not against it.

---

## Next — Phase 1 — specification extraction.
