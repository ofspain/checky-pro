<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T173 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | **34 / 34 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1814 / 1815 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T173/artifacts/07-mutation-harness.py` | **16/16 regressions killed, 0 survivors, 0 broken, 0 disagreements** |

No production or test code was changed during this review.

---

## Findings

### F1 — No test asserts that each primitive applies its intended styling classes
· **Severity:** **Major**
· **Evidence:** The named test checks tags, attributes, ref forwarding, and that certain utilities compile into the CSS, but it never checks that `ActionButton` carries `bg-accent`, that `Screen` carries `max-w-prose`, that `TextLink` carries `underline`, etc.
· **Assessment:** A mutant could remove `bg-accent` from `ActionButton.tsx` and no case would fail: the sign-in screen still renders a button, AC9 still finds the `--color-accent` token, AC14 still finds `max-w-prose` and `text-step-1` in the emitted CSS, and the R30 pin only checks that class attributes exist and are identical across account states — not which classes are present. The design system's visual contract is therefore unmeasured at the per-primitive level.
· **Recommendation:** Add one source-scan case per primitive that asserts the expected Tailwind utilities appear in its `className` literal, or add a runtime DOM case that mounts each primitive and asserts its element carries the expected classes. For `ActionButton`, assert `bg-accent`; for `TextLink`, assert `underline`; for `Screen`, assert `max-w-prose`; for `Surface`, assert `min-h-dvh`; etc.

### F2 — The `Only` sweep only scans `app/` and `auth/`
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:123-134` limits `productionFilesUnder` to `'app'` and `'auth'`.
· **Assessment:** Today there are no production `.tsx` files outside those two directories, so the suite passes. But a future screen in `src/profile/` or a new top-level domain would not be checked by the sweep and would not be added to `RENDER_SITES` automatically. The brief's §6e intent is "every screen", not "every screen in app/ and auth/".
· **Recommendation:** Either scan all of `src/` (excluding `api/`, `i18n/`, `pwa/`, and test files) or document in the test header that the sweep is scoped to `app/` and `auth/` and that adding a new source directory requires updating the test.

### F3 — The CSS link assertion does not check `rel="stylesheet"`
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:684-687` checks that `index.html` contains a `<link ... href="... .css">`.
· **Assessment:** The regex `<link\b[^>]*\bhref="([^"]+\.css)"` does not require `rel="stylesheet"`. A malformed link such as `<link href="/app/assets/foo.css">` (without rel) would satisfy the assertion but the browser would not load it as a stylesheet.
· **Recommendation:** Strengthen the assertion to require `rel="stylesheet"` on the matched `<link>`.

### F4 — Interactive primitives' focus-indicator classes are not asserted
· **Severity:** **Minor/Major**
· **Evidence:** `ActionButton.tsx`, `TextLink.tsx`, and `FocusHolder.tsx` all include `focus-visible:outline-2 focus-visible:outline-focus`.
· **Assessment:** A mutant that removed these classes would leave keyboard users without a visible focus indicator on the application's only control and only link. No test currently checks for their presence. This is an L11/WCAG 2.4.7 regression that the suite would miss.
· **Recommendation:** Add the focus classes to the per-primitive class assertion suggested in F1.

### F5 — The callback screen's literal English strings are not asserted
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:503-523` asserts literal English for `SignedOutScreen` and `SignInScreen`, but not `CallbackScreen`.
· **Assessment:** `CallbackScreen` renders `callback.heading` and `callback.working`. The catalogue-key set is pinned, but a missing value or key leak is not verified for this screen. The exchange is stubbed in the test file (`vi.mock`), so mounting `CallbackScreen` is straightforward.
· **Recommendation:** Add a case that mounts `CallbackScreen` and asserts its text contains `en.callback.heading` and `en.callback.working` and no `callback.` keys leak.

### F6 — The AC4 scan checks prop types for `style` but not hardcoded `style=` in JSX bodies
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:322-329` checks `propsType` for `className`/`style`. There is no scan of the JSX body for inline `style=` attributes.
· **Assessment:** A primitive could hardcode `<div style={{ color: 'red' }}>` without accepting `style` as a prop. The prop-type scan would pass. The R30 pin checks rendered output for style attributes, but only on failure screens. Other screens or primitives could carry inline styles undetected.
· **Recommendation:** Add a source scan that forbids `style=` in each primitive's JSX body (excluding tests). This is symmetric to the `className` newline check.

### F7 — The `SignInScreen` dynamic `aria-disabled`/`aria-busy` change is not tested by this suite
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:386-407` checks the initial idle state only.
· **Assessment:** The suite verifies the button starts with `aria-disabled="false"` and `aria-busy="false"`. It does not click the button to verify they flip to `"true"` while starting. Other suites may cover this, but T173's own AC5 case is incomplete.
· **Recommendation:** Add a case that clicks the sign-in button and asserts `aria-disabled="true"` and `aria-busy="true"` while the navigation is pending (the exchange is already mocked, so the click can be fired).

### F8 — The `RateLimitNotice` focus move itself is not directly tested here
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:489-495` names the existing case in `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`.
· **Assessment:** The existing case is strong evidence and is correctly cited. But if that case were ever removed or renamed, T173's AC6 would have no behavioural instrument of its own. The citation-rot guard catches the rename.
· **Recommendation:** Keep the citation case, but consider adding a lightweight direct case that shows the notice, clicks dismiss, and asserts `document.activeElement` is the holder. This would make AC6 self-contained.

### F9 — The harness still carries two known instrumentation bugs
· **Severity:** **Minor**
· **Evidence:** `artifacts/07-mutation-harness.py:269` has the `.tsx` → `.ts` truncation regex; `91-95` excludes the whole `lintRejectsHandWrittenBackendFetch.test.ts` file as environmental.
· **Assessment:** Phase 10 did not fix these; the artifact says they belong to the eleventh copy. They do not affect the current verdict (16/16 killed, no disagreements), but they corrupt attribution and hide real kills in the environmental file. They should be fixed when the harness is next copied.
· **Recommendation:** Fix in the next task that copies the harness: regex `(\S+\.tsx?)\b`; environmental set keyed by case name.

### F10 — `ActionButton` does not have a pressed/active state asserted
· **Severity:** **Minor**
· **Evidence:** `ActionButton.tsx` includes no `active:` or `pressed:` Tailwind variant.
· **Assessment:** The brief did not require hover/active states, so this is not a defect. It is worth recording because the first screen that needs visual feedback on press is the trigger for adding one.
· **Recommendation:** None.

### F11 — The `Only` sweep could be fooled by a string literal containing a lowercase tag
· **Severity:** **Minor**
· **Evidence:** `lowercaseJsxTags` strips comments but not string literals; it matches `<` when not preceded by an identifier character.
· **Assessment:** A production file under `app/` or `auth/` that contains the string `"<div>"` would be reported as rendering a bare element. No current file does this, and the controls prove the scan catches real tags. But the scan is source-text based, not AST based.
· **Recommendation:** Optional: add a control that proves a string literal is not counted, or document the limitation. A cheaper fix is to note it in the test header.

### F12 — `CallbackScreen` is not covered by the "no side effect on render" case
· **Severity:** **Minor**
· **Evidence:** AC10 only checks `SignedOutScreen`.
· **Assessment:** `CallbackScreen` runs an effect that may read storage and network; it cannot be included unchanged. But the initial render of `SignInScreen` and `RateLimitNotice` could be added: neither writes storage or fetches on first render.
· **Recommendation:** Extend the AC10 case to assert no storage write / no fetch on initial render of `SignInScreen` and `RateLimitNotice` as well as `SignedOutScreen`.

### F13 — The catalogue-key test pins 14 keys but not their order or grouping
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:525-546` sorts and compares keys.
· **Assessment:** The test is correct as far as it goes. It would not catch a key moving from one group to another with the same flat path, but that is a structural change no other test guards either.
· **Recommendation:** None.

### F14 — The AC9 test is a strong implementation of "computed, not asserted"
· **Severity:** **Minor** (positive finding)
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:612-650` recomputes ratios and prints them in assertion messages; it also checks the comments state the computed figures.
· **Assessment:** This closes phase 7's F1 completely. A wrong comment will now fail.
· **Recommendation:** None.

### F15 — The disarming of the "thirteen" trap is well done
· **Severity:** **Minor** (positive finding)
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:192-224` distinguishes the union of pinned lists (13) from the derived tag-bearing set (7), and names `TextLink` as the delegating exception.
· **Assessment:** This is the right resolution of phase 7/8's concern. The three assertions keep both halves of §6e(2) honest.
· **Recommendation:** None.

---

## Summary

T173's Phase 10 is a strong suite: 34 cases, 16/16 mutants killed, no survivors, no broken mutants, no disagreements. The most important remaining gap is **F1**: the design system's visual contract is not asserted per primitive, so a primitive could lose its styling classes and the suite would still pass. **F2** (sweep scoped to `app/`/`auth/`), **F3** (CSS link `rel` not checked), **F4** (focus-indicator classes not asserted), and **F6** (hardcoded `style=` in JSX bodies not scanned) are smaller but real. **F7** and **F8** are optional tightenings. The suite and gates are independently reproducible, and the one whole-suite failure is the known environmental `oxlint` host case.

**Phase 11 complete.** Fifteen findings returned; no code or contract changed.
