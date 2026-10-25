# frontend · T16 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | **Claude Opus 5 — SELF-PRODUCED** |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

> **This phase is normally Kimi 2.7's.** The owner instructed, for T16 only: *"do not wait on kimi, do everything
> yourself for this phase."* This is therefore a self-review wearing the adversarial phase's hat, and it is worth
> less than an independent one — I cannot be surprised by my own blind spots. Recorded plainly rather than passed
> off as an external review.

---

## Method

Read `artifacts/02-task-implementation-brief.md` against R6, L2, L11, `agents.md`, React 19's actual semantics,
and the two existing precedents in this package (`RateLimitNotice.tsx`, `mountShell.tsx`). Nine findings. The
first three are real defects in the proposed component; the rest are boundary and test-quality problems.

---

## Findings

### 1. `disabled` does not actually prevent the double submit the brief says it prevents
**Issue ·** The brief justifies `disabled={status === 'starting'}` as stopping a second click from building a
second authorize request. It does not, reliably. `setStatus('starting')` schedules a re-render; the `disabled`
attribute only exists *after* React commits it. Two clicks dispatched in the same task — a double-click, a stuck
Enter key, a test firing two `click` events in one `act()` — both enter `startSignIn` before any re-render, both
call `buildAuthorizeRequest()`, both overwrite the three L16 items, and both call `assign`. The guard is a
rendering artefact being used as a mutual exclusion.
**Severity ·** Medium. In a real browser the first navigation usually wins the race, so the symptom is rare and
intermittent — the worst kind to debug.
**Recommendation ·** Guard with a synchronous ref, and keep `disabled` only as the visual consequence:
```tsx
const inFlight = useRef(false);
async function startSignIn() {
  if (inFlight.current) return;
  inFlight.current = true;
  try { … } catch { inFlight.current = false; setStatus('failed'); }
}
```
Note it stays `true` on success, deliberately: the page is being replaced, and re-enabling would invite a second
navigation during the redirect. Add a test that fires two clicks synchronously and asserts `assign` was called
once — which the brief's design would fail.

### 2. A disabled button drops the keyboard user's focus to `<body>`
**Issue ·** The brief disables the button during the attempt. A focused element that becomes `disabled` is removed
from the a11y tree and loses focus, which falls to `<body>`. On success this is invisible (the page goes away). On
**failure**, the sequence is: click, button disables, focus lost, request rejects, focus moved to the live region
— the user passes through a focusless state, and if the rejection is slow they are stranded there. L11 requires
managed focus.
**Severity ·** Medium — an accessibility regression on the project's first screen, in the exact area L11 names.
**Recommendation ·** Use `aria-disabled` plus the ref guard from finding 1, and leave the element enabled so it
keeps focus. `aria-disabled` tells assistive technology the control is inert; the ref makes it actually inert. A
real `disabled` attribute is the wrong tool for a transient in-flight state on the only control on the screen.

### 3. The focus-move on failure copies `RateLimitNotice`'s pattern without its reason
**Issue ·** The brief says moving focus to the live region "mirrors `RateLimitNotice`'s focus handling". It
mirrors the mechanism but not the cause. `RateLimitNotice` moves focus because **dismissing unmounts the button
that had focus** (`RateLimitNotice.tsx:25-31` says exactly that) — focus would otherwise be destroyed. On the
sign-in screen the button is still there, still focused. Moving focus away from it is unsolicited focus theft,
and focusing a `role="status"` element can produce a double announcement: once because the live region's content
changed, once because focus landed on it.
**Severity ·** Medium — the stated justification is false, and the behaviour is probably worse than doing nothing.
**Recommendation ·** Do not move focus. Leave the user on the button they pressed — the live region announces the
failure, which is what `aria-live="polite"` is for, and the control they need to retry is already under their
hands. Drop `tabIndex={-1}` and the `liveRegion` ref with it. Keep the always-rendered region. If a later
usability finding says otherwise, that is a change with evidence behind it.

### 4. AC5's source assertion is confused and would be vacuous
**Issue ·** AC5 says "the component source contains no quoted string that appears in the rendered text". Every
component source is full of quoted strings — `'button'`, `'status'`, `'polite'`, `'signIn.heading'`. As written
the assertion is either trivially false or, if the matcher is loose enough to pass, meaningless. It is the same
defect class that produced T15's surviving mutants: an assertion that cannot fail.
**Severity ·** Medium — it is the only guard for "no hardcoded copy", which is a locked rule (`agents.md:96`).
**Recommendation ·** Invert it. Read the catalogue's four values, and assert **no** component source file contains
any of them as a literal:
```ts
for (const value of Object.values(en.signIn)) expect(componentSource).not.toContain(value);
```
That fails the moment someone inlines the real copy, and it cannot be satisfied by rendering nothing, because the
companion assertion checks the *rendered* text equals those same values. Feed it a synthetic source containing one
of the values as the negative control.

### 5. AC2 and AC4 re-assert T15's contract, and the brief does not say which parts are T16's
**Issue ·** AC2 checks `response_type`, `code_challenge_method` and `scope` in the URL; AC4 recomputes the
challenge from the stored verifier. Both are already owned by T15, with 50 cases and 28 mutants behind them. A
reader of T16's suite cannot tell which assertions are T16's own obligation and which are duplication, and a
future change to the request shape would now fail in two places with no indication of which is authoritative.
**Severity ·** Low — duplication, not error. But undeclared duplication rots.
**Recommendation ·** Keep both, with their purpose stated in the test file. T16's own obligation is narrow: *the
URL that reaches `assign` is the one `buildAuthorizeRequest` produced.* The three parameter checks exist to
demonstrate R6's words — "the authorization code flow with PKCE" — on the screen that starts it, not to re-own the
shape. The strongest version of this is to assert the URL passed to `assign` is **identical** to the one a
directly-called `buildAuthorizeRequest()` returns for the same stubbed origin, parameter by parameter except
`state`, `nonce` and `code_challenge`, which are fresh per call. State that T15 is authoritative for the shape.

### 6. The brief never says what happens to the returned `state` and `nonce`
**Issue ·** `buildAuthorizeRequest()` resolves with `{url, state, nonce}`. The component destructures only `url`.
That is correct — T15 already persisted all three L16 items, and L16 forbids T16 adding a fourth key — but the
brief does not say so, and a reviewer could reasonably read the discard as an oversight and "fix" it by storing
them again.
**Severity ·** Low, but it is exactly the kind of silent invariant that gets helpfully broken.
**Recommendation ·** Destructure `url` only, with a comment saying the other two are already in session storage
and that re-storing them here would add nothing and risk divergence. Add an assertion that
`sessionStorage` holds exactly three keys after the action — which the brief has, but tie it to this reason.

### 7. The failure path discards the error completely, with no way for a developer to see it
**Issue ·** `catch {}` with no binding is right for the user — T15 can reject with a `TypeError` naming the
verifier contract, which must not reach the screen. But nothing records it anywhere. A real failure
(`crypto.subtle` unavailable on an insecure origin, for instance) shows the user "try again" forever and leaves a
developer with nothing at all.
**Severity ·** Low for correctness, real for operability.
**Recommendation ·** Accept the silence, deliberately and on the record. The alternatives are worse here: a
`console.error` is a logging channel on a screen whose whole purpose is to touch credentials never, and
`agents.md:82`'s "never log … PII" is a habit better kept absolute on this surface than reasoned about case by
case. The diagnosis path for a developer is that `crypto.subtle` being absent is visible in one line in the
console from the browser itself, and T14's suite pins the primitive. Record it as an accepted trade-off rather
than an oversight, and revisit when a real telemetry decision exists.

### 8. AC9's negative control cannot mutate `appRoutes`, and the brief does not say how it works
**Issue ·** AC9 wants a negative control where "a second attached element fails it". `appRoutes` is a
module-scope `const` built from two frozen path arrays; a test cannot attach a second element to it without
mutating shared state that other cases in the same file read.
**Severity ·** Low — but an unspecified negative control tends to become no negative control.
**Recommendation ·** Extract the check into a predicate over a route array — `routesWithElements(routes)` →
the list of paths carrying one — and run it twice: once over the real `appRoutes` (expecting exactly
`['sign-in']`) and once over a locally built array with two elements (expecting both, so the predicate is shown to
count). No mutation of shared state, and the predicate is the thing under test.

### 9. A successful click leaves a dead screen on display until the browser navigates
**Issue ·** After `assign`, the document lives until the browser commits the navigation — tens of milliseconds
normally, much longer on a cold connection. The screen shows a heading, an explanation and an inert button, with
nothing saying anything is happening.
**Severity ·** Low, and it is partly a copy question (OQ-1).
**Recommendation ·** Announce it in the live region that already exists: a catalogue string for the in-flight
state, placed in the region when `status === 'starting'`. One more key, no new structure, and it makes the
`aria-live` region earn its place on the success path as well as the failure path. The owner can reword it with
the rest of OQ-1.

---

## Not findings

- **`<button type="button">` over a link.** Correct. The destination is not a document the user should be able to
  open in a new tab with no PKCE state, and a link would do exactly that.
- **`globalThis.location.assign` over a router navigation.** Correct, and the brief's reason is right: a router
  navigation would resolve `/oauth2/authorize` inside the `/app` basename and hit the catch-all.
- **The provider wrapping `AppShell` rather than sitting inside it.** Correct — it keeps `AppShell` rendering only
  its children, so T01's `innerHTML === ''` is untouched.
- **Narrowing T02's absence assertion rather than deleting it.** Correct, and the conservative choice.
- **No styling.** Correct for this package today. WCAG AA contrast on browser defaults passes.
- **Placing the screen in `src/auth/`.** Correct per `design.md` §6.
- **Not mounting `RateLimitNotice`.** Correct — no notice state exists on a screen that makes no API call.
- **No `@testing-library/react`.** Correct; the `createRoot` + `act()` pattern is established here.

---

## Open questions

- **OQ-1 (copy)** grows by one key if finding 9 is accepted. Still the owner's, still gating nothing.
- **OQ-4 (T02's assertion)** unchanged: narrowed, not deleted.
- **No new blocker.** Every finding is resolvable inside T16.

---

## Next

Phase 4 — freeze the brief with these findings folded in. Findings 1, 2, 3 and 4 change the implementation; 5, 6,
8 and 9 change the tests or the record; 7 is accepted as a stated trade-off.
