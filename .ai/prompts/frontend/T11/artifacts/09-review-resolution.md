# frontend · T11 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

## Dispositions

### Phase 8 finding 1 — AC5 covered five of twelve overlapping prefixes — **FIXED**
Accepted, and Kimi's own caveat is the interesting part: R57's navigation list is **not** the edge map. It covers
`/connect` only at `/connect/logout`, and it never mentions `/notifications`, because that is an API route and
therefore this task's business. So AC5 now iterates **all twelve** prefixes against a table that records, per
prefix, whether the navigation denylist also claims it:

- the invariant asserted for every one of the twelve: **never the shell, always NetworkOnly**;
- `isDenylistedNavigation` asserted `true` for the ten R57 also names, `false` for `/connect` and `/notifications`;
- a table-order case, so the pairing cannot silently fall out of step with `NETWORK_ONLY_PATH_PREFIXES`;
- and one further case asserting `/connect/logout` **is** a denylisted navigation, so the `false` for bare
  `/connect` reads as R57's wording rather than as a gap — T10 owns that path's flow.

### Phase 8 finding 2 — no future `/api/v2` case — **FIXED**
Two rows added to AC6: `/api/v2/accounts` and `/api/health`. The `/api/v1` → `/api` normalisation (D3) existed
precisely so a version nobody has written yet is already covered; now something fails if that is undone.

### Phase 8 finding 3 — `repoRoot` computed at module load — **ACCEPTED, no change**
Kimi states no change is required for T11, and the `repoFile()` helper already names the computed root in its
failure message. This is the T08 owner decision applied for the fourth time: keep the pattern, keep the existence
check. `import.meta.dirname` remains unavailable under the Vite transform in jsdom.

### Self-review 1 and Phase 8 corroboration — default-deny makes most rows weak — **ACCEPTED, no change; phase 10 must prove it**
Both reviews agree the design is right and the test count is less meaningful than it looks. The honest resolution is
not more cases but the mutant: **inverting the default must fail AC2**, and phase 10 reports that explicitly rather
than leaving the reader to infer the suite's real strength.

### Self-review 3 and Phase 8 corroboration — the exported list is load-bearing for task 12 — **CARRIED FORWARD, sharpened**
Kimi marks it an important carry-forward and adds the operative instruction: **task 12 must import
`NETWORK_ONLY_PATH_PREFIXES` and must not restate it.** Recorded in phase 13 alongside the matching obligation from
T09 and T10 (import `NAVIGATION_DENYLIST`, guard the precache manifest, keep `/app/signed-out` answerable).

### Self-review 2, 4, 5, 6, 7 — **ACCEPTED, no change**
The duplicated `segmentPrefixPattern` (a shared helper needs a task that legitimately opens T09's module — task 12);
`/app/sign-in` NetworkOnly as a request (defensible under §5, practically unobservable, and a deliberate divergence
from Kimi's phase-3 suggestion); no drift guard against `agents.md:90` (AC9 guards `design.md`'s map instead — worth
Kimi's phase-11 attention rather than a change now); the harness proving the decision and not the plumbing (D10,
task 12); AC10's purity scan keeping trailing comments (over-strict in the safe direction).

## Net change in this phase

`frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts` only: AC5 rebuilt as a twelve-row table plus two
supporting cases, and two AC6 rows. **No production change.** 158 pass in `src/pwa` (61 T09 + 23 T10 + 74 T11).
