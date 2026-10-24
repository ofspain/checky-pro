# frontend · T08 · Phase 12 — Specification Verification

Consumes `04-frozen-task-brief.md`, `09-review-resolution.md`, `10-test-generation.md` and `11-test-review.md`
(Kimi 2.7: 8 gaps). Date: 2026-10-24, macOS host (the Docker daemon is down; see T07 phase 9). **No production file
has been touched in the whole of T08** — `git status` shows only the shared test file.

## Phase 11 gaps (Kimi), resolved before verification

Line numbers refer to `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`.

| Gap | Decision | Change |
|---|---|---|
| 1 No copy-invariance case for a POST 429 | **ACCEPTED** | `:1042`: `POST /api-keys` gets a 429 with a `text/html` body naming "60 minutes" and `Retry-After: 3600`; the whole rendered notice must still equal the catalogue's two strings. |
| 2 No POST-only burst | **ACCEPTED** | `:1058`: three different POSTs (`/api-keys`, `/accounts/me/password`, `/accounts`) rate-limited concurrently → three transport calls, **one** notification, the copy shown. |
| 3 The no-notice loop doesn't assert the body | **ACCEPTED** | `:851` now asserts each recorded body equals the JSON its case sent. Kimi's reasoning is right and is the reason it matters: a dropped body on a *public* POST is not expressible as a mutant of our code, so the assertion is the only guard. |
| 4 The uncloneable case doesn't prove the original body was sent | **ACCEPTED** | `installTransport` now falls back to reading the **original** request when `clone()` throws (we are the send, so consuming it harms nothing), and `:928` asserts the body equals `JSON.stringify(NEW_API_KEY)` instead of a sentinel. A change that consumed the body before the send would leave the count at one and the server with nothing — now caught. |
| 5 No 401 → failed renewal → no re-send case for a POST | **ACCEPTED** | `:1083`: the POST's 401 comes back to the caller, `renew()` is called once and rejects, `failures` holds exactly that reason, one transport call, and no notice. **New mutant M20** was written for it. |
| 6 The drift guard doesn't handle a missing contract | **REJECTED** | Kimi itself marks it "not required". `readFile` already fails with `ENOENT` naming the exact path, which is clearer than a custom message, and the phase 9 assertion (`contract` must contain `openapi:`) already covers the "found but wrong file" case. Wrapping it would hide the real error. |
| 7 No POST 429 with an `AbortSignal` | **REJECTED, with the reason recorded** | The notice middleware only ever sees a response, and an aborted call throws before one exists. With a stubbed transport there is no interception point for a mid-flight abort unless the stub itself honours the signal — the case would be testing the stub, not production code. T05 already covers the one real interaction (`parallel401sTriggerExactlyOneRenewal.test.ts:403`, an abort while waiting on a renewal). |
| 8 No POST-vs-GET notice comparison | **REJECTED as redundant** | Kimi marks it "already implicit in cases 1–9"; every POST case asserts `isShown()` and the rendered copy. |

## A test defect found while adding gap 5, and fixed

The gap-5 case timed out, and in isolation it passed in 483 ms — so the cause was the suite's order, not the case.
**My own AC6 case (`:967`) was spying on `globalThis.setTimeout` while fake timers were installed.** `afterEach`
runs `vi.useRealTimers()` first and `vi.restoreAllMocks()` second, so the spy put the **fake** `setTimeout` back on
`globalThis` after the fake clock had been uninstalled: every later real-timer wait in the file — `vi.waitFor`,
`settle()` — hung forever. It was invisible until a case that waits on real timers ran after it.

The fix removes the spies: with a fake clock installed, `vi.getTimerCount()` is the direct assertion, and it needs
no spy. The case now asserts the count is 0 before and after the 429, and the comment records why spying there is
wrong. M4 and M17 are still caught (3 and 2 cases), so nothing was weakened. The suite was then run **three times in
a row**, with the same result each time.

## Mutation runs (20)

Each mutant was applied to the real file, the suite run, then reverted with `git checkout --`. **20 of 20 caught.**

| Mutant | Caught by |
|---|---|
| M1–M14 (T07's set, re-run on the shared file) | unchanged, all caught |
| M15 the boundary treats 429 like 401 | 13 cases |
| M16 the 401 re-send is sent twice | 6 |
| M17 a timer-driven retry after a 429 | 2 |
| M18 `auth.yaml` gains a POST | exactly 1 — the drift guard |
| M19 the boundary rebuilds a protected request without its body | 8 (was 5 before gaps 3 and 4) |
| M20 the boundary re-sends after a **failed** renewal | exactly 1 — the gap-5 case |

After the runs, `contracts/api/auth.yaml` and `sessionBoundary.ts` are byte-identical.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R23** a 429 on a non-idempotent POST is never replayed automatically | Yes, by construction | `rateLimitNotice.ts:40-43` (`onResponse` only, returns `undefined`); `sessionBoundary.ts:118` (returns early unless the status is 401) | `:808` (×5), `:828` (×4), `:851`, `:861`, `:1058` | No | No |
| **R23** nothing resumes on a timer or a later success | Yes | no timer in either path | `:967` (`getTimerCount()` 0, an hour of faked time, `dismiss()`) | No | No |
| **R23** resume only on user action | Yes | `rateLimitNotice.ts:50` | `:967`, `:1083` (the user's own resubmit is the only way on) | No | No |
| **R23 / Q12(b)** no dependence on the body or `Retry-After` | Yes | `rateLimitNotice.ts:41` | `:1042` | No | No |
| **AC1** one transport call per rate-limited POST, public and protected, with and without a notice | Yes | as above | `:808`, `:828`, `:851`, `:1058` | No | No |
| **AC2** each body sent exactly once, unchanged | Yes | `sessionBoundary.ts:101` (the clone is only for a 401 re-send) | `:808`, `:828`, `:851`, `:882`, `:928` | No | No |
| **AC3** the 401 re-send boundary: two calls, never three | Yes | `sessionBoundary.ts:118-129` | `:861`, `:882`, `:1083` | No | **Scope decision, not a deviation:** R23 is read as 429-scoped (phase 4, owner decision 1); the rationale is a comment at `:882` |
| **AC4** an uncloneable POST body | Yes | `sessionBoundary.ts:101-104` | `:928` | No | No |
| **AC5** the 429 path has only `onResponse`, returning `undefined` | Yes | `rateLimitNotice.ts:39-43` | `:949` | No | No |
| **AC6** no timer; nothing after dismissal or an hour | Yes | no timer created | `:967` | No | No |
| **AC7** every POST in `auth.yaml` accounted for | Yes (test-only guard, owner decision 2) | `contracts/api/auth.yaml`, 19 `post:` operations | `:993` | Ten are accounted for, not driven — by decision, each repeating a driven shape | No |
| **AC8** a POST body is never logged, stored or displayed | Yes | nothing in the path reads the body | `:1012` (a marker string) | No | No |
| **AC9 / L12** T05–T07 unchanged | Yes | no production file touched | `:1109`, `:1083`; T07's 29 cases unedited; full suite | No | No |
| **`agents.md` API access** the boundary owns 429 handling; screens add no retry | Yes | the middleware lives in `src/api`; no screen file exists yet | `:949` | No | No |
| **L6** generated client only | Yes | `client.ts:1`; `oxlint` green | T04's suite | No | No |
| **L18 / R21** one renewal per tab, the single re-send part of it | Yes | `sessionBoundary.ts:67-80` | `:861` (no renewal on a 429), `:882`, `:1083` | No | No |
| **§5 / L16** nothing persisted; no retry queue | Yes | one in-memory boolean | `:1012`, `:967` | No | No |
| **L11 / O3** catalogue-only strings | Yes, inherited | T07's catalogue | `:1042` | T08 adds no string | No |
| **tasks.md task 8** verifies `rateLimitedResponseShowsGenericCopyWithoutReplay` | Yes | the named test | 50 cases in the file (T07's 29 + T08's 21) | No | No |

## Verification run (2026-10-24, macOS host)

- The named test file: **50/50** (T08 now has 21 cases). Three consecutive whole-suite runs: **148 passed, 1 failed
  (149)** each time — the failure is the pre-existing environmental `lintRejectsHandWrittenBackendFetch` AC4 case
  (this host's `oxlint@1.87.0` prints no summary line), which fails identically with T08 stashed.
- `tsc --noEmit` exit 0. `oxlint` exit 0. `vite build` exit 0.
- Mutations: **20/20**.
- `git status`: the shared test file only.

## Principal engineer's answers

1. **Is the task fully complete?** Yes. T08's deliverable is the POST no-replay guarantee and its cases (phase 4,
   owner decision 2), and all nine acceptance criteria are covered, with the contract drift guard standing in for the
   operations that are not driven.
2. **Does it satisfy every acceptance criterion?** Yes — AC1–AC9 each have evidence and at least one case above, and
   each has a mutant that breaks it.
3. **Does it violate any LOCKED decision?** No. R23's reading is the owner's decision from phase 4, recorded in the
   brief and in the test file. L6, L11, L12, L18/R21, §5/L16, Q12(b) and the `agents.md` rules are traced above. No
   production, spec, contract or earlier-task file changed.
4. **Remaining risks.**
   - **The guarantee is only as live as the wiring.** Like T07, none of this is user-visible until the first screen
     mounts the notice and passes it to `createAuthApi` (task 16). The no-replay behaviour itself holds regardless,
     since it is the absence of code.
   - **A dropped body on a public POST** is not expressible as a mutant of our code (the boundary returns early for
     public operations), so `:808` and `:851`'s body assertions are the only guard there.
   - **The drift guard's parser is line-based** (T05's own), so a contract split into multiple files or a path-item
     `$ref` would need it revisited.
   - **Ten POST operations are accounted for, not driven** — by decision; each repeats a driven shape and the
     boundary reads only `response.status`.
   - **Not verified in Docker this phase** (daemon down). The behaviour is transport-stubbed, so the risk is
     environmental; one in-container run is worth doing when the stack is back.
   - **R42's edge limiter** may rate-limit any route with any body; detection is status-only, so that is covered by
     design.

**PASS** — every acceptance criterion and LOCKED decision for T08 is covered, traced and mutation-tested; a test
defect of my own making was found in this phase and fixed, and the suite is stable across three consecutive runs.
