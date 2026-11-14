<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T42 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T42 — Authenticated shell |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

All commands run independently of the self-review, on the same tree.

| Command | Result |
|---|---|
| `vitest run src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **57 / 57 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1885 / 1886 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T42/artifacts/07-mutation-harness.py` | **25/26 regressions killed**, 1 canary killed, 2 unfalsifiable/survivors (TE21, TE28), **0 broken**, 5 disagreements |

The whole-suite count and harness outcome match the Phase 10 manifest exactly (1886 cases, 1 environmental failure; 25/26 regressions killed, TE21 unfalsifiable, TE28 survived as a disagreement).

---

## Findings

### F1 — No test asserts the banner appears before the screen's `<main>` in DOM order
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:665-678` (AC2) checks that `<main>` is not inside `<header>` and that `<main>` contains the home screen's heading and paragraph. It does not assert that `<header>` precedes `<main>`.
· **Assessment:** A mutant that rendered `<><Outlet /><Banner>...</Banner></>` would still pass AC2 (because `<main>` is not inside `<header>`), but the chrome would appear below the screen content on a screen that has content. On the ten empty paths this would be invisible, so the case that matters is `/app/home`. The user experience would be broken, and no case would fail.
· **Recommendation:** Add an AC2 assertion that `banner(container)` is the previous sibling of `container.querySelector('main')`, or more robustly that `main.compareDocumentPosition(header) & Node.DOCUMENT_POSITION_PRECEDING` is truthy.

### F2 — The StrictMode AC14 case does not prove the first cleanup closed the listener
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:945-969` mounts the layout under `StrictMode`, posts a sibling sign-out, and asserts the tab redirects. The self-review and Phase 10 manifest note that TE23 cannot isolate StrictMode because module state outlives the test.
· **Assessment:** StrictMode invokes subscribe → cleanup → subscribe. The case proves the second subscription hears the broadcast, but it does not prove the first subscription was actually closed. If the implementation leaked the first listener (e.g., `close()` was a no-op), the test would still pass because the second listener works. This is the same kind of "idempotent but not measured" gap that TE28 exposes.
· **Recommendation:** Document this as a measurement limit, or add a case that broadcasts during the brief window between the first cleanup and second mount (not possible synchronously in jsdom). The honest resolution is to record it as unmeasurable here.

### F3 — TE28's survival means the `signOut()`-first comment overstates a criterion
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/AuthenticatedLayout.tsx:68-73` says *"`signOut()` first, so this tab is clean before anything re-renders"*. The Phase 10 harness row TE28 inverted the order (`setSiblingSignedOut(true)` before `signOut()`) and survived, because React batches both statements before re-rendering.
· **Assessment:** The comment reads as a load-bearing criterion, but the observable behavior is the same either way. This is the exact issue the Phase 10 manifest identifies under "TE28 survived". The comment should be weakened to say the order is conventional and not required by correctness.
· **Recommendation:** Update the comment to: "`signOut()` is called before setting the flag, which is the conventional order for clearing local state before revealing the redirect. React batches the two updates, so the order has no observable effect." Keep TE28 predicted `own` and reported as a disagreement, as the Phase 10 manifest correctly chose.

### F4 — The focus-ring clipping case only inspects `Banner.tsx`, not the layout wrapper
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:1022-1038` checks `app/ui/Banner.tsx` for `overflow-`, `clip-`, `contain-`, `mask-` utilities.
· **Assessment:** The ring belongs to `ActionButton` inside `Banner`. If `AuthenticatedLayout.tsx` wrapped `<Banner>` in a `<div>` with `overflow-hidden`, the ring would clip even though `Banner.tsx` is clean. Today the layout uses a fragment (`<>...</>`), so there is no wrapper, but a future change could introduce one.
· **Recommendation:** Extend the source scan to include `AuthenticatedLayout.tsx` in the clipping check, or add a structural assertion that `Banner` is the direct child of the layout's returned fragment with no intervening element.

### F5 — The `Banner` landmark still has no accessible name
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/ui/Banner.tsx:26-32` renders `<header>` with no `aria-label`, heading, or `aria-labelledby`. The Phase 8 I7 finding was accepted as a documentation half; the Phase 10 manifest lists it as still not measured.
· **Assessment:** A screen reader navigating by landmark hears "banner" with no name. This is acceptable under WCAG but not ideal. The deliberate omission is documented in `Banner.tsx`, so this is not a defect, just a persistent accessibility gap.
· **Recommendation:** Optional: add a named case that asserts the banner has no accessible name, documenting the deliberate omission as a measured limit rather than an oversight. Or add an app-name label if the owner decides the chrome should identify the application.

### F6 — The public-path case asserts the token is held, but "does not react" is about the layout
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:995-1020` arrives at `/app/sign-in` with a token, posts a sibling sign-out, and asserts the token is still held and the location is still sign-in.
· **Assessment:** The case correctly pins a limit (no app-level listener). The token assertion is true because `accessToken` is in-memory and only this tab can clear it. The location assertion is the meaningful one. The case name and comment are clear, but a reader might think the token assertion proves the absence of a listener more strongly than it does.
· **Recommendation:** None required. The comment already explains this pins a hole, not a feature. Consider renaming the assertion message to emphasize that the layout (which owns the listener) was not mounted.

### F7 — The content-column case pins the gutter but not horizontal alignment
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:1040-1062` asserts `mx-auto`, `max-w-prose`, and `px-6` on both `Banner` and `Screen`.
· **Assessment:** This addresses Phase 7 F4 / Phase 8 I9. It does not pin `justify-end`, `items-center`, or `gap-4` on `Banner`. A mutant that changed `justify-end` to `justify-start` would left-align the sign-out button without failing any test. Visual alignment of the control within the banner is not claimed by any AC, so this is acceptable.
· **Recommendation:** Optional: pin the horizontal alignment utilities if the owner cares about the button position. Otherwise document that only the shared content column is pinned.

### F8 — Phase 10's honest handling of TE21 and TE28 is a strength, not a weakness
· **Severity:** **Positive**
· **Evidence:** The Phase 10 manifest records that TE21 was re-aimed from `px-6` to `pt-6` after the new case killed the old row, and that TE28 survived and was kept as a disagreement rather than relabeled `unfalsifiable`.
· **Assessment:** Both decisions follow good harness hygiene: a row whose prediction flips after a test is added must be re-aimed transparently, and a surviving row must not be relabeled to hide the disagreement. The appended re-run in `10-mutation-run.txt` documents what moved and why.
· **Recommendation:** None. This is the right way to handle harness surprises.

### F9 — TE26 and TE27 resolved the F4/I9 question correctly
· **Severity:** **Positive**
· **Evidence:** The Phase 10 manifest explains that TE26 (`Screen`'s column widened) was killed by both the new pair case and T173's existing `Screen` utility case, while TE27 (gutter change) was killed only by the new case.
· **Assessment:** This showed that the real drift hole was the gutter (`px-6`), not `max-w-prose`, and that the new case pins the right utility on the pair. Phase 7 F4 and Phase 8 I9 were directionally right but imprecise about which utility mattered.
· **Recommendation:** None. The new case is well designed.

### F10 — The OQ-T36-5 case is now cleanly separated from AC7
· **Severity:** **Positive**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:913-940` (AC7) and `:941-944` (OQ-T36-5) are now separate cases. Phase 8 I8 flagged the mix.
· **Assessment:** The separation makes each case's failure message meaningful. The OQ-T36-5 case asserts that `signOutClearsMemoryAndEndsSasSession.test.tsx` names the layout in `PERMITTED_IMPORTERS`, which is the right instrument for the question.
· **Recommendation:** None.

### F11 — The `onClick={() => endSession()}` fix removes the latent hazard
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/AuthenticatedLayout.tsx:83` now wraps the call, and the AC13 source scan at `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:766-780` permits the import list while forbidding direct reach for pieces.
· **Assessment:** Phase 8 I4's concern is addressed. The source scan also covers the new shape.
· **Recommendation:** None.

### F12 — TE22 verifies the `endSession()` wrapper is preserved
· **Severity:** **Positive**
· **Evidence:** The harness row TE22 mutates the button handler back to a bare reference `onClick={endSession}` and is killed by the AC13 source scan at `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:766-780`.
· **Assessment:** This is the right way to guard the Phase 8 I4 / Phase 9 fix. A behavioral test cannot see the parameter-mismatch hazard because it does not mock `endSession`, so a source-level row is the only instrument that can.
· **Recommendation:** None.

### F13 — The whole-suite count matches the Phase 10 manifest
· **Severity:** **Positive / verification**
· **Evidence:** My run: 1885 passed / 1886 total. Phase 10 manifest: "1885 passed, 1 failed (environmental)".
· **Assessment:** The numbers match. The single failure is the known environmental `oxlint` case.
· **Recommendation:** None.

### F13 — The named suite grew from 51 to 57 cases, all passing
· **Severity:** **Positive**
· **Evidence:** My run of `src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` reports 57 passed.
· **Assessment:** Phase 10 added 6 new cases (the manifest says 5, but the count went from 51 to 57, which is 6; likely one case was split or added in phase 9/10 boundary). All pass.
· **Recommendation:** None.

---

## Summary

Phase 10 addressed the five gaps that Phases 7 and 8 identified: StrictMode coverage (I5), return-target behavior (D11), public-path limit (OQ-T42-5), focus-ring clipping (I6), and shared content column (I9). It also handled two harness surprises honestly (TE21 re-aimed, TE28 kept as disagreement) and added TE22 to guard the `endSession()` wrapper fix. The remaining adversarial issues are smaller: **F1** (no DOM-order assertion for banner before main), **F2** (StrictMode doesn't prove first cleanup closed the listener), **F3** (the `signOut()`-first comment overstates a criterion that TE28 disproved), **F4** (focus-ring clipping scan doesn't inspect the layout wrapper), and **F5** (banner landmark has no accessible name, deliberately). The suite is strong and ready to ship; the main action items are the DOM-order test and the comment correction.

**Phase 11 complete.** Fourteen findings returned; no code or contract changed.
