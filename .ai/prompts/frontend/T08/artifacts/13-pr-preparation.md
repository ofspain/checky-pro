# frontend · T08 · Phase 13 — PR / Commit Preparation

Gate: the Phase 12 verdict is **PASS**.

Branch note: work lands on `spec/service-specs-and-ai-framework` and is pushed after each phase. For `main`, merge or
squash the branch's final tree; `main` stays deployable. Attribution: the repository's standing trailer (this
session's is `Claude Opus 5`), as in T01–T07; the prompt header's `Claude Opus 4.8 (1M context)` line is stale.

## Commit title
`frontend: prove a rate-limited POST is never replayed (T08)`

## Commit message
```
frontend: prove a rate-limited POST is never replayed (T08)

Closes the half of R23 that T07 left to this task. No production code:
the SPA already has no 429 retry anywhere - the notice middleware
exposes only onResponse and returns undefined, and the session
boundary returns early unless a response is 401 - so T08's work is to
pin that, and to pin the one re-send that is allowed.

21 cases in the existing rateLimitedResponseShowsGenericCopyWithoutReplay
(T07's 29 untouched), under describe('no POST replay'):

- nine POST operations driven end to end, covering every request shape
  auth.yaml declares: the five public ones, a JSON body, a sensitive
  body, no body, and a path parameter. Each asserts one transport call
  and the exact body sent, with and without the tab's notice.
- a renewal-capable session: a 429 neither renews nor re-sends.
- the boundary: 401 -> one renewal -> one re-send -> a 429 on that
  re-send ends the call at two transport calls, never three. R23's
  no-replay clause is 429-scoped (its WHEN is a 429, and agents.md puts
  the sentence inside 429 handling), so the 401-driven re-send stays;
  a failed renewal re-sends nothing either.
- a POST whose clone() throws still sends its original body once.
- the copy is identical whatever a 429's body and headers carry, a
  burst of three POSTs notifies once, no timer is created, an hour of
  faked time and a dismissal send nothing, and a marker body reaches
  the transport and nowhere else.
- a contract drift guard: every POST in auth.yaml is accounted for, so
  a new one cannot escape this file.

20 of 20 mutants caught, six written for this task.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed
- `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` (+449 / −1).

That is the whole footprint. **No production file, dependency, contract, spec, lint config, CI or earlier-task test
changed** (`git diff --stat` against T07's final commit shows one file).

## Summary
The seventh task proved what a rate-limited user *sees*; this one proves what the app does **not** do. If a write is
rate limited — creating an API key, changing a password, requesting a password reset — the app does not quietly send
it again. There is no retry, no backoff and no queue: the request stops, the fixed notice appears, and the next
attempt is the user's own. That matters most for writes, because a silent retry of a payment-shaped request is how
duplicates happen.

One repeat is allowed and is now written down precisely: if a request is rejected because the session token expired,
the app renews the session once and sends that request a second time — the server never processed the first attempt,
so nothing is duplicated. If the renewal fails, or if the second attempt is itself rate limited, the app stops there.

Nothing on screen changes, and no production code changed: the guarantee is the absence of code, and this task is
what keeps it absent.

## Testing performed
On the macOS host (the Docker daemon is down, so T08 was not re-run in `themistra-local-frontend-1`):
- the named test file: **50/50** (T07's 29 + T08's 21);
- whole frontend suite: **148 passed, 1 failed (149)**, three consecutive runs identical. The failure is
  pre-existing and environmental — `lintRejectsHandWrittenBackendFetch` AC4 expects oxlint to print
  `Found 0 warnings and 0 errors`, and this host's `oxlint@1.87.0` prints no summary line at all. It fails
  identically with T08 stashed, and its AC2 case needs a `pnpm` shim on `PATH`;
- `typecheck` exit 0, `lint` exit 0, `build` exit 0.

**Mutation breaks: 20, each caught** (applied to production code, then reverted with `git checkout --`; afterwards
`contracts/api/auth.yaml` and `sessionBoundary.ts` are byte-identical). T07's fourteen, plus six for this task:
the boundary treating 429 like 401 · the 401 re-send sent twice · a timer-driven retry after a 429 · `auth.yaml`
gaining a POST (caught by the drift guard alone) · the boundary rebuilding a protected request without its body ·
the boundary re-sending after a **failed** renewal.

**Two defects were found by this task's own process, not by a reviewer**, and both are recorded:
1. designing the "429 treated as 401" mutant showed every case used a client whose `renew()` rejects, which would
   have **hidden** a renewal attempt on a 429 — a renewal-capable case now closes it (phase 6 deviation 1);
2. the AC6 case spied on `globalThis.setTimeout` while fake timers were installed, so `afterEach` restored the
   **fake** `setTimeout` after the clock was uninstalled and every later real-timer wait in the file hung. Found when
   a new case timed out but passed in isolation; `vi.getTimerCount()` replaces the spies (phase 12).

## Carried forward
- **The first screen's wiring (task 16)** still owns making R23 visible: one notice per tab, passed to
  `createAuthApi` **and** rendered under the tab's `<I18nextProvider>`, with an end-to-end assertion. Unchanged from
  T07.
- **A dropped body on a public POST** is not expressible as a mutant of SPA code, so the body assertions in the
  table-driven and no-notice cases are the only guard. Worth remembering if that path ever changes.
- **The drift guard's parser is line-based** (T05's own): revisit if `contracts/` is split into multiple files or
  starts using path-item `$ref`s.
- **Ten POST operations are accounted for but not driven**, by owner decision at phase 4; each repeats a driven
  shape and the boundary reads only `response.status`.
- **One in-container run** when the Docker stack is back up, to match T01–T06's verification environment.
- **`design.md`'s "Not present in `auth.yaml`" list is stale** (it names the three MFA POSTs that `auth.yaml` now
  declares at `:163`, `:193`, `:214`). Recorded at phase 0; `spec/` is read-only for frontend tasks, and it will
  matter for the MFA screen tasks.

## Specification references
- **Task:** `spec/frontend/tasks.md` task 8, "No POST replay".
- **Requirement:** R23 (the no-replay clause; the copy clause is task 7).
- **Locked decisions:**
  - `agents.md` API access: the boundary owns 429 handling, with no automatic replay of non-idempotent POSTs;
  - `agents.md` errors: server text is never shown;
  - L6: generated client only;
  - L18 / R21: one renewal per tab, the single re-send part of that mechanism;
  - L12: T05–T07 behaviour and suites unchanged;
  - §5 / L16: nothing persisted, no retry queue;
  - L11 / O3: catalogue-only strings (T08 adds none);
  - Q12(b): detection by status alone;
  - **R23 read as 429-scoped** — owner decision, phase 4.
- **Named test:** `package.md` §8, `rateLimitedResponseShowsGenericCopyWithoutReplay` → R23 (shared with task 7).
