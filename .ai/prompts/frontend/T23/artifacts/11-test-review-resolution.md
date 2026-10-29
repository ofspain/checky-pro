# frontend · T23 · Phase 11 — Test Review Resolution

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 (resolving Kimi 2.7's review) |
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | this artifact; `stateSurvivesRedirectAndIsConsumedOnce.test.tsx` at **29 cases** |

**All six gaps accepted.** Four became test cases or assertions, one became a comment in the test file, one is a
recorded handoff. Each cited claim was checked against the committed source first; all six were accurate.

Result: **29 cases** (was 26), suite **1008/1009** by exit code, `tsc --noEmit` exit 0, and the harness re-run
gives **16 killed of 16** — M16 excluded as the documented survivor and re-measured separately, M17 added as the
mutant Gap 2's new net exists to catch.

---

## Resolution log

### Gap 1 · no direct test for a partially populated store — **ACCEPTED**

Verified: the isolation block covered a full store and an empty one, not a store holding two of the three.

**Added** `removes what is there when only some of the three are`: seeds `state` and `nonce` plus an unrelated
`ui.theme`, calls the function directly, asserts no throw, that only `ui.theme` survives, and — the part that
makes it more than a duplicate — that **all three removals were attempted**, including the key that was not
there. `removeItem` on an absent key is a no-op by specification, which is what makes the loop safe to run over a
store that has already lost one; the case says so rather than relying on it.

### Gap 2 · no source scan proves one production call site — **ACCEPTED, and the most valuable of the six**

Verified: the file read `authorizeRequest.ts` and `CallbackScreen.tsx` and counted one call in the screen. True
and insufficient, exactly as Kimi says: a second caller would most likely be a **new file**, which neither read
would ever see, and T23-D2's whole argument depends on no discard path anywhere calling this.

**Added** `is called from exactly one place in the package (AC5, T23-D2)`: walks every production `.ts`/`.tsx`
under `src/` with comments stripped, refuses an empty tree, and asserts the only two files naming
`consumeAuthorizeState` are its definition and the screen. A synthetic module proves the filter can report.

**Measured, not assumed, that the case is not inert.** A mutant adding a second caller inside
`clearAccessToken()` fails this case **by name** — alongside eight others, so it was worth checking which case
was doing the work:

```
× is called from exactly one place in the package (AC5, T23-D2)
Tests  9 failed | 20 passed (29)
```

### Gap 3 · the M16 survivor argument lived only in the manifest — **ACCEPTED**

Verified: the rationale was in `10-test-generation.md` and nowhere in the repository. A maintainer reading the
ordering block would find two cases guarding the exchange and the nonce check, and nothing saying why the
statement immediately above is deliberately unpinned.

**Added** a block comment at the head of `the order is not a coincidence`: the order between `retainAccessToken`
and `consumeAuthorizeState` is intentionally not asserted, both positions satisfy AC6, the harness measured no
observable difference (439 tests, 0 failed, exit 0 in both orders), and pinning it would be the structural
assertion AC6 was corrected away from. The boundaries that are real — the exchange and the nonce check — are named
with the number of cases each one's violation kills.

### Gap 4 · the replay case did not prove the missing `state` was the cause — **ACCEPTED**

Verified: the case asserted no network call and the discard route, which is the desired behaviour and not its
cause. Kimi's reading is right — "no network call" could be misread as "a replay always discards".

**Added** a control inside the same case: after the replay, the three are **re-seeded with the first attempt's
values** and the identical URL is driven again, and now `fetch` **is** called exactly once before the token
endpoint's refusal ends it on the discard route. So the absence above is the deleted state doing the work. It also
documents the pre-T23 behaviour in the file: the replay used to be exchanged and refused server-side
(`400 invalid_grant`, measured in T20).

### Gap 5 · deletion versus task 24's navigation and return target — **ACCEPTED as a handoff**

No test for T23, and nothing to change today. Recorded here as the sentence that goes with **R-I1**'s pin, which
is its enforcement:

> **Handoff to task 24.** `consumeAuthorizeState()` runs on the consumed path after the token is held and before
> anything navigates. Task 24 owns the post-login destination, so either its navigation happens **after** that
> call returns, or — if it reads a return target from storage — that read happens **before** the deletion point.
> Adding the return target to `AUTHORIZE_STORAGE_KEYS` enrols it in the deletion, which the key-set pin
> (`deletes the set this constant names, which is exactly three keys today`) now fails on, with a message that
> says what to decide. This is also **T23-D16**'s neighbour: task 24 should navigate away from `/app/callback` so
> the route is not reachable while a token is held.

### Gap 6 · the `FAULTS` table was missing a dropped connection — **ACCEPTED**

Verified: six rows, no `fetch`-throws row. It is a different fault from the refusal with the same outcome, and the
brief names five shapes, so a reader counting rows had no way to find it.

**Added** a seventh row whose `answer` throws `TypeError('Failed to fetch')` — listed rather than subsumed, with a
comment saying why it is there when the refusal row ends the same way. The row type already admitted it: a
function whose body only throws returns `never`, which is assignable to `Response`.

---

## Verification after the changes

| | |
|---|---|
| named test | **29 passed (29)**, exit 0 |
| whole suite | **1008 passed of 1009**, exit 1 — the one failure being the known environmental `oxlint`-summary case (`lintRejectsHandWrittenBackendFetch.test.ts:118`) |
| `tsc --noEmit` | exit **0** |
| mutation | **16 killed of 16** (baseline 442 tests, 0 failed, exit 0) |

Mutant deaths after the additions, for comparison with Phase 10's table: M1 18, M2 44, M3 36, M4 35, M5 49, M6 20,
M7 20, M8 13, M9 3, M10 3, M11 6, M12 7, M13 9, M14 7, M15 5, **M17 35**. M16 remains the single argued survivor
and is unchanged by this phase — its rationale now lives in the test file (Gap 3).

## What still cannot be proven here

Unchanged by this phase, and stated in the test file's header: no real full-page redirect (jsdom performs no
navigation, T23-D5); no browser-level reload, only a rebuilt module graph; and no store that denies *access*
rather than removal. A real-browser end-to-end check is owned by no task in this package.

## Next — Phase 12 — specification verification.
