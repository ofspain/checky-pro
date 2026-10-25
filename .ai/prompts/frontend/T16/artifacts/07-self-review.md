# frontend · T16 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | Claude Opus 5 |
| **Consumes** | the phase 6 implementation |
| **Produces** | `artifacts/07-self-review.md` |

---

## Verified in a real browser, against the running platform

This is the first task that could be, so it was. The whole stack was already up (eleven containers, eighteen
hours), all four services ready. Headless Chrome was driven over the DevTools protocol: open `/app/sign-in`,
click the button, record where the browser ends up.

**Rendered DOM, from Chrome, not jsdom:**

```html
<div id="root"><div data-testid="app-shell"><main>
  <h1>Sign in</h1>
  <p>Continue to the secure sign-in page to enter your details.</p>
  <button type="button" aria-disabled="false">Continue to sign in</button>
  <div role="status" aria-live="polite"></div>
</main></div></div>
```

**Before the click:** `location.href` is `/app/sign-in`, and `Object.keys(sessionStorage)` is **`[]`**. AC3 holds
outside the test harness.

**After the click:**

| | |
|---|---|
| Navigation chain | `/oauth2/authorize?response_type=code&client_id=checky-spa&redirect_uri=…%2Fapp%2Fcallback&scope=openid+profile+email&state=…` → `/login` |
| Final URL | `http://localhost:5173/login` |
| Final page title | **`Please sign in`** |
| `document.querySelector('input[type=password]')` there | **present** |
| `sessionStorage` keys | `themistra.auth.code_verifier`, `themistra.auth.nonce`, `themistra.auth.state` |
| Stored verifier length | 43 |

**That last block is R6 demonstrated rather than asserted.** There is a password field in this flow — it is on
the Spring Authorization Server's own page, on the server, reached by a full-page navigation. The SPA has none.
L2's "interactive login is SAS-hosted" is no longer a design claim; it is an observed fact with a screenshot
behind it.

It also incidentally confirms things later tasks own: the three L16 items survive a real full-page redirect
(task 23's property), and the edge routes `/oauth2/*` and `/login` on the same origin as the SPA (L9).

---

## Suite

| | |
|---|---|
| New file | 25 cases, stable over three consecutive runs |
| Whole suite | **554 pass / 556 total**, exactly +25 |
| The 2 failures | `lintRejectsHandWrittenBackendFetch`, both environmental and both pre-existing: AC4 because this host's `oxlint@1.87.0` prints no summary line, AC2 because bare `pnpm` is not on PATH |
| `tsc --noEmit` | clean |
| `oxlint` | clean |

---

## What I got wrong, and fixed

### 1. The test harness raced the navigation — and leaked into the next case

The first run failed five cases with results that **changed between runs**: the same spy reported 0 calls once and
2 calls another time. That is not a flaky test, it is a broken harness.

Cause: `clickAndSettle` dispatched the click inside `await act(async …)` and returned. But the handler awaits
`buildAuthorizeRequest()`, which awaits a **real SHA-256 through `crypto.subtle`**, and that settles on a later
macrotask than act's microtask flush. So the assertions ran before the navigation — and the attempt then resolved
during the *next* case, calling **that** case's `assign` stub. One case's async work was being attributed to
another.

Fixed by draining five macrotask turns inside `act` after the click, with the measurement recorded in the comment
so the next person does not "simplify" it away. Three consecutive green runs.

This is worth naming as a class: **`act()` does not await anything that leaves the microtask queue.** Every later
screen task that awaits real crypto, a real timer or a real network boundary will hit it.

### 2. Two tests from earlier tasks had to move, and I only predicted one

Phase 2 predicted T02's absence assertion. It did not predict **T13's**, which failed on the whole-suite run:

```
expected [ …(8) ] to deeply equal [ …(7) ]
+ "https://react.i18next.com/latest/usetranslation-hook"
```

`useTranslation` puts that documentation link in the error it throws when no i18n instance is found. The hook was
already used by `RateLimitNotice` — but nothing imported that component, so Rollup tree-shook it out. The sign-in
screen is reachable from `main.tsx`, so the hook and its error string now ship.

**T13's guard worked exactly as designed.** Its own comment says a URL arriving from a new dependency "fails this
file until someone justifies it here". So it was justified there, in place, with a dated note. The list stays a
set equality.

Recorded because it is a good sign, not a bad one: a task-13 assertion caught a real change in what ships, two
tasks later, with no human noticing.

---

## Findings against my own implementation

### S1 — `expect(JSDOM_ORIGIN).toBe('http://localhost:3000')` is a dead assertion

The AC14 case ends with that line. It asserts a constant equals its own literal — the exact defect class that
produced T15's two surviving mutants, and I wrote it anyway while wiring the constant up. It proves nothing and
should either be deleted or turned into something real: the origin the shell actually mounts at.

**Fix:** assert `globalThis.location.origin` equals `JSDOM_ORIGIN` *before* the out-of-basename mount, which is
what makes that mount meaningful — the shell renders nothing *because* the origin is outside `/app`.

### S2 — The AC13 console spy may be weaker than it looks

It spies on every function-valued key of `globalThis.console` and asserts `length > 10`. If a future environment
exposed `console` as a prototype-based object with non-enumerable methods, `Object.keys` would return few or none,
the `> 10` guard would fail loudly — good. But a *partial* shrink (say 11 methods instead of 20) would pass with
fewer spies than intended. Low severity, worth a comment stating the guard is a sample and the source scan is
authoritative.

### S3 — `credentialInputsIn` returns selectors, and the decoy's expectation hardcodes three of them

The non-vacuity case asserts the decoy yields exactly `['input[type=password]', 'input', 'form']`. That is correct
today but couples the control to the *order and membership* of `CREDENTIAL_SELECTORS`. Adding `'button[type=submit]'`
to the list would fail the decoy case for no real reason.

**Fix:** assert the decoy's result *contains* `'input[type=password]'` and is non-empty, and keep the exact
equality only for the real screen (where `[]` is the meaningful claim).

### S4 — Nothing asserts the button is the *only* control

AC1 proves there is no credential input. AC6 proves the button is a real button. But a screen that also rendered
a stray second button, or a link to somewhere unexpected, would pass everything. On the project's first screen —
the one an unauthenticated visitor meets — "exactly one interactive control, and it is this one" is worth pinning.

**Fix:** assert `querySelectorAll('button, a, input, [tabindex]')` has length 1 and is the action button.

### S5 — The failure path never proves the ref was actually reset

AC8's retry case asserts `aria-disabled` is back to `false` after a failure. That is the *rendered* consequence of
`setStatus('failed')`, not proof that `inFlight.current` was reset — and those are different things. If a
regression reset the status but not the ref, the button would look enabled and do nothing on the second click:
the worst possible failure mode, because it is invisible.

**Fix:** after a failure, make the second click succeed — swap the mock to resolve, click again, assert `assign`
was called. That tests the ref, not its shadow.

### S6 — `aria-disabled` is asserted as a string, which is correct but undocumented

`expect(action.getAttribute('aria-disabled')).toBe('false')` works because React renders the boolean as a string
for `aria-*`. A reader may try to "fix" it to `toBe(false)`. One comment prevents that.

### S7 — The in-flight announcement is never observed mid-flight in a browser

The test asserts the live region holds `signIn.starting` after `clickAndSettle` — which in jsdom is true because
the navigation never happens. In a real browser the document is replaced, so whether a screen reader ever
announces it depends on timing the test cannot model. The case is honest about what it checks (the state is
rendered) but the artifact should not claim the announcement is *heard*.

---

## Not findings

- **No styling.** Deliberate (D-10). The screenshot shows browser defaults, which meet WCAG AA contrast.
- **The component imports `buildAuthorizeRequest` directly** rather than taking it as a prop. Correct: there is no
  DI container here, and the failure case mocks the module, which is the established pattern (T15 does the same).
- **`globalThis.location.assign`.** Confirmed correct by the real-browser run.
- **No `RateLimitNotice` mount.** Still right; the screen makes no API call.
- **Five catalogue keys.** OQ-1 remains the owner's.

---

## Next

Phase 8 — independent review. **Self-produced** at the owner's instruction for this task, and labelled as such.
