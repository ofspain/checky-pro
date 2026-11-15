<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review). -->

# frontend · T45 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Consumes** | `artifacts/06-implementation-notes.md`, `artifacts/07-mutation-harness.py` |
| **Produces** | `artifacts/07-self-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run …registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | **16 / 16 passed** |
| `vitest run` | **1961 / 1962** — the one known environmental failure, `lintRejectsHandWrittenBackendFetch.test.ts` |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| `python3 -I .ai/prompts/frontend/T45/artifacts/07-mutation-harness.py` | **39/39 regressions killed**, 3 canaries killed, **1 expected survivor** (TR24, unfalsifiable), **0 broken**, **0 disagreements** · `artifacts/07-mutation-run.txt` |

The harness ran with the local Docker stack **stopped** (FU-T42-1 is load-sensitive): baseline 1 failed of 1962,
and that one case name is excluded from every attribution.

Two **probe mutants** were applied by hand for this review and reverted immediately; neither is in the harness
yet, and F1/F3 are measurements rather than arguments. The working tree is clean.

---

## Findings

### F1 — The provenance hole the task was built around is still open: a screen that interpolates literals survives everything
· **Severity:** **Major**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:81-84` interpolates
  `ADVISORY_MINIMUM_CODE_POINTS`/`ADVISORY_MAXIMUM_CODE_POINTS`. I replaced those two arguments with the
  literals `12` and `128` — the catalogue template untouched — and ran the three files this task owns and grew:
  **126 / 126 passed.** The mutant survives. The source-scan case
  (`…registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:360-377`) asks only that the
  screen contains `'register.passwordGuidance'`; the bounds case (`:339-358`) asks that the *catalogue* holds
  `{{min}}`/`{{max}}` and that the rendered digits equal the constants — which literals satisfy by arithmetic.
· **Assessment:** TR41 moves the literals into the catalogue, and the suite catches that. TR41's own
  description says the thing that separates the two is **provenance**, and the freeze says the same
  ("only an assertion tying the rendered numbers to the constants can kill it"). One step further along the
  same wire — literals in the *screen* instead of the catalogue — is byte-identical on screen **and** invisible
  to every assertion in the package. The drift R1's bound is protected from is therefore protected at the
  catalogue end only. This is the same shape as T37's `/accessToken\s*\(/`: a pattern aimed one element away
  from the hazard it was written for.
· **Recommendation:** Phase 9 — add a harness row (TR44) that interpolates literals in the screen, and close it
  in the source-scan case by requiring the lookup to *name* the two constants
  (`expect(code).toMatch(/min:\s*ADVISORY_MINIMUM_CODE_POINTS/)` and the `max` counterpart). The source is the
  only instrument that can see this, exactly as it is for TR40.

### F2 — The bounds case credits the wrong half of itself
· **Severity:** **Minor/Major**
· **Evidence:** `…registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:346-352` says
  the digit-identity assertion is "THE ONLY ASSERTION THAT CAN KILL THE LITERAL-BOUNDS MUTANT (freeze, §3)".
  TR41's replacement renders `12` and `128`, so `sentence.match(/\d+/g)` still equals
  `[String(ADVISORY_MINIMUM_CODE_POINTS), String(ADVISORY_MAXIMUM_CODE_POINTS)]` and that assertion passes.
  What actually kills TR41 is `:353-356`, `expect(en.register.passwordGuidance).toContain('{{min}}')`.
· **Assessment:** The case is right and the comment is wrong, which is the failure mode phase 12 caught twice
  in T42: a comment naming a criterion the code next to it does not hold. Both assertions earn their place —
  see F3 — but a reader who trusts this comment will believe the rendered numbers are tied to the constants,
  and F1 is the price of believing it.
· **Recommendation:** Phase 9 — rewrite the comment so it says which assertion does which: the `{{min}}` pin
  is what sees literals in the catalogue, the digit identity is what sees the bounds **swapped or renamed**, and
  neither sees literals in the screen until F1 is closed.

### F3 — No harness row aims at the digit-identity assertion (it is falsifiable; nothing falsifies it)
· **Severity:** **Minor**
· **Evidence:** No row in `07-mutation-harness.py` changes which variables the template names. I replaced the
  template with `'Use a password of {{max}} to {{min}} characters.'` and the named suite went to
  **2 failed of 16** — the bounds case and `delivers guidance, not a verdict (D-T45-2)`.
· **Assessment:** The assertion is awake, so this is not the storage-spy shape; it is an assertion with no
  mutant, which this package has twice decided is not good enough (T44 I2, T43 phase 10).
· **Recommendation:** Phase 9 — add the swapped-bounds row. It costs one tuple and makes `:351-352` positively
  proved rather than merely passing.

### F4 — The whole copy contract rests on one literal line, and that is deliberate
· **Severity:** **Positive / observation**
· **Evidence:** Every equality in the suite compares against `GUIDANCE` (`:129-131`), which is derived from
  `en.register.passwordGuidance` and therefore **moves with any copy change**. TR42 (the copy becomes a verdict)
  died in exactly two cases — the bounds case and `delivers guidance, not a verdict` — and in no equality case,
  precisely as the row predicted.
· **Assessment:** This is the right design: deriving the expected sentence keeps the suite from re-spelling the
  catalogue, and the words themselves are pinned once, at `:387`. But it means the single line
  `expect(GUIDANCE).toBe('Use a password of 12 to 128 characters.')` is the only thing standing between this
  screen and arbitrary copy. A later task that "simplifies" it into a catalogue read would delete the pin and
  nothing would fail.
· **Recommendation:** Phase 9 — one sentence in the comment above `:387` saying that this assertion is the only
  non-derived copy claim in the file and must stay non-derived. No code change.

### F5 — The verdict word list exists twice and nothing ties the two copies together
· **Severity:** **Minor**
· **Evidence:** `:383` lists `['short', 'long', 'invalid', 'wrong', 'error', 'failed']` in the scan;
  `:394` lists the same six words again inside the control.
· **Assessment:** The control claims to prove "the scan" fires on a verdict and not on `belongs`. It proves
  that about **its own copy** of the list. A word added to the scan is not controlled, and a word added to the
  control is not enforced; the two can drift silently in either direction. This is a control measuring
  something adjacent to its claim — T37's lesson, at smaller scale.
· **Recommendation:** Phase 9 — hoist one `const VERDICT_WORDS` and have both the scan and the control read it.

### F6 — `describedElement` resolves the id against the document, not the container
· **Severity:** **Minor**
· **Evidence:** `:123-126` — `container.ownerDocument.getElementById(id)`. `GUIDANCE_ID` is a module constant
  (`RegisterScreen.tsx:59`), not a `useId()` value.
· **Assessment:** With one mount per case and an `afterEach` that removes the container, every case today
  resolves to its own element. The hazard is latent rather than live: the first case that mounts the screen
  twice, or that leaks a container, would resolve `register-password-guidance` to whichever copy comes first in
  document order and could assert against the wrong screen. The comment at `:116-122` justifies walking from
  the field, which is right; it is the second half of the walk that leaves the container.
· **Recommendation:** Phase 9 — `container.querySelector('#' + CSS.escape(id))`, keeping the walk and scoping
  the lookup. Low cost, and it removes the only way this helper can lie.

### F7 — The screen claims a reading-order criterion that no case measures
· **Severity:** **Major**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:98-102` — "Between the field and the control
  so the reading order matches the visual one." No case in the package asserts the guidance element's position
  relative to the password input or the submit control; `grep` finds no `compareDocumentPosition` and no
  child-order assertion in either account suite.
· **Assessment:** A comment stating a criterion nothing holds is the defect T42's phase 12 found twice and that
  two reviews walked past. It is also cheap to be wrong about: TR37 renders the element unconditionally and
  TR36 removes it, and both would behave identically if it were moved after the submit control — so a future
  restyling can break the stated criterion with the whole suite green.
· **Recommendation:** Phase 9 — either assert it (the described element precedes the submit control and follows
  the password input, via `compareDocumentPosition`) or delete the clause. Asserting it is the better of the
  two: it is one line, and the claim is a real accessibility criterion rather than decoration.

### F8 — `GUIDANCE` is built with a non-global `replace` and one exact spelling of the placeholders
· **Severity:** **Minor**
· **Evidence:** `:129-131` — `.replace('{{min}}', …).replace('{{max}}', …)`. i18next also accepts
  `{{ min }}` with inner whitespace, and `String.replace` with a string pattern substitutes the first match only.
· **Assessment:** Both failure modes are loud, not silent: a whitespace variant or a twice-named bound would
  leave braces in `GUIDANCE` and every equality in the file would fail. The bounds case does pin the exact
  spelling (`:353-356`), so the coupling is enforced — just in a different case from the one that depends on it.
· **Recommendation:** None required. If phase 9 touches the block anyway, one line in the comment pointing at
  the bounds case as the thing that keeps this spelling honest.

### F9 — The two deviations are recorded honestly, and one of them falsified the freeze within a minute
· **Severity:** **Positive**
· **Evidence:** `06-implementation-notes.md` D-1 and D-2;
  `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:923-932` carries the key with a
  comment saying plainly that phase 3's F9 was wrong and why.
· **Assessment:** F9 reasoned about element counts, `RENDER_SITES` and prop-type pins and concluded the file
  needed no change; the freeze accepted it; the catalogue key-set pin failed on the first run. Recording it in
  the file the next word-adding task will touch is worth more than recording it only here. D-2 is the same
  shape: the first draft of the copy scan flagged `password`, a legitimate identifier, and the fix was to assert
  the **sentence** rather than its words — the shape T43 had already settled on.
· **Recommendation:** None.

### F10 — FU-T43-2, third sighting: ~120 lines of helpers copied again
· **Severity:** **Minor**
· **Evidence:** `…registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx:51-148` —
  `installBrowserRequest`, `installTransport`, `settle`, `renderScreen`, the three element getters, `type` and
  `fillAndSubmit`, all copied from T44's suite. The header comment at `:26-28` says so.
· **Assessment:** The freeze (F7) chose duplication over extraction, which is right for a task with no mandate
  to add a module. But this is now the third task to pay the same cost, and the copies have begun to diverge:
  this one adds `describedElement`, T44's does not.
· **Recommendation:** Owner decision, not phase 9. A `features/account/test-helpers.tsx` is a task of its own.

### F11 — AC4's "one id, not a list" and the email field's exemption are asserted once each, with no mutant
· **Severity:** **Minor**
· **Evidence:** `:276` (`expect(id).not.toContain(' ')`), `:279-280` (the email field is neither described nor
  marked). No harness row accumulates ids or describes the email field.
· **Assessment:** Both are cheap, correct, and currently unfalsified — the same class as F3 but lower value,
  since neither failure mode is reachable from the screen as written (one condition, one element, one id).
· **Recommendation:** Optional in phase 9. If a row is added, the id-accumulation one is the better of the two:
  it is the shape a "remember the last few messages" change would take.

---

## Summary

The implementation does what the frozen brief asked, and the harness says so: **39/39 regressions killed, 0
broken, 0 disagreements**, with the one expected unfalsifiable survivor. The rows aimed at this task behaved as
predicted, including the two byte-identical ones — TR40 died only in the source scans, TR41 died in **exactly
one case**, and TR42 died in exactly the two cases that do not derive their expectation from the catalogue.

The important finding is that TR41 died for a different reason than the freeze predicted (**F2**), and that the
reason the freeze named — provenance — is **not actually enforced**: a screen that interpolates the literals
`12` and `128` passes all 126 cases in the three files this task owns (**F1**, measured). That is phase 9's
first job, with the swapped-bounds row (**F3**) and the unmeasured reading-order claim in the screen's own
comment (**F7**) next. The rest are small: a duplicated word list in a control (**F5**), a document-wide id
lookup in an otherwise careful helper (**F6**), and two unfalsified cheap assertions (**F11**).

**Phase 7 complete.** Eleven findings returned; no code changed — the two probe mutants were reverted and the
tree is clean.
