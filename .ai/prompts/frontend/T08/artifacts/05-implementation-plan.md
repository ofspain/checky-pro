# frontend · T08 · Phase 5 — Implementation Plan

Consumes `04-frozen-task-brief.md`. Date: 2026-10-24. Runs on the macOS host (`frontend/`), not in
`themistra-local-frontend-1` — the Docker daemon is down; see the T07 phase 9/12 note for the host workaround.

**Phase split.** T08's deliverable *is* test code (owner decision 2: no production change), so phase 6 writes the
cases and phase 10 becomes the mutation and coverage pass — the same division of labour phase 10 did for T07, where
it ran the mutants. Nothing is left for phase 6 to "implement" otherwise.

## Steps

1. **Scaffolding in the existing file** (`rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`):
   - add `node:fs/promises` + `node:path` imports and the `repoRoot` constant, copying T05's technique
     (`parallel401sTriggerExactlyOneRenewal.test.ts:9`, `:513`), for the AC7 drift guard;
   - extend `Sent` with `body` and record it in `installTransport`, so a replayed **body** is visible and not just a
     call count (AC2). The read is wrapped in `try/catch` because the AC4 case makes `clone()` throw; it records
     `'<uncloneable>'` there.
   - **Check immediately** that T07's 29 cases still pass before adding anything.
2. **Nested `describe('no POST replay', …)`** (finding 6), inside the existing describe so the named test string is
   unchanged, holding:
   - `PUBLIC_POSTS` (5) and `PROTECTED_POSTS` (4: JSON body, sensitive body, no body, path parameter) as `it.each`
     tables;
   - `EXERCISED_POST_PATHS` and `UNEXERCISED_POST_PATHS`, the two halves of the AC7 guard, the second with a comment
     saying why those ten are accounted for rather than driven.
3. **The cases**, in the frozen brief's order: AC1/AC2 public ×5 · AC1/AC2 protected ×4 · AC1 with no notice ·
   AC3 the 401 re-send boundary, with the R23-scope rationale as a comment · AC4 `clone()` throwing · AC5 the
   middleware's own shape · AC6 no timer and no resumption · AC7 the drift guard · AC8 the marker body · AC9 T05
   parity.
4. **Mutation runs** (phase 10 proper, prepared here): T07's 14 plus four for T08 — the boundary treating 429 like
   401; the 401 re-send sent twice; a timer-driven retry after a 429; and `auth.yaml` gaining a POST, which must fail
   the drift guard and nothing else.
5. **Verification:** `typecheck`, `lint`, the file alone, the whole suite, `build`, and `git status` showing only the
   test file.

## Risks
- **Typed calls under strict TS.** The no-body operation (`POST /accounts/me/mfa/totp`) and the path-parameter one
  (`POST /admin/accounts/{accountUuid}/activate`) have different call shapes; if `api.POST(path)` without an init
  argument fails `typecheck`, pass `{}` rather than casting.
- **Touching a shared helper.** `installTransport` is used by T07's cases. The change only adds a field, and step 1
  re-runs T07's cases before anything else is written; if any of them depended on the record's shape, stop and record
  it instead of editing a T07 case.
- **A mutant that cannot be caught.** The boundary mutant only shows itself if a renewal *would* succeed, and every
  T08 case so far uses a client whose `renew()` rejects. If so, the fix is a new case (a renewal-capable session with
  a 429) rather than dropping the mutant.
- **Contract mutation.** The drift-guard mutant edits `contracts/api/auth.yaml`, which is read-only for this task. It
  is applied and reverted by the runner (`git checkout --`), and `git status` is checked afterwards.

## Tests
This task is the tests. No production file is created or modified.
