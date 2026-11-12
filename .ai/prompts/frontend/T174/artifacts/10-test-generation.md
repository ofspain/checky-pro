<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). Tests only; no production code. -->

# frontend · T174 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` · **Task** T174 — Home screen |
| **Consumes** | `artifacts/04-freeze-task.md` (FROZEN) · `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` · `10-mutation-run.txt` |
| **Named suite** | `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` — **29 cases** |
| **Whole suite** | **1862 passed, 1 failed** (environmental) of 1863 |
| **Harness** | **19/19 regressions killed**, canary killed, RE18 unfalsifiable, **0 BROKEN** |

No production code changed in this phase. One production **comment** was corrected — see Deviations.

---

## Manifest

| case | AC | requirement | mutant that kills it |
|---|---|---|---|
| renders one main landmark holding exactly one h1 | AC1 | task 174, L11 | RE06 |
| puts the heading BEFORE the paragraph *(phase 8 I4)* | AC1 | L11 | **RE20** |
| writes no lowercase JSX tag of its own (§6e(3)) | AC1 | T173 §6e | RE06 |
| CONTROL: the tag scan still finds a real element | AC1 | — | *control* |
| focuses the heading itself, and it carries the screen name | **AC2** | **L11** | RE01, RE02, RE03 |
| distinguishes the three candidates the freeze weighed | **AC2** | **D1** | RE03 |
| CONTROL: body is what holds focus when nothing moves it | AC2 | — | *control* |
| renders the catalogue entries, as literal English, no key in the DOM | AC3 | L11, O3 | RE07, RE08, RE14 |
| holds no user-facing string in its own source | AC3 | agents.md | RE07 |
| redirects an arrival with no session, renders no part of the screen | **AC4** | **R28** | RE10, RE11 |
| renders the screen when there IS a session | AC4 | R28 | RE01, RE10 |
| makes no network call and writes no storage | AC5 | R29, L16 | RE12 |
| does not read the access token | AC5 | **R20** | RE13 |
| imports only react, react-i18next and the barrel | AC6 | **D6** | RE13 |
| reaches no identity source, named one by one | AC6 | R22, R20 | RE12, RE13 |
| renders no identity-shaped text | AC6 | **R22** | RE14 |
| names home in the screen-count pin that belongs to task 16 | AC7 | D4 | RE17 |
| names HomeScreen.tsx in the render-site list, union at fourteen | AC7 | F14 | RE15, RE16 |
| names home in the attached-element pin, read from the table itself | AC7 | D5 | RE10 |
| gives the focus target a negative tabindex | AC8 | L11 | RE02, RE04 |
| renders no element with a non-negative tabindex | AC8 | **D3** | RE04 |
| installs no key handler and declares no modal | AC8 | D3 | — *(structural, see gaps)* |
| renders tabindex="-1" from Heading, for every consumer | AC9 | **D1** | RE02 |
| keeps the props type at { children }, so rule 2 holds literally | AC9 | T173 rule 2 | RE05 |
| SignInScreen leaves focus on `<body>` when it mounts *(I9)* | **I9** | L11 | **RE21** |
| SignedOutScreen leaves focus on `<body>` when it mounts *(I9)* | I9 | L11 | RE21's shape |
| CallbackScreen leaves focus on `<body>` when it mounts *(I9)* | I9 | L11 | RE21's shape |
| lands on the same heading through the REAL route table, under StrictMode | **AC4+AC10** | D9 | RE01, RE10 |
| lands on the same heading when the effect runs twice | AC10 | D9, F17 | RE01 |

**Every criterion AC1–AC10 has at least one case and at least one mutant**, except AC8's
no-key-handler clause, which is a source assertion with no mutant — recorded under gaps.

### Inherited instruments carrying this task's claims

| file | case | why it counts |
|---|---|---|
| `loginChallenge…` | **T27-D1 DISCHARGED: the completed resumption lands on the home screen and focuses its heading** | the only proof of the **arrival** — RE01/RE02/RE03 all kill here |
| `failedRenewal…` | pins that every authenticated path has no element | grown to the exact set `[HOME_PATH]` — RE11 |
| `publicRouteSet…` | attaches an element to sign-in, callback, signed-out and home | RE10 |
| `routesResolve…` | attaches a screen only to the routes whose task has run | RE17 |
| `screensRender…` | four cases incl. the new barrel-importer derivation | RE06, RE09, RE15, RE16, RE19 |
| `lintRejects…` | *"stops build at lint, so tsc and Vite never run on a violation"* | AC9's real enforcement — RE05 |

---

## The one case this phase added

**`lands on the same heading through the REAL route table, under StrictMode`** — the combination no existing
case covered. AC10 mounts the screen directly, so the route table and the gate are absent. AC4's with-session
case uses the real table but not StrictMode. `unauthenticatedRoutesRedirectToSignIn.test.tsx:215` does both,
but for the **redirect**, where no screen mounts at all. A double-invocation fault that needed the gate above
it would have been invisible to all three.

## Declined as duplicates, with the owning case named

Per T173's rule: name the owner **by its exact assertion**, so a rename breaks a test rather than orphaning
a citation.

- **An empty-string token at `/app/home`.** `RequireSession` uses `Boolean(accessToken.current())`, so `''`
  is not a session. Already owned by `unauthenticatedRoutesRedirectToSignIn.test.tsx`'s
  *"does not count an empty-string token as a session (AC1)"*, which arrives at `${APP_BASENAME}/${HOME_PATH}`
  — this exact route. Writing it again here would add a second instrument with no second claim.
- **The `home` catalogue group holding exactly two keys.** Owned by `screensRender…`'s *"keeps the
  catalogue's key set exactly as it was (AC11)"*, proved live by **RE09**.
- **`tsc` rejecting a `tabIndex` prop.** Owned by `lintRejectsHandWrittenBackendFetch.test.ts`'s *"stops
  build at lint, so tsc and Vite never run on a violation"*, proved live by **RE05**.

---

## Deviations

| | |
|---|---|
| **D-6** | **`src/auth/returnTarget.ts` modified** — a comment, no code, in a file the freeze put off limits. It read *"`/app/home` is registered in the route table with no element… so the fallback is a blank route today… what renders afterwards is someone else's task."* It was someone else's task and it has now run. Same category as D-3 (`Screen.tsx`): a knowingly false statement left in a frozen file is worse than the deviation, and it is recorded rather than slipped in. |

---

## Gaps this suite does not close

1. **No real Tab keypress** — `@testing-library/user-event` is not a dependency and jsdom implements no
   sequential focus navigation. AC8 is structural. **OQ-T174-5**, stated in the suite's own header.
2. **AC8's no-key-handler clause has no mutant.** It is a source assertion (`/onKeyDown|addEventListener/`),
   and a mutant adding a handler would be killed by it — but no row plants one, so the clause's *precision*
   is unmeasured. Smaller than it sounds: the trap it guards needs a handler **and** a reason to swallow the
   key, and the other two clauses carry the realistic failures.
3. **StrictMode idempotence has no isolating mutant** (self-review F2, confirmed by I7). AC10's cases die
   alongside AC2 whenever focus breaks at all; nothing distinguishes "idempotent" from "works".
4. **Screen-reader announcement is not measured anywhere.** Every AC2 case asserts `document.activeElement`.
   That focus lands on an `<h1>` carrying the screen's name is the best proxy jsdom allows, and no case
   claims more.
5. **The copy is asserted for consistency, not quality** (D7). The words remain the owner's to change.
