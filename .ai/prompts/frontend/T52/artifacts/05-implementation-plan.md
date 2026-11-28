<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). -->

# frontend · T52 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

**Baseline: 2148 / 2148, lint 0, types 0.**

---

## 0 · The freeze's open item, answered — and the answer is *it does not exist*

D-T52-1 required this plan to **locate** the instrument that *"checks every key is RENDERED"*, referenced at
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:279`, before writing the catalogue step.

**There is no such global instrument.** Every catalogue pin in the tree is **section-scoped**:

| Where | What it pins |
|---|---|
| `screensRenderThroughDesignSystemPrimitivesOnly:926` | **set equality against the whole catalogue key set** — fires on a key's *existence*, rendered or not |
| `publicRouteSetIsExactlyTheDeclaredSet:544` | `en.signedOut`, `en.signIn`, `en.callback` **only** |
| `callbackValidatesNonce:830`, `sasRefusalFor…:1397` | `en.signIn`, `en.callback` **only** |

So `:279`'s phrase refers to the **per-screen** *"renders the catalogue entries, as literal English"* cases —
each owned by that screen's own suite, never a tree-wide sweep. **Consequences, both of which this plan now
predicts rather than discovers:**

1. Adding `passwordResetRequest` fires **exactly one** pin, `:926`, and **nothing complains about an unrendered
   key**. That is *two steps earlier* than a plan would naturally expect — the thing T48's and T50's plans each
   got wrong in the same direction.
2. Rendering the seven keys is **T52's own suite's** obligation (AC3, AC5), not an inherited sweep's.

**`publicRouteSetIsExactlyTheDeclaredSet` therefore changes for the element-attachment case alone** — its
catalogue pin names three sections and a fourth does not disturb it. One of the six files in D-T52-1 needs a
**narrower** change than the freeze assumed; stated here rather than found in Phase 6.

## 1 · The order, with a prediction at every step

The order exists so that **each change is the only candidate** when something turns red. A step whose
prediction misses is a finding, and is recorded as one.

### Step 1 — promote the constant, attach nothing

```ts
export const PASSWORD_RESET_REQUEST_PATH = 'reset/request';   // same string value
```

**Predict: GREEN, 2148/2148.** `routesResolveUnderAppPrefixOnly:242` hard-codes the spelling, so an identical
value is invisible to it. **If this step is red, the spelling changed** — which is the only thing that case is
for.

### Step 2 — the named suite, **before the screen exists** (AC13)

`passwordResetRequestAcknowledgementIsUniform.test.tsx`, driving the **real route table** through a memory
router at `/app/reset/request` — *not* importing the screen directly, so the failure is a **measurement** and
not a module-resolution error. The precedent for the failure message already exists:
`publicRouteSetIsExactlyTheDeclaredSet:482` — *"the route resolved to nothing — before task 37 it had no
element."*

**Predict: RED, and every new case fails *because the route resolves to nothing*.** This is AC13's evidence.
**A suite that fails to LOAD would prove nothing** — T175 paid for that lesson when a mutant stopped 1350 tests
from loading and three criteria went unmeasured while the table called one of them "as expected".

### Step 3 — the catalogue section

Seven flat keys as frozen. **Predict: RED at exactly one case** — `:926`, *"keeps the catalogue's key set exactly
as it was"*. Then add the seven paths to that list. **Predict: that case GREEN**; the named suite still red.

### Step 4 — the screen

`PasswordResetRequestScreen.tsx` exactly as §2 of the freeze fixes it, including `parseAs: 'stream'` **from
birth**. **Predict RED in three places, and no others:**

| | |
|---|---|
| `screensRenderThroughDesignSystemPrimitivesOnly:337` | *"names every file that imports the primitive barrel"* — the new file imports it |
| …`:266` | `RENDER_SITES` is pinned `toHaveLength(11)`; adding the file makes **12** |
| `generatedClientCallsDoNotParseBodies…` | **both** its census case and its invariant case — a new non-reading call appears. **If only the census fires, the call is missing `parseAs`** |

**Predict GREEN without change:** `:292` (*"exactly twelve files bearing a lowercase tag"*) — this screen renders
**none**, so the count is untouched. That is a prediction, not an assumption, and a red there means a bare
element got in.

### Step 5 — attach the element

**Predict: the named suite flips GREEN, and two inherited pins go RED:**

| | |
|---|---|
| `publicRouteSetIsExactlyTheDeclaredSet:722` | the case **enumerates the attached paths in its own title** and asserts *"and to nothing else"*. **The title must change too** — T50 grew this "the fourth time"; T52 is the fifth |
| `loginChallengeRunsInsideSasFlowAndResumesAtCallback:263` | the form pattern is `/<\s*form\b/gi` — **case-insensitive**, so the `<Form>` **primitive** counts. Needs a **fourth** allow-list row, `count: 1`, with the `why` naming R14's email-only form |

### Step 6 — the timing claim (AC8), measured not assumed

The screen introduces **no timer**. **Predict: the tree-wide timer scan in
`auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` needs no new allow-list entry.** If it fires,
the screen gained a delay nobody intended — which is exactly L4's timing clause doing its job.

### Step 7 — whole suite, `oxlint`, `tsc`, then the harness

Tests are **committed before any mutant runs** — the harness reverts with `git checkout` over `REVERT_PATHS`.

## 2 · What the named suite must contain

| Group | Cases |
|---|---|
| **The call** | one POST to `/accounts/password-reset-request` with `{ email }`; **one** request under `StrictMode`; **no `Authorization` header even with session storage seeded**; one case with no session at all |
| **The acknowledgement** | non-empty; replaces the form; **provenance** — shown to come from the catalogue, not a literal that matches (T45); focus moves to the heading; one `<h1>` |
| **Uniformity (AC4)** | **≥13 arms** — valid JSON, a different `message`, no body, empty string, HTML, plain text, truncated JSON, `204`, `400`, `404`, `429`, `500`, `503`, `302`-with-body, `304` — compared on **markup, pathname, focus target, region text**. `204`/`304` carry **no body** (constructing one throws) |
| **The baseline, guarded** | non-empty, **carries the expected text**, **has no `<form`** — a uniformly broken screen also renders identically (T51) |
| **The failure arm** | the neutral retry line; the form stays mounted; a fresh submit clears it; the acknowledgement is absent |
| **The email (AC6)** | query string, hash, full URL, `sessionStorage`, `localStorage` — **each with a control proving the spy intercepts**, because an instance spy on jsdom storage does **not**, which made a T42 claim vacuous from birth and survived because the assertion was **negative** |

Helpers: the four frozen in D-T52-3. **FU-T43-2's fourth copy**, stated.

## 3 · Harness rows planned

| | |
|---|---|
| **TR41** | `parseAs` removed → the uniformity arms **and** T175's scan |
| **TR42** | `response.ok` consulted (`ok ? 'acknowledged' : 'unsent'`) → **the uniformity arms must die.** The row that proves AC4 is real rather than incidental |
| **TR43** | the reject arm set to `'acknowledged'` → the failure-arm cases |
| **TR44** | `setStatus('submitting')` removed from `submit` → the stale-retry-line case |
| **TR45** | the email interpolated into the acknowledgement → the AC6 and provenance cases |
| **TR46** | an outcome-dependent `setTimeout` → **AC8**, and the row that says L4's timing clause is held by something |
| **TR47** | CANARY — the screen renders neither state |

## 4 · Risks

| | |
|---|---|
| **A six-file change with four inherited pins** | mitigated by the step order: one change per step, each with a named prediction |
| **The arm table passing vacuously** | the guarded baseline, and **TR42** |
| **`304` / `204`** | null-body statuses; `new Response(body, {status: 204})` **throws** |
| **The screen being unreachable** | OQ-T52-3, accepted at the freeze; a known demo-path hole |

---

**Phase 5 complete. Next is Phase 6 — Implementation.**
