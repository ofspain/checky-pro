<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation Notes) -->

# frontend · T42 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/04-freeze-task.md` · `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## What was built

| | |
|---|---|
| `app/ui/Banner.tsx` | the ninth primitive: `<header>`, `{ children }` only, no `role` prop |
| `app/AuthenticatedLayout.tsx` | the layout: `Banner` + one `ActionButton` + `<Outlet />`, one `useEffect`, one boolean |
| `app/routes.tsx` | one pathless layout route inside the gate; the eleven paths are now its children |
| `i18n/catalogue.ts` | one group, one key: `shell.signOut` |
| nine instruments grown | listed below, each naming task 42 |
| `auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **51 cases** — T41's 35 and task 42's 16, under the describe prefix T41 reserved |

**State:** suite **1879 passed, 1 failed of 1880** (the documented host `oxlint` case), `tsc` exit 0,
`oxlint` exit 0. Committed and pushed before any harness run.

## The measurement the plan existed for

Phase 5 ordered the route table wired while the layout only wrapped, then the whole suite run **before one
instrument was touched**. The freeze predicted **six** failures. The measurement gave **nineteen** (plus the
environmental one), in six files.

| predicted? | instrument | what it actually read |
|---|---|---|
| **no** | `publicRouteSetIsExactlyTheDeclaredSet`'s `withProbes` — **13 arms through one baseline assertion** | the derivation walked the gate's children expecting eleven paths and found one pathless layout, so it attached 1 probe where it asserts 21 |
| **no** | the same file's **AC12** | read `gate.children.map(path)` and got `[undefined]` |
| **no** | the same file's **AC13** | *"exactly one pathless route carrying exactly one element"* — now three. **The pin T40 wrote for exactly this**, and the freeze did name this one (D6) |
| **no** | `routesResolveUnderAppPrefixOnly`'s `pathTree` | the nested literal gained a level |
| **no** | `returnTargetOutsideAllowlistFallsBackToHome`'s match chain | `['/', '(index)', '(index)', 'home']` — a second pathless level |
| **yes** | `screensRender…`'s lowercase-tag sweep and barrel-importer derivation | nine bearers, seven importers |
| **yes, later** | the two body-text equalities (D6) | fired at step 6, with the message `'Sign outHomeYou are signed in…'` |
| **yes, later** | T173's catalogue key set | `shell.signOut` |
| **yes, later** | T36's `PERMITTED_IMPORTERS` | fired **with its own instructions in the message** |
| **predicted and did NOT fire alone** | T173's primitive-count and union pins | they are **hand-written lists**: blind until the new entries are added, then they fail. A count pin over a list nobody has edited yet measures nothing |

Two further things only a run could have said:

- **`tsc` is a stronger instrument here than any scan.** T173 built two `Record<(typeof PRIMITIVES)[number], …>`
  maps — required utilities and expected ref tag — so a ninth primitive **cannot compile** until it declares
  what tokens it owns and what element its ref must land on. Neither map is a test; both are types.
- **A latent order-dependent flake exists in T16's suite.** In the first whole-suite run,
  `signInStarts…`'s *"re-arms the attempt"* and *"writes exactly the three items T15 writes"* failed
  (`assign` 0 calls, `setItem` 6 instead of 3). The file passes **31/31 alone**, and the **identical tree**
  ran clean on the next whole-suite run. So it is timing or ordering, not this change: nothing here touches
  the sign-in screen except by adding one export to the barrel it imports. Recorded as **FU-T42-1**; not
  fixed, because it is T16's file and this task has no diagnosis, only a sighting.

## Deviations from the frozen brief

| | | why |
|---|---|---|
| **D-1** | **The freeze's instrument prediction was wrong by thirteen.** Six predicted, nineteen measured, four instruments unnamed | Recorded as a deviation rather than quietly absorbed, because the freeze had made it a prediction *to be measured* (D6) and T174's D-5 is the precedent. The nineteen were nine distinct claims; `withProbes` accounted for thirteen arms through a single baseline assertion, which is that function working as designed |
| **D-2** | **`routesResolveUnderAppPrefixOnly.test.tsx` and `returnTargetOutsideAllowlistFallsBackToHome.test.tsx` modified**, neither named in the freeze's Files to Modify | Both had become **false**, the permitted reason: the route tree gained a level and both spell the tree out literally. Neither was loosened — the new level is written out and, in `returnTarget`'s case, labelled (both pathless entries read `(index)`, so the comment names which is the gate and which the layout) |
| **D-3** | **`i18n/catalogue.ts`'s `home` comment corrected.** It said `/userinfo` and `/accounts/me` *"are task 42's"* | Wrong twice: their readers are tasks 30's and 31's, and this task calls neither. Same category as T174's D-3/D-6 — a knowingly false statement left in place is worse than the deviation |
| **D-4** | **`unauthenticatedRoutesRedirectToSignIn.test.tsx`'s `import.meta.url` comment corrected** | The same false diagnosis T174's phase 9 measured out of `screensRender:69`, surviving in a second file: `import.meta.url` **is** a `file:` URL here and the quoted error never occurs — the real failure is ENOENT. A false reason that tells the next reader a working pattern is broken |
| **D-5** | **The layout imports four modules, where the freeze's D9 allowlist named four and the brief's amendment had said two** | D9 is what was implemented; the two-module line in the amendment predates D11's decision about how a receiving tab leaves. Listed here so the discrepancy between two artifacts is on the record rather than resolved silently |
| **D-6** | **`Banner`'s `REQUIRED_UTILITIES` row is `['mx-auto', 'max-w-prose']`, not `[]`** | The map exists to pin colour tokens, type steps and focus indicators, and the banner owns none of those. An empty row was available and would have measured nothing; these two are the one visual claim `Banner`'s own header makes — that the chrome and the screen share one content column |

## What the freeze got right that mattered

- **D2's reasoning survived contact.** `tsc` forced `Banner` to declare its tokens and its ref target; both
  maps are keyed by the primitive union. Had the task taken F2's option (1) — render a `Surface`, relax AC9 —
  nothing would have forced either declaration, and the chrome would have had no landmark.
- **D11 works.** The cross-tab journey — a sibling broadcasts, this tab clears its token, the outlet is
  replaced by `RedirectToSignIn`, and the sign-in heading renders — passes end to end, and the listener is
  closed on unmount (asserted by unmounting, re-seeding a token, broadcasting, and finding the token intact).
- **D11's accepted consequence was measured and is harmless so far.** No T35 or T41 instrument pins the
  return target as cleared across a redirect, so the re-remembering the freeze accepted broke nothing. It is
  still a behaviour nobody has asserted either way — carried to phase 7 as a gap, not reported as a pass.

## Not done, deliberately

- No navigation links (D1). No screens for the other ten paths. No `StatusRegion` (D10). No brand string
  (D7). No session subscription (OQ-T40-1). No data call — `createAuthApi`, `readAccount` and `readProfile`
  still have no production caller, which is **OQ-T33-2** and all that remains of OQ-T36-5's substance.
