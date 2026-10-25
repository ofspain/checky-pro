# frontend · T16 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | **Claude Opus 5 — SELF-PRODUCED** |
| **Consumes** | the phase 6 implementation, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

> **Normally Kimi 2.7's phase.** The owner instructed, for T16 only: *"do not wait on kimi, do everything yourself
> for this phase."* Labelled so the record is accurate. A review of my own work cannot find my own blind spots,
> and the findings below are the ones I could reach by re-reading the code cold and by probing the module graph —
> not by being surprised.

---

## Method

Re-read the six changed files against the frozen brief. Probed the import graph, the build, and the test harness
rather than reasoning about them. Phase 7's seven findings are taken as read and not repeated; six new ones
follow, and the first is the serious one.

---

## New findings

### 1. T16 introduces a circular import, and it is benign only by accident
**Issue ·** Attaching the element in `routes.tsx` closes a loop:

```
app/routes.tsx  →  auth/SignInScreen.tsx  →  auth/authorizeRequest.ts  →  app/routes.tsx
```

`authorizeRequest.ts:1` has imported `APP_BASENAME` from `../app/routes` since T15, which was fine while nothing
in `app/` imported back. T16 closes the cycle.

**Why it does not break today:** `APP_BASENAME` is read at `authorizeRequest.ts:60`, **inside `redirectUri()`**,
which runs at click time. At module-evaluation time the binding is in its temporal dead zone, but nothing touches
it, so evaluation completes and the binding initialises afterwards.

**Why that is not good enough:** the safety is an accident of where one `const` sits. Hoisting that lookup to
module scope — a refactor any reasonable person might do, for instance to compute the redirect URI once —
produces `ReferenceError: Cannot access 'APP_BASENAME' before initialization` **at application start**, which is
a white screen with a stack trace pointing at the wrong file. Rollup emitted no circular-dependency warning, so
nothing in the build would say so either.

**Severity ·** Medium. No present defect, real latent fragility, and it is on the application's entry path.

**Recommendation ·** Keep the cycle — `routes.tsx` is specified as "the single source of the route set" and its
own comment says screens are attached by their own tasks, so attaching elsewhere would contradict the design —
but make it **safe and visible** rather than accidental:
1. A source assertion that `authorizeRequest.ts` uses `APP_BASENAME` only inside a function body, so the one
   property the cycle depends on is pinned by a test instead of by luck.
2. A case that imports the modules in the real entry order (`app/routes` first, as `mountShell` does) and asserts
   `buildAuthorizeRequest()` still produces the right `redirect_uri` — proving the cycle resolves under the order
   the application actually uses, not just the order a test happens to use.
3. A comment in `routes.tsx` naming the cycle, so the next reader meets it deliberately.

### 2. The AC14 case creates a browser router it cannot dispose
**Issue ·** It calls `mountShell`, which calls `createBrowserRouter`. T02's test file treats router disposal as
mandatory — it keeps a `toDispose` handle and calls `dispose()` in `afterEach` (`:79, :88-89`). `mountShell`
returns only the `Root`, so my case unmounts the React tree but leaves the router's history subscription behind,
twice per run.
**Severity ·** Low — the suite passes and jsdom is torn down per file. But it is a documented convention in this
package, broken by the newest file, and leaked history listeners are exactly the kind of thing that makes a later
test mysteriously flaky.
**Recommendation ·** Either have the case build its own router the way T02 does, or accept it with a comment
saying why unmounting is sufficient here. The former is better: it makes the case independent of `mountShell`'s
return type.

### 3. The explanation is not programmatically associated with the action
**Issue ·** The `<p>` explains what the button will do, but nothing links them. A screen-reader user tabbing
straight to the button hears only "Continue to sign in" — the sentence explaining they are about to leave the
application for a server-hosted page is not announced with the control.
**Severity ·** Low-medium. L11 is a locked requirement and this is the project's first screen, so the pattern set
here will be copied.
**Recommendation ·** `aria-describedby` on the button pointing at the paragraph's `id`. Two attributes, no new
structure, and it makes the relationship real rather than visual.

### 4. `aria-disabled="true"` with a clickable element gives a mouse user no feedback for the swallowed click
**Issue ·** During the attempt the button is `aria-disabled` but still clickable; the ref silently discards the
second click. A screen-reader user is told it is disabled. A sighted mouse user sees an ordinary button that does
nothing.
**Severity ·** Low — the live region does say "Taking you to the sign-in page…", which is the feedback. But it is
worth being deliberate.
**Recommendation ·** Accept, and note it. `aria-busy="true"` alongside would describe the state more accurately
than `aria-disabled` alone, and costs one attribute. Either is defensible; the silence is not a defect because
the live region covers it.

### 5. Nothing proves the screen has exactly one interactive control
*(Phase 7 raised this as S4; repeated here because it is the strongest of the unapplied findings.)* On the one
screen an unauthenticated visitor meets, "there is no credential input" and "there is exactly one control, and it
is the sign-in button" are different claims, and only the first is tested. A stray second button or an
unexpected link would pass everything.
**Recommendation ·** Apply S4.

### 6. The `starting` state is unreachable in a real browser, and the test cannot say otherwise
**Issue ·** The AC7 case asserts the live region holds `signIn.starting` after the click. In jsdom that is true
because the navigation never happens. In a real browser the document is replaced, and the real-browser run in
phase 7 confirms it: the page became SAS's `/login`. So the string is rendered for a window the test cannot
measure and a user may never perceive.
**Severity ·** Informational — it is still right to render it (a slow or failed navigation leaves it visible, and
that is exactly when it is needed), but the artifact must not claim it is *heard*.
**Recommendation ·** Keep it, and say in the test file that the case asserts the state is rendered, not that it
is announced.

---

## Confirmed sound

- **R6 is demonstrated, not asserted.** The real-browser run is the strongest evidence in this task: the password
  field exists on SAS's page and nowhere in our bundle.
- **The synchronous ref guard** (D-1) is correct and the two-click case proves it.
- **Not moving focus** (D-2) is correct, and phase 3's reasoning — that `RateLimitNotice`'s focus-move exists
  because dismissal unmounts the focused button — holds.
- **The inverted catalogue scan** (D-3) can fail, which the old form could not.
- **T13's vendor-URL list was extended in the way its own comment asks for**, with a dated justification, and the
  set equality is preserved.
- **T02's absence assertion was narrowed, not deleted**, with a named constant task 17 must widen and a predicate
  shown to count.
- **The harness drains real async work**, and the cause is recorded in the comment rather than papered over.
- **No new dependency, no styling, no new route path, no `sessionStorage` write of T16's own.**
- **`src/auth/pkce.ts` and `src/auth/authorizeRequest.ts` are untouched.**

---

## Summary

The implementation is sound and, unusually for this pipeline, has been observed working against the live platform
rather than only asserted. The one finding that matters is the circular import: it is harmless today, it is
harmless for a reason nobody wrote down, and the failure it would produce is an application that does not start.
Phase 9 should pin that property with a test. The rest are small: a disposal convention, an `aria-describedby`,
and three test-quality items that phase 7 already identified.
