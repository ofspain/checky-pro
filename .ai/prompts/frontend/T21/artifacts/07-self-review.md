# frontend · T21 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` and the code it describes |
| **Produces** | `artifacts/07-self-review.md` |

Seven findings. **F1 matters most: an acceptance criterion in the frozen brief is literally false as written**, and
Phase 10 would otherwise have to either fail it or write it vacuously.

---

### F1 · AC9 says the token is held once. It is not, and it cannot be — the built boundary keeps transient copies

AC9 reads: "The token is held **once**: no second copy in React state, a prop, a `globalThis` property, another
module's closure, or a thrown message." Taken literally, the implementation already violates it, and so would any
possible implementation:

- `sessionBoundary.ts:100` reads `tokens.current()` into `tokenUsed`, and `:108` stores it in a
  `Map` entry — `pending.set(id, { clone, tokenUsed, waitedOnFailedRenewal })`. That is the token string, held in
  another module's closure, for the lifetime of the request.
- `:104` clones the request, and `:89` has already put `Authorization: Bearer <token>` on it. The clone is a
  second copy again, kept in the same map so the request can be re-sent after a renewal.
- Both are deleted when the response arrives (`:114`) or errors (`:134`), so they are transient — but they exist.

None of this is avoidable by T21. `sessionBoundary.ts` is built, tested, and on this brief's **NOT to modify**
list, and the copies are the mechanism that makes "re-send once after renewal" possible at all. A token that
reaches an `Authorization` header has by definition been copied; AC5 *requires* that it does.

**Decision: AC9 needs the honest reading, and Phase 9 should record it rather than Phase 10 working around it.**
The criterion T21 can hold is: **T21 introduces no copy of its own, and holds no durable second copy anywhere.**
That is: one module variable; nothing in React state, no prop, no `globalThis` property, no module-level cache in
`src/auth`, and nothing in a thrown message. Transient per-request copies inside the client boundary are a built
mechanism outside this task, and task 22's sweep is the right place to state they reach no *storage*.

Writing the AC9 test against the literal wording would mean asserting something false; writing it to pass anyway
would mean asserting something hollow. This is the package's recurring defect waiting to happen, so it is flagged
before the test exists rather than after.

### F2 · Clearing on every discard turns a forged callback link into a forced sign-out

`clearAccessToken()` sits inside `discard` (`CallbackScreen.tsx:68-74`), which runs for all five failure paths. So
in a tab that is already signed in, opening `/app/callback?code=1&state=2` — a link an attacker can get a visitor
to follow, since the route is public — discards, clears, and **ends that tab's session**.

The alternative is worse, which is why the code stays as it is: not clearing leaves a tab holding a token from
sign-in #1 after sign-in #2 was discarded, which is the half-authenticated state R22 forbids, and AC3/AC10 require
the clear. Failing closed is the right default for credential material.

**Decision: no change; recorded as a known consequence.** It is an availability annoyance, not a compromise — the
visitor signs in again, and nothing is leaked. It belongs in the PR notes so nobody discovers it as a bug report.
Phase 10 pins it as deliberate behaviour, not as an accident.

### F3 · `retainAccessToken` and `adopt` each write `held` separately — three assignment sites for one variable

The file assigns `held` in three places: `adopt`, `retainAccessToken`, and `clearAccessToken`. Phase 6 flagged the
two-names decision as the file's one judgement call and asked Phase 7 to re-examine it. Re-examined: **the two
names are right, and the two writes are not.** `adopt` should delegate to `retainAccessToken`, leaving exactly one
place where a non-null token is assigned and one where it is cleared.

**Decision: accepted for Phase 9**, a one-line change. It is deliberately not made here, so Phase 8 can disagree
on the record — there is a real argument the other way, that a delegating `adopt` reads as though the cross-tab
path and the callback path differ in some way they do not.

### F4 · `accessToken.renew()` called directly, without a catch, is an unhandled rejection

The built boundary handles it everywhere it calls it — `attempt.catch(() => undefined)` at `sessionBoundary.ts:59`,
`try { await renew(); } catch` at `:120-124`, and `queueMicrotask` for the hook at `:72`. So nothing today can
produce an unhandled rejection, and the full-suite run confirms it (no `Errors` line). But `accessToken.renew()`
is exported on a public object, and the next caller is the task that fills the seam.

**Decision: Phase 10 asserts the rejection is observable and handled**, including one case that drives it through
the real boundary. No production change: a `renew()` that resolved or swallowed would be the actual defect.

### F5 · The seam's message names internal state, and a future task must not render it

`Silent re-authentication is not built; this tab cannot renew its session.` travels to `onRenewalFailed`, which
task 33 routes to clean re-auth. L4 forbids a visitor learning which step failed, so that string may never become
copy. It carries no token, no status and no server detail, which is what T21-D1 required of it.

**Decision: no change; recorded as a constraint on task 33.** The message exists for a developer reading a console
in a failing state, and it is the kind of thing that ends up in a toast if nobody writes down that it must not.

### F6 · Nothing calls `clearAccessToken` except the discard path

Sign-out must "clear in-memory token state" (`agents.md:35-36`), and task 33's clean re-auth implies an empty
holder. Neither task exists yet. So the export has exactly one caller today, and an unused export is usually a
smell.

**Decision: keep, and record the consumers.** It is not speculative API — `agents.md` names the behaviour, T21-D3
put the function here precisely because `LocalSession` cannot express it, and the alternative is that the
sign-out task edits `crossTabRenewal.ts` to widen `adopt`, which this brief forbids for good reason.

### F7 · The holder validates nothing, by design, and the one caller is what makes that safe

`retainAccessToken` accepts any string, including `''`. `codeExchange.ts:74-81` already refuses a response whose
`access_token` is missing, empty or not a string, so the only production caller cannot pass one. But `adopt` is
reachable from a `BroadcastChannel` message, and `crossTabRenewal.ts:54` checks `typeof message.token === 'string'
&& message.token !== ''` before adopting — so that path is guarded too, in the module that owns it.

**Decision: no change.** Both callers validate, each in the place that knows what valid means, and a third check
here would be a second opinion with no new information. Phase 10 records the two guards so the absence of a third
reads as a decision.

---

## What I checked and found sound

- **The ordering is real, not decorative.** Retention sits behind an early return after the nonce check; the
  suites that drive the real screen (T18's 134, T19's 123, T20's 69) all pass unchanged, so nothing about R8's
  ordering or T19's check moved.
- **Conformance is proven by use, not assertion.** The real holder, through the real `createSessionBoundary` and
  the real generated client, put `Authorization: Bearer TOKEN-ONE` on the wire; through the real
  `shareRenewalAcrossTabs`, `adopt` was reflected immediately.
- **The seam is a defined outcome.** On a 401 the caller kept its original 401 and `onRenewalFailed` ran exactly
  once with the fixed message — not a crash, not a retry loop.
- **Tasks 5 and 6 needed nothing.** `parallel401sTriggerExactlyOneRenewal` and `siblingTabsShareOneRenewal` build
  fakes of the interface T21 now implements and required no edit, which is the cheapest evidence that the real
  holder is shaped the way those tasks assumed.
- **No storage, no logs, no config.** The holder names no storage API, no `console.`, no `import.meta.env` and no
  absolute address; `oxlint` exits 0 and the module-level `let` trips no rule, confirming the ban T19 and T20
  enforce on their own files is a test-level scan and this file is correctly outside it.

## Open questions

**None new.** The frozen brief's §6 still records the two pieces of work the specification never assigned — silent
re-authentication and the composition root. F1 needs a decision at Phase 9, but it is a wording correction with a
determined outcome, not an open question.

---

## Next — Phase 8 — independent code review. **Kimi 2.7.**
