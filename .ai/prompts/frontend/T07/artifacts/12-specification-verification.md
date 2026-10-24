# frontend · T07 · Phase 12 — Specification Verification

Consumes `04-frozen-task-brief.md`, `09-review-resolution.md`, `10-test-generation.md` and `11-test-review.md` (Kimi
2.7: 8 gaps). Date: 2026-10-24. Only the test file changed in this phase; `rateLimitNotice.ts`,
`RateLimitNotice.tsx`, `client.ts`, `i18n.ts` and `catalogue.ts` are as resolved at phase 9.

**Environment note.** Unlike T01–T06, this phase ran on the macOS host, not in `themistra-local-frontend-1`: the
Docker daemon is down, so the workspace was installed locally (see phase 9). Consequences are stated under Risks.

## Phase 11 gaps (Kimi), resolved before verification

Line numbers refer to `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`.

| Gap | Decision | Change | Break caught |
|---|---|---|---|
| 1 No mixed-endpoint burst | **ACCEPTED (premise partly wrong)** | Kimi says the burst fires "the same operation" three times; it already spans three **different** protected operations (`:322`–`:326`). The real gap is the protected/public mix, so `:598` now fires `GET /accounts/me` (bearer) and `POST /accounts/password-reset` (no bearer) together and asserts both get 429, one notice, **one** notification — the notice is the tab's, not the request's. | `M6`, `M13` |
| 2 No programmatic `dismiss()` | **ACCEPTED** | `:623` calls `notice.dismiss()` directly, with no button click: shown → cleared, the component re-renders empty, the control disappears, and subscribers are notified exactly once. A second call changes nothing and notifies nobody (the `shown === next` guard, `rateLimitNotice.ts:24`). | **new `M13` (`dismiss()` a no-op)** |
| 3 No copy invariance on the re-send path | **ACCEPTED** | `:660` repeats the renewal case with a noisy 429 on the re-send — `text/html` body naming "60 minutes", `Retry-After: 3600` — and asserts the whole rendered notice is still exactly the catalogue's two strings. | `M2`, `M3`, `M11` |
| 4 A healthy subscriber after a throwing one | **ACCEPTED as explicit; largely covered already** | The thrower is registered **before** the component (`:565` vs `:573`), so the component rendering the copy already proved the loop continues. Made explicit: a counting subscriber registered after the thrower must fire exactly once (`:570`–`:573`, `:586`). | **new `M14` (loop stops at a throw)** |
| 5 No transport-call count in the burst | **REJECTED — factually wrong** | `expect(sent).toHaveLength(3)` is already asserted at `:327`, immediately before the notification count. No change. | `M7` |
| 6 No 429 → dismiss → 200 → 429 sequence | **ACCEPTED** | Folded into `:623`: after the direct dismiss, a 200 arrives (notice stays cleared), then a 429 shows it again with a third notification. | `M6`, `M13` |
| 7 No privacy assertion on the error path | **ACCEPTED** | `:552` now also pins cookies, both storages and all five console levels across a 429 whose subscriber throws. | — |
| 8 Case 24 doesn't assert the notice stays hidden with no `rateLimit` | **ACCEPTED in part; the suggestion is half-invalid** | "No rate-limit live region appears" would contradict AC5 and the frozen brief: the region is **always** rendered, which is what makes the announcement fire. `:503` now asserts the region exists but holds no copy and has no dismiss button. | `M5`, `M10` |

Suite after the gaps: **29 cases, all green** (was 26).

## Mutation breaks (14)

Each mutant was applied to production code, the suite run, then the file restored with `git checkout --`. **14 of 14
caught**, i.e. every break the frozen brief lists, plus phase 9's focus fix and phase 11's two new ones.

| Mutant | Caught by (case count) |
|---|---|
| M1 the notice middleware registered after T05's | 2 (`:206`, `:660`) |
| M2 the copy taken from the problem `title` | 16 |
| M3 `Retry-After` interpolated into the copy | 17 |
| M4 a `setTimeout` auto-dismiss | 2 (`:276` spy, `:308` after an hour) |
| M5 no live region | 21 |
| M6 notify on every 429 | 3 (`:308`, `:598`, `:623`) |
| M7 a replay on 429 | 9 |
| M8 matching `>= 429` | 1 (`:463`, the 503 row) |
| M9 matching 5xx | 22 |
| M10 a hardcoded string instead of the catalogue | 1 (`:422`) |
| M11 the catalogue resources removed | 16 |
| M12 the phase 9 wrapper focus fix removed | 2 (`:292`, `:363`) |
| M13 `dismiss()` a no-op | 4 |
| M14 the notification loop stops at a throwing listener | 1 (`:552`) |

## Traceability matrix

Test references are line numbers in `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`.

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R23** fixed, generic waiting copy on an SPA 429 | Yes | `rateLimitNotice.ts:41`; `catalogue.ts:3-6`; `RateLimitNotice.tsx:30-31` | `:168`, `:262` (×7), `:660` | No | No |
| **R23** no countdown or interval | Yes | `rateLimitNotice.ts:40-43` (no timer in the path); copy has no digit | `:276`, `:292`, `:308` | No | No |
| **R23** no automatic replay of a non-idempotent POST | Yes, for T07's part | `rateLimitNotice.ts:42` returns `undefined`; no `onRequest`/`onError` | `:445` (one transport call), `:187`, `:598` (a POST 429 is not re-sent) | The POST guarantee itself is **task 8**, same named test (frozen Out of scope) | No |
| **R23** resume only on user action | Yes | `rateLimitNotice.ts:50` (`dismiss()` is the only clear) | `:308`, `:623` | No | No — "the user's own next action resumes work" is the phase 2 interpretation, frozen and unchallenged by both reviews |
| **R23** SAS-hosted page 429s out of SPA scope | n/a | — | — | No | No |
| **R23 / Q12(b)** no dependence on `Retry-After` or the body | Yes | `rateLimitNotice.ts:41` (status alone) | `:262` rows 1–4 and 7, `:660` | No | No |
| **AC1** protected / public / re-send after renewal | Yes | `client.ts:24` (notice registered first) | `:168`, `:187`, `:206` | No | No |
| **AC2** copy identical across headers, titles, bodies | Yes | `rateLimitNotice.ts:41`; `RateLimitNotice.tsx:31` | `:262` (×7), `:660` | No | No |
| **AC3** no digit; no `setTimeout`/`setInterval` | Yes | `catalogue.ts:4-5`; no timer in `rateLimitNotice.ts` | `:276`, `:292` | No | The rendered **markup** carries `tabindex="-1"` (phase 9); AC3 is asserted on text, and the case says so |
| **AC4** persists, clears only on dismiss, re-shows, one notification per burst | Yes | `rateLimitNotice.ts:24`, `:41`, `:50`; `RateLimitNotice.tsx:33-35` | `:308`, `:363`, `:383`, `:598`, `:623` | No | No |
| **AC5** `role="status"` region existing before the 429; catalogue strings | Yes | `RateLimitNotice.tsx:30`; `i18n.ts:4-9` typed keys, `:13-22` factory | `:400`, `:422` | No | No |
| **AC6** each 429 reaches the caller unchanged; transport called once | Yes | `rateLimitNotice.ts:42` | `:445` (identity-equal `Response`), `:327` | No | No |
| **AC7** non-429 statuses silent; `createAuthApi(boundary)` as in T05; T05/T06 suites unchanged | Yes | `client.ts:20` (optional `rateLimit`), `:24-25` | `:463` (×4), `:478`, `:503`; T05/T06 files untouched | No | No |
| **AC8** nothing stored, logged, or in the URL | Yes | no storage/log/URL use in any T07 file | `:531`, `:552` | No | No |
| **L4** no lockout timers on public surfaces | Yes | no timer at all | `:276`, `:187` | No | No |
| **L6** generated client only | Yes | `client.ts:1` (`createAuthClient`); no hand-written transport | `lintRejectsHandWrittenBackendFetch` (T04), `oxlint` green | No | No |
| **L11 / agents.md i18n** catalogue-only strings, live region, managed focus | Yes | `RateLimitNotice.tsx:29-35`; `i18n.ts:4-9` | `:400`, `:422`, `:363`, `:383` | Focus **return** to the pre-notice element is the screen's (phase 9 carry-forward) | No — L11's managed focus is written for screens; the notice keeps focus inside itself |
| **O3** react-i18next, English only, never a raw key | Yes | `i18n.ts:13-22` (`lng`/`fallbackLng: 'en'`, `initAsync: false`, no `initReactI18next`) | `:422`; `tsc` exit 0 proves the keys are typed | No | No |
| **L12** T05/T06 behaviour and suites untouched | Yes | `client.ts:20` optional argument | `:478`, `:503`; full suite | No | No |
| **agents.md API access** the boundary owns generic 429 handling; screens don't reimplement it | Yes | the middleware lives in `src/api`; the component only renders state | `:168`, `:503` | No | No |
| **agents.md errors** server text is never shown | Yes | no body or header read | `:262` (whole-notice text), `:660` | No | No |
| **§5** nothing persisted | Yes | one in-memory boolean | `:531`, `:552` | No | No |
| **No module-level mutable state** | Yes | `createRateLimitNotice()`, `createI18n()` are per-call factories | `:422` (a second instance), every case creates its own | No | No |
| **tasks.md task 7** verifies `rateLimitedResponseShowsGenericCopyWithoutReplay` | Yes | the named test file exists | 29 cases | No | No |

## Verification run (2026-10-24, macOS host)

- `vitest run` on the T07 file: **29/29**.
- Whole frontend suite: **127 passed, 1 failed (128)** — the failure is the pre-existing environmental
  `lintRejectsHandWrittenBackendFetch` AC4 case (this host's `oxlint@1.87.0` prints no summary line). It fails
  identically with T07 stashed; phase 9 baselined it both ways.
- `tsc --noEmit` exit 0. `oxlint --no-ignore -c .oxlintrc.json src` exit 0. `vite build` exit 0 (93 modules).
- Mutations: **14/14 caught**.
- `git status`: the T07 test file only. No production, spec, T01–T06, or `AppShell`/`routes`/`main` file changed.

## Principal engineer's answers

1. **Is the task fully complete?** Yes, for T07's frozen scope: the notice state, its status-only middleware, the
   catalogue and `createI18n()`, the component, the `createAuthApi` registration, and the named test. The frozen Out
   list is unchanged and still correct: task 8 owns the POST no-replay guarantee, and mounting waits for the first
   screen's provider tree.
2. **Does it satisfy every acceptance criterion?** Yes — AC1–AC8 each have evidence and at least one case above, and
   every acceptance criterion has a mutant that breaks it.
3. **Does it violate any LOCKED decision?** No. L4, L6, L11, L12, O3, §5, Q12(b) and the `agents.md` API-access and
   error rules are each traced above. The phase 9 focus change was checked against L11's wording before it was
   accepted: L11's managed focus is a screen rule, and the fix keeps focus inside the notice rather than taking it.
4. **Remaining risks.**
   - **R23 is not live yet.** The notice is not mounted (T01 asserts an empty shell), so the requirement is only
     user-visible once the first screen wires it: one notice per tab passed to `createAuthApi` **and** rendered under
     the tab's `<I18nextProvider>`. Carried from phase 9, to be restated in phase 13.
   - **The `rateLimit` argument is optional** (P7-2, frozen for L12), so wiring can silently lose the copy. `:503`
     pins what that costs; the wiring task needs its own end-to-end assertion.
   - **Focus return** to the element focused before the notice appeared is the screen's job.
   - **Not re-verified in Docker this phase.** The container suite run and a live 429 re-probe were not possible
     (daemon down); the live 429 was probed at phase 4 and detection is status-only, so the risk is environmental
     rather than behavioural. Worth one in-container run when the stack is back up.
   - **R42's edge limiter** may add 429s on any route with any body. Status-only detection covers that by design.

**PASS** — every acceptance criterion and LOCKED decision for T07 is implemented, traced and mutation-tested; the only
open items are the frozen out-of-scope wiring and task 8's POST guarantee.
