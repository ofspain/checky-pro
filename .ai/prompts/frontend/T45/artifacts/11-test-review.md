<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T45 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md`, `artifacts/10-mutation-run.txt` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | **19 / 19 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1964 / 1965 passed** — 1 known environmental `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T45/artifacts/07-mutation-harness.py` | **44/44 regressions killed**, 3 canaries killed, **1 expected survivor** (TR24), **0 broken**, **0 disagreements** |

The named suite count, whole-suite count, and harness outcome all match the Phase 10 manifest exactly. TR24 survived as expected; no FU-T42-1 flake occurred in this run.

---

## Findings

### F1 — `describedElement` and the AC4 case still resolve ids against the document, not the container
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:123-126` uses `container.ownerDocument.getElementById(id)`; `:308` uses the same pattern in the AC4 case.
· **Assessment:** Phase 10 closed most Phase 8 issues but not this one. With one mount per case and an `afterEach` that removes the container, the hazard is latent. The first case that mounts the screen twice or leaks a container could resolve `register-password-guidance` to the wrong element. The first half of the helper (reading `aria-describedby` off the field) is correctly scoped; the second half should match.
· **Recommendation:** Replace both occurrences with `container.querySelector('#' + CSS.escape(id))`. This keeps the lookup scoped to the rendered container without changing the test's semantics.

### F2 — The provenance source-scan regex is sensitive to whitespace
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:467-470` uses `/min:\s*ADVISORY_MINIMUM_CODE_POINTS\b/` and `/max:\s*ADVISORY_MAXIMUM_CODE_POINTS\b/`.
· **Assessment:** The regexes correctly pin the screen to the constants, closing Phase 8 I1. They allow any amount of whitespace, which is good. They would fail if a future edit removed the space and wrote `min:ADVISORY_MINIMUM_CODE_POINTS`, but TypeScript/JSX object syntax almost always includes a space. The risk is small.
· **Recommendation:** Optional: the regex is fine as is, but a comment noting that it pins the exact token names would make the intent clearer to a future maintainer.

### F3 — Phase 10 closed the screen-literals provenance hole
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:460-470` now asserts the screen source contains `min: ADVISORY_MINIMUM_CODE_POINTS` and `max: ADVISORY_MAXIMUM_CODE_POINTS`.
· **Assessment:** This closes Phase 8 I1. A screen interpolating literals `12` and `128` will now fail the source scan while still passing every DOM assertion.
· **Recommendation:** None.

### F4 — Phase 10 corrected the bounds-case comment and clarified which assertion kills which mutant
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:433-444` now explains that the `{{min}}`/`{{max}}` pin kills catalogue literals, the digit identity kills swapped/renamed bounds, and the source scan kills screen-side literals.
· **Assessment:** Honest and accurate documentation. The comment no longer overstates the digit-identity assertion.
· **Recommendation:** None.

### F5 — Phase 10 added TR45 (swapped bounds), closing the missing-mutant gap
· **Severity:** **Positive**
· **Evidence:** `.ai/prompts/frontend/T45/artifacts/07-mutation-harness.py:506-512` defines TR45, and the digit-identity assertion at `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:445-446` catches it.
· **Assessment:** Closes Phase 8 I3. The assertion is now positively proved rather than merely passing.
· **Recommendation:** None.

### F6 — Phase 10 asserted the reading-order claim with `compareDocumentPosition`
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:321-332` now asserts the guidance element follows the password input and precedes the submit control.
· **Assessment:** Closes Phase 8 I4. The screen's comment is now held by a real test.
· **Recommendation:** None.

### F7 — Phase 10 centralized `VERDICT_WORDS` and made the control read it
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:423` defines `VERDICT_WORDS`; `:488-491` and `:503-504` both read it.
· **Assessment:** Closes Phase 8 I6. The scan and its control are now tied to the same list and cannot drift silently.
· **Recommendation:** None.

### F8 — Phase 10 added the 11-astral row and the table control, closing the counting-bug silence at the lower end
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:200-207` adds the 11-astral row; `:239-257` adds a control asserting the table has a row whose code points are below the minimum while its UTF-16 length is at or above it.
· **Assessment:** This is the central measurement Phase 10 was built around. The 129-astral row does not discriminate the counting bug because 258 units is also out of range; the 11-astral row does, because 22 units is inside. The control makes the table's discriminating power explicit.
· **Recommendation:** None.

### F9 — Phase 10 added the persistence case, pinning the state machine's other edge
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:383-409` keeps the guidance up while the visitor types an in-range password, then asserts it drops on the next submit.
· **Assessment:** Closes the gap where `onChange` resetting `advisory` would have passed 18 of 19 cases. The case documents OQ-T45-5 rather than deciding it.
· **Recommendation:** None.

### F10 — Phase 10 added TR46-TR48 for the new cases
· **Severity:** **Positive**
· **Evidence:** `.ai/prompts/frontend/T45/artifacts/07-mutation-harness.py:514-540` defines TR46 (reading order), TR47 (lower comparison counts units), and TR48 (advisory resets on keystroke).
· **Assessment:** Every new behavioral case has a corresponding mutant, which is the package's standard for a strong suite.
· **Recommendation:** None.

### F11 — This harness run was clean, matching Phase 10's report
· **Severity:** **Positive**
· **Evidence:** This run: **44/44 regressions killed**, 0 broken, 0 disagreements, TR24 survived as expected. Phase 10 reported the same. Phase 9 had seen TR24 falsely promoted to DISAGREES via inherited kills in `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`.
· **Assessment:** Two consecutive clean runs (Phase 10 and this run) is strong evidence that the suite is stable under current conditions. The FU-T42-1 flake history means a single run would have been insufficient, but the consistency across runs is reassuring.
· **Recommendation:** None required for this task. For future tasks, continue reporting multiple harness runs when the machinery has shown flakiness before.

### F12 — No test asserts the guidance element is rendered through `Text`
· **Severity:** **Minor**
· **Evidence:** The AC4 cases walk from `aria-describedby` to the element and assert its text content. They do not assert the element's tag name is `P` or that it is the `Text` primitive.
· **Assessment:** The brief requires the screen to render through `src/app/ui` primitives and not bare elements. The design-system suite's "no lowercase JSX tag in render sites" assertion already enforces that the screen renders no bare `<p>`. The guidance must therefore be a `Text` primitive. This is indirectly covered.
· **Recommendation:** None required; the layer pin is sufficient.

### F13 — The AC7 literal pin is the only non-derived copy claim
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:497` asserts `GUIDANCE` equals `'Use a password of 12 to 128 characters.'`, and the comment at `:492-496` explains why it must stay non-derived.
· **Assessment:** Good documentation of the copy contract. Every other assertion derives from `GUIDANCE`, so this line is the one place arbitrary copy changes are caught.
· **Recommendation:** None.

---

## Summary

Phase 10 closed almost every Phase 8 issue: the screen-literals provenance hole (**F3/I1**), the misleading bounds-case comment (**F4/I2**), the missing swapped-bounds mutant (**F5/I3**), the unmeasured reading-order claim (**F6/I4**), the duplicated verdict-word list (**F7/I6**), and the counting-bug silence at the lower end (**F8**). It also added the state-machine persistence case (**F9**) and its corresponding mutants (**F10**).

Remaining concerns are small: the id lookup is still document-scoped (**F1/I5**) and the provenance regex is slightly whitespace-sensitive (**F2**). The harness ran cleanly in this phase (**F11**), matching Phase 10. The layer already ensures the guidance is rendered through a primitive, not a bare element (**F12**), and the copy contract is well documented (**F13**).

**Phase 11 complete.** Thirteen findings returned; no code changed.
