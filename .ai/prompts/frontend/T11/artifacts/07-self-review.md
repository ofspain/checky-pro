# frontend · T11 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | the Phase 6 implementation, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/07-self-review.md` |

## Findings

### 1. Default-deny makes most AC1 rows individually weak
Because NetworkOnly is the negation of a narrow cacheable set, a predicate that simply returned `true` for
everything would satisfy all twenty-seven AC1 rows, all of AC3, AC4, AC6 and AC8. The only real counterweight is
AC2 — the shell and assets must still be cacheable — plus AC10's negation identity. That is a sound design, but it
means the suite's strength rests on far fewer cases than its count suggests, and the phase 10 mutant that inverts
the default is the one that proves AC2 earns its place.
**Severity ·** Medium as a reading of the test count; not a defect.

### 2. `segmentPrefixPattern` is now duplicated, and nothing asserts the copies agree
T09 keeps it module-private (`navigationDenylist.ts:40`), so this module carries its own copy (D5). Nothing fails if
the two diverge — T11's boundary rows and T09's would each still pass against their own copy. A shared helper is the
real fix and needs a task that legitimately opens T09's module; task 12 is the natural one.
**Severity ·** Low, recorded, with the fix named.

### 3. The exported prefix list is unused by the decision, so only the drift guard defends it
D1 deliberately keeps `NETWORK_ONLY_PATH_PREFIXES` out of the predicates. The consequence is that a wrong entry in
that list changes no behaviour here — AC9's parse against `design.md` is the only thing that would notice — but it
**will** change behaviour in task 12, which builds Workbox matchers from it. So the list is load-bearing for a task
that has not been written yet, defended by one test.
**Severity ·** Medium for task 12, Low here. Task 12 must not restate it.

### 4. `/app/sign-in` being NetworkOnly as a request is a judgement call, and practically unobservable
D5 decided the cacheable set is the shell document and assets, so a non-navigation request for an SPA route is
NetworkOnly. In a browser that request essentially never happens — an SPA route arrives as a navigation, which T09
answers. So the decision is defensible and faithful to §5, but no production behaviour depends on it, and a future
reader could reasonably have chosen the other way (Kimi's phase 3 finding 5 did).
**Severity ·** Info, flagged because it diverges from the reviewer's suggestion by choice, not by oversight.

### 5. There is no drift guard against `agents.md:90`
AC9 guards the prefix list against `design.md`'s edge map. Nothing guards the policy against the sentence that
actually mandates it — *"API routes use NetworkOnly"* in `agents.md`. If that line were softened or removed, every
case here would still pass. T09 and T10 both guard their rule's own sentence; this task guards a different file.
**Severity ·** Low. Worth a case in phase 10's gap list rather than a code change.

### 6. The harness proves the decision, not the plumbing
Stated in D10 and in the file header, repeated here so phase 13 carries it: a pure policy cannot prevent a cache
write. **Task 12 must wire a true `NetworkOnly` strategy that neither reads nor writes.** Everything AC1–AC8 proves
is that the classification is right.
**Severity ·** Medium as a limitation, Info as a defect — the declared shape of the task.

### 7. AC10's purity scan keeps trailing comments
Same limitation as T09's and T10's: block comments and whole-line comments are stripped, trailing ones are not. Over-
strict in the safe direction.
**Severity ·** Info.

## Checklist (no finding)

- **Default-deny is structural** — `isNetworkOnlyRequest` is literally `!isCacheableRequest`, asserted over every
  path the file uses (AC10), so the two cannot drift.
- **The twelve prefixes match the edge map**, in order, parsed from `design.md` with an explicit boundary (AC9).
- **`/api` not `/api/v1`** (D3), so a future `/api/v2` is NetworkOnly without an edit.
- **`/notifications` is covered and reasoned** (AC8): a buffered or replayed SSE stream is broken, not stale.
- **The four sensitive body kinds are named** (AC3), not just implied by prefix.
- **Method independence is structural** — the predicates take only a `URL`; AC4 exercises four methods through the
  harness, where a method could plausibly be consulted.
- **The policies compose** (AC5): for each overlapping path T09 declines the navigation and T11 marks the request
  NetworkOnly, asserted by importing T09 rather than restating it.
- **Boundaries hold** (AC7): `/application`, `/app2`, `/appfoo`, `/app/assetsx/evil.js`, `/app-assets/main.js` are
  all NetworkOnly.
- **Purity** (AC10): no `caches`, `fetch`, `self`, storage, `navigator`, `document`, `import.meta` or absolute URL.
- **L12 honoured** — two new files; `navigationDenylist.ts` and the T09/T10 tests untouched; no T01–T08 file edited.
