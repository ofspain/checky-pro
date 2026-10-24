# frontend · T10 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | the Phase 6 implementation, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/07-self-review.md` |

## Findings

### 1. AC6 cannot detect what it is named for
It asserts that a repeated end-session navigation is declined identically, as a stand-in for "a second tab is not
served the first tab's cached response". But nothing in this task ever *writes* to the cache, and the fake cache is
rebuilt per case, so no cached response could exist to be served. What the case actually proves is that the decision
is stateless — real, but weaker than R22's cross-tab concern, which lives in the worker's caching behaviour (tasks
11 and 12) and in T06's cross-tab machinery.
**Severity ·** Low. The case is kept because statelessness is worth pinning, but its name overstates it and the
artifacts should not claim cross-tab coverage.

### 2. Every AC1/AC4 case asserts "declined", which an unknown path also returns
`simulateNavigationFallback` returns `undefined` both for a denylisted navigation and for a path it simply does not
recognise. So a mutant that removed `/connect/logout` from the denylist would still return `undefined` from the
harness — AC1's and AC4's harness assertions alone would not catch it. They are saved by the explicit
`isDenylistedNavigation(...) === true` assertion in the same cases, and by AC7's membership check.
**Severity ·** Low, and already mitigated — but worth the mutation pass confirming it explicitly (AC-M mutant 1).

### 3. The task has no production footprint, so it cannot constrain the real worker
By D1 this is a verification task. It demonstrates that T09's policy keeps the end-session response and the ID token
out of Cache Storage *when the fallback is ordered denylist-before-cache*. It does not and cannot make task 12 wire
it that way.
**Severity ·** Medium as a limitation, Info as a defect — it is the declared shape of the task, recorded in D5 and
in the test file's header.

### 4. The `agents.md` parse is now in a third file
T05, T08, T09 and now T10 each carry their own copy of a "find the paragraph, extract the backticked paths" parse.
T10's is a narrower variant (it only asserts the block contains `` `/connect/logout` ``), but it is the same idea
four times.
**Severity ·** Info. Extracting a shared spec-reading helper would touch frozen files; worth doing when a task
legitimately opens one of them.

### 5. Two harness copies, one warning
D4 accepted the duplication, but the header comment telling maintainers to keep them in step could only be added to
T10's file — editing T09's test to carry the same warning is an edit to a frozen file. A maintainer who opens T09's
copy first sees no warning.
**Severity ·** Info, deliberate, recorded.

### 6. The `400` variant tests a URL, not a response
The foreign-origin `post_logout_redirect_uri` case asserts the *navigation* is declined. It does not assert that
SAS's `400` response is never cached, because this task has no responses — only a decision. The reason the variant
exists (an error response must not be cached either) is therefore argued in the artifacts, not proven in the test.
**Severity ·** Info.

### 7. Importing `../app/routes` couples a worker-policy test to the router module
Accepted by D3 as the typed alternative to a regex over source. It pulls `react-router` into this test file's module
graph. Side-effect-free and fast in practice, but it means a break in `routes.tsx` fails this file too.
**Severity ·** Info.

## Checklist (no finding)

- **The URL shape is the backend's**, cited to `SasLoginIntegrationTest:519-527` and `:488-490`, not invented.
- **No real credential committed** — `ID_TOKEN` is prefixed `fake.` and has no valid signature.
- **AC2 is asserted over recorded keys**, not only call counts, and the one vacuous assertion was removed.
- **AC3 pins the other half of the flow** — `/app/signed-out` and `?state=s1` are answered by the shell, so AC1 and
  AC4 are not vacuously true and offline sign-out still renders.
- **AC5 runs both navigations against one cache**, asserting the only key ever used is the shell's and that no key
  contains `?` or the token.
- **No production change, nothing frozen touched** — `navigationDenylist.ts`, T09's test and every T01–T08 file are
  unmodified (L12); `vite.config.ts` and `.oxlintrc.json` untouched.
- **No worker registered or emitted** — T09's D1 still holds; task 12 owns that.
