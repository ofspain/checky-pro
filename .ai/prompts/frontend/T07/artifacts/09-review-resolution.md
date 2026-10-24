# frontend · T07 · Phase 9 — Review Resolution (Human Approval gate)

STATUS: RESOLVED (2026-10-24)

Consumes `07-self-review.md` (5 findings: 1 Low, 4 Info) and `08-independent-review.md` (Kimi 2.7: 2 new Low findings
plus corroboration of all 5). Each claim was checked against the phase 6 code at the cited line before it was decided,
and the one accepted behavioural change was probed before and after. The owner decided the two dispositions below; the
rest carry no change.

## Checks made before deciding

- **Kimi's two new findings are factually accurate but neither is a defect**, and both of its preferred fixes are
  unavailable to this phase:
  - finding 1's option (b), a `createTabRateLimitKit()` factory, is a **new public API**, which this phase forbids
    ("do not change public APIs"). Only its option (a), documentation, was open;
  - finding 2 asks for a constraint in `design.md`, but `spec/**` is in Files NOT to Modify and the guardrails forbid
    touching the specification. Only a code comment was open.
- **L11's "managed focus" is written for screens** (`design.md:47`, `agents.md:95`: "across redirect and MFA steps"),
  and the notice is not mounted anywhere yet, so self-review finding 1 is WCAG-AA hygiene rather than a breach of a
  LOCKED decision. It was accepted on its merits, not under duress.
- The frozen brief's "no focus theft" is about **showing** the notice; moving focus in response to the user's own
  dismiss does not conflict with it.
- The T07 test file does not exist yet (phase 10 writes it), so no frozen test is rewritten by this change.

## Resolution log

| Source | Finding | Decision | Change |
|---|---|---|---|
| P7-1 / P8 corr. 1 | Dismissing drops keyboard focus to `<body>`: the dismiss button is unmounted while it has focus | **ACCEPTED** (owner, minimal fix + carry-forward) | `RateLimitNotice.tsx`: the wrapper takes a ref and `tabIndex={-1}` (programmatic focus only, so it never enters the tab order), and a local `dismiss()` records whether the dismiss button held focus, calls `notice.dismiss()`, and focuses the wrapper **only in that case** — a mouse user whose focus is elsewhere is not pulled away. The comment states that a screen can later restore the element focused before the notice appeared; the component cannot know it. No change to the notice state, the middleware, the catalogue or the copy. |
| P8-1 | `createRateLimitNotice()` has no singleton guard, so two instances would silently decouple the middleware from the component | **ACCEPTED as documentation only** (option (a); the factory is rejected as a public-API change) | `rateLimitNotice.ts`: two lines added to the doc comment — wiring creates exactly one instance per tab and passes that same instance to `createAuthApi` and to `<RateLimitNotice>`; with two, the component subscribes to a flag the middleware never sets. No code change. |
| P8-2 | A future service client could be built without the notice and lose R23 | **ACCEPTED as documentation only** (`design.md` is out of bounds) | `client.ts`: two lines added to the doc comment — any further generated client (payments, notifications) takes the same notice and registers it first, as here, or it loses R23's copy. No code change. |
| P7-2 / P8 corr. 2 | `rateLimit` is optional, so wiring could forget it | **No change; carried forward** | None. Optional is the frozen brief's choice, so T05/T06 callers stay unchanged (L12). The wiring task must pass it and assert a 429 end to end; recorded below and for phase 13. |
| P7-3 / P8 corr. 3 | A second 429 while shown isn't re-announced | **No change** | None. The brief requires one notification per burst, the copy is identical, and the visible notice stays. A 429 after a dismissal re-announces, because `shown` flips false → true. Both reviews call it acceptable. |
| P7-4 / P8 corr. 4 | `subscribe`'s unsubscribe returns a boolean | **No change** | None. `() => listeners.delete(listener)` is assignable to `() => void` and `useSyncExternalStore` ignores the value. |
| P7-5 / P8 corr. 5 | A rejected `init` would be unhandled | **No change** | None. With inline resources and `initAsync: false` the init is synchronous and cannot reject; an unexpected rejection surfaces uncaught rather than being swallowed, the same policy as T05/T06 use for hook and listener throws. |

Kimi's "non-gaps" list (status-only detection, middleware order, no replay or timer, typed catalogue keys, no
module-level mutable state, T05/T06 untouched, nothing stored or logged) was re-checked against the files and needs no
action.

## Verification after the changes

- **Focus probe** (a scratch Vitest file plus a copy of the phase 6 component taken from `fe68f3d1`, both deleted after
  the run; jsdom, `react-dom/client` + `act`, under `<I18nextProvider>` as the brief requires):
  - **phase 6 component:** raise a 429, focus the dismiss button, activate it → `document.activeElement` is
    `document.body`. The reported defect is real;
  - **after the change:** the same sequence leaves `document.activeElement` on the wrapper (`tabIndex` −1), with
    `isShown()` false and the live region empty, so dismissal still clears the notice;
  - **mouse case:** with focus on an input outside the notice, activating dismiss leaves focus on that input.
- `tsc --noEmit` exit 0. `oxlint --no-ignore -c .oxlintrc.json src` exit 0. `vite build` exit 0 (93 modules).
- **Suite: 98 passed, 1 failed, both before and after the change** — the failure is environmental, not T07's.
  `lintRejectsHandWrittenBackendFetch` AC4 asserts oxlint prints `Found 0 warnings and 0 errors`; the host's
  `oxlint@1.87.0` binary prints **no summary line at all** (confirmed by running it directly, and by running it over a
  deliberate `fetch` violation: the diagnostic is printed, the summary is not). The same case fails with the phase 9
  changes stashed. This phase ran on the macOS host, not the container the earlier phases used (the Docker daemon is
  down and the workspace was installed locally), and `pnpm` had to be shimmed onto `PATH` for AC2's
  `pnpm run build` sub-case, which then passed (32 cases in that file: 31 pass, 1 fails on the summary text).
- `git status`: only `client.ts`, `rateLimitNotice.ts` and `RateLimitNotice.tsx` modified (plus the user's untracked
  `prompt`). No spec file, no T01–T06 source or test, no `AppShell.tsx`.

## For phase 10 (on top of the frozen cases)

- **Frozen case 6 (AC4, dismiss) gains the focus assertion:** with the dismiss button focused, activating it leaves
  focus on the notice wrapper, not on `<body>`; and with focus on an element outside the notice, dismissing leaves it
  there. A matching **mutation break**: remove the wrapper's `tabIndex`/focus call and the first assertion fails.
- **AC3's "no digit" must be asserted against text, not markup.** The wrapper now carries `tabindex="-1"`, so a digit
  appears in `innerHTML`. Assert on the live region's `textContent` and the button's label (and keep the
  `setTimeout`/`setInterval` spies as frozen).

## Carried forward (not T07 code)

- **The wiring task (first screen, task 16)** must pass the tab's notice to `createAuthApi`, render
  `<RateLimitNotice>` under the tab's `<I18nextProvider>`, and assert a 429 shows the copy end to end — the optional
  argument is otherwise easy to forget (P7-2). It should also restore the element focused before the notice appeared,
  which only a screen can know (P7-1).
- **Any further generated client** registers the same notice middleware first (P8-2, now a comment in `client.ts`).
- **Exactly one notice instance per tab**, shared by the client and the component (P8-1, now a comment in
  `rateLimitNotice.ts`).
