# frontend · T16 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` |
| **Produces** | `artifacts/13-pr-preparation.md` |

---

## Proposed PR description

### T16 — the sign-in screen. The first visible UI in the project.

Sixteen tasks in, the application renders something. `/app/sign-in` shows a heading, a sentence and one button,
and that button starts the real OIDC authorization-code flow with PKCE against the auth service.

**It renders no password field** — which is the task, and the requirement (R6). The SPA never collects a
credential; Spring Authorization Server does, on its own page, behind a full-page redirect (L2).

#### Verified against the running platform, not only in jsdom

Headless Chrome driven over the DevTools protocol, against the live stack:

| | |
|---|---|
| Before the click | `sessionStorage` is `[]` |
| Click → | `/oauth2/authorize?response_type=code&client_id=checky-spa&redirect_uri=…%2Fapp%2Fcallback&scope=openid+profile+email&state=…` |
| → | `/login`, titled **"Please sign in"** |
| `input[type=password]` there | **present** |
| `sessionStorage` after the redirect | the three L16 items, verifier 43 characters |

There is a password field in this flow. It is on the server. Ours has none.

#### What ships

| File | |
|---|---|
| `src/auth/SignInScreen.tsx` | new, 72 lines |
| `src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` | new, 31 cases |
| `src/app/mountShell.tsx` | the `<I18nextProvider>` the shell has been missing since T07 |
| `src/app/routes.tsx` | `element` on `sign-in` |
| `src/i18n/catalogue.ts` | five `signIn.*` keys |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | T02's absence assertion, narrowed |
| `src/app/bundleContainsNoSecretEnvKeys.test.ts` | T13's vendor-URL list, extended with a justification |

No new dependency, no styling, no new route path. 560/562, `tsc` and `oxlint` clean. 22 of 23 scored mutants
caught.

#### Two earlier tasks' guards fired, and both were right to

- **T02** asserted no route carried an element. Narrowed, not deleted, to "exactly one route carries an element,
  and it is `sign-in`", with a named constant task 17 must widen deliberately.
- **T13** asserted the built bundle contains exactly an enumerated set of absolute URLs. Attaching the screen made
  `useTranslation` reachable from `main.tsx` — it was tree-shaken before, because nothing imported
  `RateLimitNotice` — so react-i18next's error-documentation link now ships. T13's own comment says such a URL
  "fails this file until someone justifies it here", so it was justified there. A task-13 assertion caught a real
  change in what ships, three tasks later, with nobody watching.

#### The finding a reviewer should look at

**T16 closes a circular import:** `app/routes → auth/SignInScreen → auth/authorizeRequest → app/routes`.
`authorizeRequest` has read `APP_BASENAME` from `routes` since T15; nothing in `app/` imported back until now.

It works only because that read sits **inside a function**. Hoisting it to module scope — an ordinary-looking
refactor — gives:

```
TypeError: Cannot read properties of undefined (reading 'endsWith')
  ❯ src/auth/authorizeRequest.ts:59
```

and a **blank page**: `<div id="root">` empty, measured in a real browser. Vite's transform yields `undefined`
rather than throwing on the temporal dead zone, so the error names `.endsWith` and never mentions a cycle. Rollup
emits no circular-dependency warning either.

Two tests now pin the property, and `routes.tsx` names the cycle at the import that closes it. Mutant M15 confirms
both the suite collapse and the blank page.

#### Honest limits

- **No assistive technology has been run against this screen.** `role`, `aria-live`, `aria-describedby`,
  `aria-busy` and focus behaviour are asserted structurally — that is not a screen reader doing the right thing,
  and L11 is ultimately a claim about the latter.
- **The in-flight message is rendered, not necessarily heard.** In a real browser the document is replaced. It
  earns its place for a slow or blocked navigation.
- **Contrast is browser default**, because there is no styling yet.
- **The copy is unreviewed.** Five neutral strings, chosen by me. See OQ-1 — it is one file to change, and two of
  its properties are now enforced by a test (no credential type named, no account state disclosed).
- **The cycle guard lives inside the cycle**, so the loud failure is caught by the suite collapsing rather than by
  the guard; the guard catches the quiet variant that would silently produce a wrong `redirect_uri`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

---

## Owner decision requested

**OQ-1 — the sign-in copy.** Nothing in the spec says what this screen says. The proposal:

| Key | Value |
|---|---|
| `signIn.heading` | `Sign in` |
| `signIn.explanation` | `Continue to the secure sign-in page to enter your details.` |
| `signIn.action` | `Continue to sign in` |
| `signIn.starting` | `Taking you to the sign-in page…` |
| `signIn.failed` | `Sign-in could not be started. Try again.` |

Change them in `src/i18n/catalogue.ts` alone; no component or test needs touching, as long as the copy names no
credential type and discloses no account state.

## Next task

**T17 — the callback.** It reads back the three items this task's button caused T15 to store, and it will need to
widen T02's `SCREENS_ATTACHED_SO_FAR` deliberately.
