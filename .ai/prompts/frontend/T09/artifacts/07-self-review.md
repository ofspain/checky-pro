# frontend · T09 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | the Phase 6 implementation, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/07-self-review.md` |

## Findings

### 1. A precache route could still answer a denylisted navigation, and T09 cannot stop it
The denylist governs the **navigation fallback**. In Workbox, precaching registers its own route, which matches by
URL and runs before the navigation route. If a later task's precache manifest ever contained a denylisted URL, the
precache route would answer it and this module would never be consulted — R57 would be violated with every T09 case
still green. Nothing in this task can prevent that.
**Severity ·** Medium, and the most important limitation of the task as framed.
**Evidence ·** `navigationDenylist.ts` has no cache reference by design (D5); the fallback semantics live only in the
test harness.
**Carry-forward ·** Task 12, when it wires the worker, must (a) import `NAVIGATION_DENYLIST` rather than restate the
list, and (b) assert the generated precache manifest contains no denylisted URL. Worth adding to task 12's brief.

### 2. Nothing in production imports this module yet
By D1, T09 ships policy only; registration is task 12. So the module has no production consumer, and neither `tsc`
nor `oxlint` would notice if it were deleted or quietly bypassed — only its own test would fail, and a bypass
wouldn't even do that.
**Severity ·** Low, and inherent to the task ordering rather than a defect.
**Carry-forward ·** Task 12's test should fail if the worker's navigation route is not built from these exports.

### 3. Sub-paths of `/connect/logout` are denylisted, which is more than R57 needs
The segment rule denylists `/connect/logout/confirm`, and an AC1 case asserts it. SAS's end-session endpoint is
exactly `/connect/logout`; no sub-path exists. The behaviour is the safe direction and consistent with every other
entry, but the case pins behaviour the spec does not require.
**Severity ·** Info. Left as is for uniformity; flagged so a future reader does not read it as a spec claim.

### 4. `/connect` is otherwise unguarded, and that is R57's choice, not an oversight
Only `/connect/logout` is listed, so `/connect/register` and any other `/connect/*` navigation is not denylisted by
this clause; AC4 pins that. The edge routes those paths to auth regardless, and a worker scoped to `/app/` never sees
them (D2), but if the scope ever widened and a precache ever held such a URL, it would not be caught here.
**Severity ·** Info — faithful to R57, recorded because the gap is real.

### 5. AC9's banned-global scan does not strip trailing comments
It removes block comments and whole-line `//` comments. A banned token in a comment *after* code on the same line
would fail the case spuriously. Over-strict in the safe direction, and no current line is affected.
**Severity ·** Info.

### 6. The drift guard depends on one sentence in `agents.md`
D7's parser finds the paragraph by its opening words — `Navigation requests to these paths are never answered from
cache`. A rewording of that sentence (not of the list) breaks the guard with a `expect(start).toBeGreaterThan(-1)`
failure. That is the intended loud failure, but it makes a prose edit in `spec/` fail a frontend test.
**Severity ·** Info. The T05/T08 drift guards carry the same property.

### 7. Both predicates re-check `mode`, so `isShellNavigation` evaluates it twice
`isShellNavigation` checks the mode, then calls `isDenylistedNavigation`, which checks it again. Harmless and it
keeps each predicate independently correct for an external caller, which is the point of exporting both.
**Severity ·** Info.

## Checklist (no finding)

- **R57 verbatim** — eleven paths, no addition (`/notifications` deliberately absent, D10), no rewording, cited in
  the source comment (AC8).
- **No cache, no network, no storage, no cross-tab coupling** in the module (AC9, L3, L6, L10, L16).
- **Navigation-only** (AC6), so task 11's NetworkOnly policy is not pre-empted.
- **No over-matching** — `/accountsxyz`, `/apikeys`, `/logouts`, `/application`, `/xwell-known` all pinned negative
  (AC4); the `/xwell-known` row is what catches a dropped regex escape.
- **Shell still answers** `/app`, `/app/`, `/app/sign-in` and a deep route (AC2), so AC1 is not vacuous.
- **Mutually exclusive** — no path is both denylisted and shell (AC4).
- **L12 honoured** — no T01–T08 file touched; T01's build and registration assertions untouched and still green.
- **`vite.config.ts`, `.oxlintrc.json`, `spec/**`, `infra/**`, `contracts/**`** unchanged.
