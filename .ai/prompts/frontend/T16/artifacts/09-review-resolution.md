# frontend · T16 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Thirteen findings — seven from the self-review, six from the independent review. **All thirteen applied.**

| # | Finding | Resolution |
|---|---|---|
| S1 | `expect(JSDOM_ORIGIN).toBe('http://localhost:3000')` is a dead assertion | **Applied.** Replaced with `location.origin` and a check that the path is outside `APP_BASENAME` — the reason the out-of-basename shell is empty, rather than a constant compared with its own literal. |
| S2 | the console spy may be weaker than it looks | **Applied** as a comment: the spy is a sample over enumerable function keys, the source scan is authoritative. |
| S3 | the decoy couples to the selector list's order and membership | **Applied.** Containment, not equality. The meaningful equality is the empty one, on the real screen. |
| S4 | nothing proves exactly one interactive control | **Applied.** `button, a, input, select, textarea, [tabindex]` must have length 1 and be the action. Mutant M12 proves it. |
| S5 | the retry case tests the rendered shadow, not the ref | **Applied, and it was the sharpest of the thirteen.** `aria-disabled` returning to `false` is the consequence of `setStatus`, not of the ref resetting. A regression resetting one but not the other leaves a button that looks enabled and silently does nothing. The retry is now performed with the mock flipped to succeed. Mutant M9 proves it. |
| S6 | `aria-disabled` compared as a string is correct but undocumented | **Applied** as a comment. |
| S7 | the in-flight announcement is never observed mid-flight in a browser | **Applied.** The case is renamed to say it checks the state is *rendered*, with the real-browser measurement in the comment. |
| I1 | T16 closes a circular import, benign only by accident | **Applied.** Two cases pin the property the cycle depends on; `routes.tsx` names the cycle at the import that closes it. Mutant M15 confirms the failure is an empty root in a real browser. |
| I2 | the AC14 case creates a router it cannot dispose | **Applied as a recorded trade-off.** `mountShell` returns the `Root`, not the router. Rebuilding its tree by hand would stop the case testing the real entry point, which is the only reason it exists. |
| I3 | the explanation is not associated with the action | **Applied.** `aria-describedby`, with a case. Mutant M10 proves it. |
| I4 | `aria-disabled` gives a mouse user no feedback for a swallowed click | **Applied.** `aria-busy` added alongside; the live region is the feedback. |
| I5 | duplicate of S4 | Applied with S4. |
| I6 | the `starting` state is unreachable in a real browser | Applied with S7. |

Phase 10 then found two more gaps by mutation (M8, M20) and one defect in a guard I had just added — see
`artifacts/10-test-generation.md`.
