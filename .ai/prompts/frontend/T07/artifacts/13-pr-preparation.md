# frontend · T07 · Phase 13 — PR / Commit Preparation

Gate: the Phase 12 verdict is **PASS**.

Branch note: work lands on `spec/service-specs-and-ai-framework` and is pushed after each phase. For `main`, merge or
squash the branch's final tree; `main` stays deployable. Attribution: the repository's standing trailer is used (this
session's is `Claude Opus 5`), as in T01–T06 — the prompt header's `Claude Opus 4.8 (1M context)` line is stale.

## Commit title
`frontend: generic 429 notice and the i18n catalogue (T07)`

## Commit message
```
frontend: generic 429 notice and the i18n catalogue (T07)

Gives the client boundary one place that recognises a rate-limited
response and raises a single fixed notice (R23):

- createRateLimitNotice() holds one "shown" boolean per tab and exposes
  an openapi-fetch middleware that sets it on any response with status
  429 - status alone, never the body or Retry-After, so the copy is the
  same for every 429 (Q12(b)). It adds no timer, no countdown and no
  replay: onResponse returns undefined and there is no onRequest or
  onError, so each 429 reaches the caller unchanged. A burst gives one
  notice and one notification; dismiss() is the only way it clears, and
  a later 429 shows it again.
- createAuthApi(boundary, rateLimit?) registers the notice middleware
  first, so it runs onResponse last and sees the final response,
  including a 429 on the session boundary's re-send after a renewal.
  The argument is optional so T05/T06 callers are untouched (L12).
- The i18n catalogue arrives with it (O3): react-i18next, English only,
  a per-tab createI18n() factory with no global instance, and keys
  typed against the catalogue so a missing one fails typecheck.
- <RateLimitNotice notice> renders the copy in an always-present
  role="status" live region, with a dismiss button that keeps keyboard
  focus on the notice instead of dropping it to <body> (L11).
- rateLimitedResponseShowsGenericCopyWithoutReplay (29 cases).

The notice is not mounted yet: T01 asserts the shell renders no markup,
and mounting needs the first screen's provider tree. Task 8 owns the
POST no-replay guarantee under the same named test.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed
New:
- `frontend/src/api/rateLimitNotice.ts` — `RateLimitNoticeState`, `createRateLimitNotice()`.
- `frontend/src/i18n/catalogue.ts` — the English resources, `as const`.
- `frontend/src/i18n/i18n.ts` — `createI18n()` and the `CustomTypeOptions` augmentation.
- `frontend/src/app/RateLimitNotice.tsx` — the component.
- `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` — 29 cases.

Modified:
- `frontend/src/api/client.ts` — `createAuthApi(boundary, rateLimit?)`, the notice registered first.
- `frontend/package.json`, `pnpm-lock.yaml` — `i18next@26.4.2`, `react-i18next@17.0.16`, pinned exactly.

No `libs/`, `spec/`, `contracts/`, `services/`, `infra/` or CI changes. `sessionBoundary.ts`, `crossTabRenewal.ts`,
`AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx` and every T01–T06 test are untouched.

## Summary
The seventh frontend task handles being rate limited. When the backend answers any SPA call with "too many requests",
the app shows one short, fixed message — "Too many requests right now. Wait a moment, then try again." — and a Dismiss
button. There is no countdown, no number and no clock: the message says nothing the server told us about how long to
wait, because exposing that invites probing, and the only live source of a 429 today (password reset) is a public page.
Nothing is retried behind the user's back; work resumes when they act. The message stays until dismissed, even if
later calls succeed, and a keyboard user who dismisses it keeps their place on the page instead of being dropped to
the top of the document.

This task also sets up the app's text catalogue, since it is the first user-facing copy in the SPA: every string lives
in one English file, typed so a missing key fails the build. Nothing is visible in the running app yet — the notice is
mounted with the first screen.

## Testing performed
On the macOS host (the Docker daemon is down, so this task was not re-run in `themistra-local-frontend-1`; see the
caveat below):
- `rateLimitedResponseShowsGenericCopyWithoutReplay`: **29/29**.
- Whole frontend suite: **127 passed, 1 failed (128)**. The one failure is pre-existing and environmental:
  `lintRejectsHandWrittenBackendFetch` AC4 expects oxlint to print `Found 0 warnings and 0 errors`, and this host's
  `oxlint@1.87.0` binary prints no summary line at all. It fails identically with T07 stashed (baselined both ways at
  phase 9), and its AC2 case needed a `pnpm` shim on `PATH`.
- `typecheck` exit 0, `lint` exit 0, `build` exit 0 (93 modules).

Probes (phase 4, through the live stack; deleted after the runs):
- the live 429: `POST /accounts/password-reset` gave 400 ×5 then `429` `application/problem+json`,
  `Retry-After: 11` — the only SPA-originated 429 today;
- middleware order: a middleware registered **before** T05's saw the final `429`; one registered after saw only the
  `401`, because T05's re-send bypasses middleware.

Phase 9 probe: the phase 6 component dropped focus to `<body>` on dismiss; after the fix focus lands on the notice
wrapper, and a dismiss with focus elsewhere leaves it alone.

Mutation breaks: **14, each caught** (applied to production code, then reverted with `git checkout --`):
the notice middleware registered after T05's · the copy taken from the problem `title` · `Retry-After` interpolated
into the copy · a `setTimeout` auto-dismiss · no live region · notify on every 429 · a replay on 429 · matching
`>= 429` · matching 5xx · a hardcoded string instead of the catalogue · the catalogue resources removed · the wrapper
focus fix removed · `dismiss()` a no-op · the notification loop stopping at a throwing listener.

## Carried forward
- **The first screen's wiring (task 16, sign-in):** create exactly **one** notice per tab, pass that same instance to
  `createAuthApi` **and** render `<RateLimitNotice>` under the tab's `<I18nextProvider>`, with an end-to-end
  assertion that a 429 shows the copy. The `rateLimit` argument is optional for L12, so this is easy to forget, and
  R23 is not user-visible until it is done (P7-2, phases 9 and 12).
- **Focus return:** the screen should restore the element focused before the notice appeared; the component keeps
  focus inside itself, which is all it can know (P7-1).
- **Any further generated client** (payments, notifications) takes the same notice and registers it first, or it
  loses R23's copy (P8-2; now a comment in `client.ts`).
- **Task 8** owns the POST no-replay guarantee under the same named test; T07 adds no replay.
- **One in-container run** when the Docker stack is back up, to match T01–T06's verification environment.

## Specification references
- **Task:** `spec/frontend/tasks.md` task 7, "429 generic copy".
- **Requirement:** R23 (the SPA-originated 429 half; the POST no-replay half is task 8).
- **Locked decisions:**
  - `agents.md` API access: the boundary owns fixed generic 429 handling, no countdown, screens don't reimplement it;
  - `agents.md` errors: problem types map to copy, server text is never shown;
  - L4: no lockout timers on public surfaces;
  - L6: generated client only;
  - L11 and `agents.md` i18n: catalogue-only strings, a live region, keyboard operation;
  - L12: T05/T06 behaviour and suites unchanged;
  - O3: react-i18next, English only, never a raw key;
  - §5: nothing persisted;
  - Q12(b): detection by status alone, no dependence on the body or `Retry-After`.
- **Named test:** `package.md` §8, `rateLimitedResponseShowsGenericCopyWithoutReplay` → R23.
