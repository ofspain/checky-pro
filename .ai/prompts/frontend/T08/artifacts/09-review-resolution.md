# frontend · T08 · Phase 9 — Review Resolution (Human Approval gate)

STATUS: RESOLVED (2026-10-24)

Consumes `07-self-review.md` (7 findings, all Low or Info) and `08-independent-review.md` (Kimi 2.7: 2 new Low
findings plus corroboration of all 7). Each claim was checked against the file before it was decided. One item needed
an owner decision and got one; the rest are settled here. No production file was touched — T08 has none.

## Resolution log

| Source | Finding | Decision | Change |
|---|---|---|---|
| P8-1 | The table-driven POST cases never compare the recorded body to what was sent | **ACCEPTED** | Verified first: the public table asserted only `body !== ''` and the protected table asserted no body at all, so a swapped or dropped body with the right call count would have passed. `PostCase` now carries the `body` object, each entry shares one hoisted literal between its `call` and its assertion (so the two cannot drift), and both tables assert `sent[0]!.body === JSON.stringify(body)` — `''` for the two operations auth.yaml declares with no request body. |
| P7-7 / P8 corr. 7 | Most T08 cases don't assert `failures` is empty | **ACCEPTED** | `expect(failures).toEqual([])` added to both `it.each` tables, so an unexpected renewal attempt (whose rejection would land there via `onRenewalFailed`) fails the case. |
| P8-2 | `repoRoot` is computed at module load from `expect.getState().testPath` | **ACCEPTED in part (owner decision); both suggested replacements rejected as wrong for this environment** | Kimi's `import.meta.dirname` is precisely what the house pattern avoids — under jsdom `import.meta.url` is a non-file URL, which is why T05 (`:8-9`) and T01 use `testPath`; and `process.cwd()` is unreliable because, as T01's own comment says, the working directory depends on how the runner was started. The owner chose to keep the pattern and make failure loud: the AC7 case now resolves `contractPath`, reads it, and asserts the content `toContain('openapi:')`, with a comment explaining why the module-level computation stands. A moved file or a changed runner now fails there, not as a silent parse miss. |
| P7-1 / P8 corr. 1 | `installTransport` is now shared by T07 and T08 | **No change** | Confirmed acceptable by both reviews: one recorder inside one file beats duplicating it. T07's 29 cases were re-run green before any T08 case was written, and again after. |
| P7-2 / P8 corr. 2 | The `'<uncloneable>'` sentinel | **No change** | It is distinguishable from `''`, and AC4 is about the call count. |
| P7-3 / P8 corr. 3 | The drift guard fails on a POST removal too | **No change** | A removed operation is a contract change that should fail loudly; T03's drift check and `generatedClientCompiles` also cover it. |
| P7-4 / P8 corr. 4 | The contract parse is line-based | **No change** | T05's own parser; `auth.yaml` is flat and generated. Recorded for the day `contracts/` splits. |
| P7-5 / P8 corr. 5 | Ten POST operations are accounted for, not driven | **No change** | Owner decision 2 at phase 4. Each repeats a driven shape, and the boundary reads only `response.status`. |
| P7-6 / P8 corr. 6 | AC6's timer assertion runs unmounted | **No change; carried forward** | Inherited from T07, where the mounted hour-of-faked-time case already proves nothing clears or resends. |

## Verification after the changes

- The named test file: **47/47**. Whole suite: **145 passed, 1 failed (146)** — the pre-existing environmental
  `lintRejectsHandWrittenBackendFetch` AC4 case (this host's oxlint prints no summary line), unaffected by T08.
- `tsc --noEmit` exit 0. `oxlint` exit 0. `vite build` exit 0.
- **Mutations: 19/19 caught**, with one added for this phase:
  - **M19, the boundary rebuilding a protected request without its body** → **5 cases**, which is exactly the
    coverage P8-1 was asking for; before this phase's change it would have been caught by far fewer. (A
    body-dropping defect on a *public* POST is not expressible as a mutant of our code: the boundary returns early
    for public operations, so only openapi-fetch itself handles that body.)
  - M15 the boundary treating 429 like 401 → 11 cases (up from 7, the new `failures` assertions widen it);
    M16 the re-send sent twice → 6; M17 a timer-driven retry → 2; M18 `auth.yaml` gaining a POST → exactly 1, the
    drift guard. T07's 14 all still caught.
- `git status`: the T08 test file only. `contracts/api/auth.yaml` is byte-identical after the M18 run, and
  `sessionBoundary.ts` after M15/M16/M19.

## For phase 10 (on top of the frozen cases)
Nothing new. Phase 10 for T08 is the mutation and coverage pass, and the 19-mutant set above is its material; the
cases themselves were the phase 6 deliverable (recorded in `05-implementation-plan.md`).
