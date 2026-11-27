<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# frontend · T47 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | **23 / 23 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2016 / 2016 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T47/artifacts/07-mutation-harness.py` | **16/16 regressions killed**, 2 canaries killed, 1 expected survivor (TR15), **0 broken**, **1 disagreement** (TR14 — stronger than claimed) |

## Phase 10 changes consumed

Phase 10 added five cases and two harness rows:

1. `renders IDENTICALLY on a cold arrival and at the end of a submit` — closes the strongest remaining form of "the screen has no inputs".
2. `is identical for a reply that is not JSON at all` — catches a client that parses the required `message` field.
3. `renders exactly one heading, so the document outline has no gap` — enforces `Heading`'s no-`level`-prop design.
4. `OPEN QUESTION PINNED - the acknowledgement offers nowhere to go` — turns the deferred OQ-T47-4 into an assertion.
5. `OPEN QUESTION PINNED - a double submit sends TWO requests` — pins OQ-T43-11's current behaviour.
6. Harness row **TR18** — `RegisterSentScreen` grows a `TextLink`; killed by the OQ-T47-4 pin.
7. Harness row **TR19** — a second `<h1>` appears; killed by the one-heading case.

Phase 10 also fixed the `type()` helper to use the HTMLInputElement prototype setter and added a body assertion in AC4 so the helper cannot be silently inert.

## Findings

### F1 — Phase 8's F1 (`replace: true` unmeasured) is now closed
· **Severity:** **Positive**
· **Evidence:** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx:232-252` asserts both `router.state.historyAction === 'REPLACE'` and that `router.navigate(-1)` does not return the form to the DOM.
· **Assessment:** The case measures the flag and the consequence. Harness row TR10, which previously survived, now has a dedicated behavioural kill.
· **Recommendation:** None.

### F2 — Phase 8's F2 (probe replies not asserted to differ) is now closed
· **Severity:** **Positive**
· **Evidence:** Lines 197-198 assert `ALREADY_REGISTERED.body !== NEW_ADDRESS.body` inside the AC2 case.
· **Assessment:** The trap is now guarded by an instrument, not only a comment. Harness row TR16, the canary for this, is now expected to be killed.
· **Recommendation:** None.

### F3 — The OQ-T47-4 "nowhere to go" pin is slightly narrow
· **Severity:** **Minor**
· **Evidence:** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx:331-336` checks for `a, button, input, select, textarea` and for `[tabindex]` nodes.
· **Assessment:** A control implemented as a non-semantic element with `role="button"`, `role="link"`, or `contenteditable` would not be caught by this list. The screen is built from primitives that do not introduce such elements, and `TextLink` renders an `<a>`, so the current selectors catch the likely mutants. But the assertion's name is "nowhere to go", and a focusable `div` with a role would be somewhere to go.
· **Recommendation:** Optional: expand the selector to include `[role="button"]`, `[role="link"]`, and `[contenteditable]` to match the assertion's wording. Not urgent because the primitive set makes the current shape sufficient.

### F4 — The AC4 source scan still does not forbid `useNavigate`
· **Severity:** **Minor**
· **Evidence:** The forbidden list at lines 381-382 includes `useLocation`, `useSearchParams`, and `useParams`, but not `useNavigate` or `useHref`.
· **Assessment:** `RegisterSentScreen` should not navigate, and it does not. The source scan is the right place to hold that property for future edits. A mutant that added an internal link via `useNavigate` would not be caught by the current scan (though it would likely be caught by the OQ-T47-4 pin or the no-lowercase-tag assertion).
· **Recommendation:** Optional: add `useNavigate` and `useHref` to the forbidden list, with the same rationale as the other router hooks.

### F5 — The AC9 storage case is still coupled to a react-router internal key name
· **Severity:** **Minor**
· **Evidence:** Line 494 filters out `key !== 'remix-router-transitions'`.
· **Assessment:** This was Phase 8's F6 and is unchanged. The failure mode is loud (the case fails if react-router renames the key), so the coupling is acceptable but should be read as a dependency on a private name.
· **Recommendation:** None required beyond the existing comment explaining why the exception exists.

### F6 — Phase 8's F3 and F4 (scope/blast radius) remain unaddressed
· **Severity:** **High (process)**
· **Evidence:** The T45 suite and multiple route-test files were modified in earlier phases. Phase 10 changed only test files, so the process findings about closed-task files are still open.
· **Assessment:** Phase 10 correctly did not touch production or closed-task files. The ratification/reversal of F3/F4 is still a Phase 9/owner decision, not a test-generation task.
· **Recommendation:** Phase 12 or the owner should resolve whether the T45-suite changes are ratified or reverted.

### F7 — The double-submit case simulates a specific, documented timing
· **Severity:** **Observation**
· **Evidence:** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx:354-357` clicks the submit control twice inside a single `act`.
· **Assessment:** The case is explicit that it pins today's behaviour and that OQ-T43-11 is the owner's call. The synchronous double-click is the right shape for "two presses before the first reply", but it is not a realistic inter-click delay. That is fine because the assertion documents the behaviour, not a desired UX.
· **Recommendation:** None.

### F8 — The `innerHTML` comparisons remain whitespace/attribute-brittle
· **Severity:** **Minor**
· **Evidence:** Phase 10's two new identical-output cases (lines 288-300 and 307-311) use `innerHTML` and `textContent` equality.
· **Assessment:** Same trade-off as Phase 8's F8. The equality is the only way to assert "and nothing else" and "same layout", but it couples the tests to React's rendered attribute order and any future wrapper markup.
· **Recommendation:** None; keep the equality and accept the brittleness as the cost of the stronger claim.

### F9 — The `type()` helper and body assertion together close the silently-inert hole
· **Severity:** **Positive**
· **Evidence:** Lines 139-145 use the prototype setter, and lines 407-409 assert the sent body equals what was typed.
· **Assessment:** This is a strong fix. The descriptor assertion catches a missing setter; the body assertion catches a setter that is never called. Either failure would now surface in this suite rather than in a sibling suite.
· **Recommendation:** None.

### F10 — The harness is 16/16 with TR16 killed and TR18/TR19 added
· **Severity:** **Positive**
· **Evidence:** The independent harness run reports `regressions: 16/16 killed`, `canaries: ['TR16', 'TR17'] - none survived`, survivors `['TR15']`, 0 broken, 1 disagreement (TR14).
· **Assessment:** Phase 10 successfully closed the two surviving canaries from Phase 7/8 (TR10 and TR16) and added rows (TR18, TR19) that isolate the new pins. TR15 remains the only expected survivor because `useEffect` → `useLayoutEffect` has no observable effect on focus timing in this test environment.
· **Recommendation:** None.

---

## Summary

Phase 10 closed the two real gaps identified in Phase 8: `replace: true` is now asserted behaviourally (F1), and the probe replies are asserted to differ (F2). The test helper was also hardened so it cannot type nothing while passing every case.

The remaining Phase 11 concerns are small: the OQ-T47-4 pin could be widened (F3), the AC4 source scan could forbid `useNavigate` (F4), and the AC9 storage case still depends on a react-router internal key (F5). The process findings from Phase 8 (F6) remain for the owner to resolve.

Final verification is clean: **23/23** in the named suite, **2016/2016** in the whole suite, and **16/16 regressions killed** in the harness with no broken rows.

**Phase 11 complete.** No code or contract changed.
