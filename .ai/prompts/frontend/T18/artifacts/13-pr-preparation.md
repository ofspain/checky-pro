# frontend · T18 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

Phase 12 returned **PASS**. Proceeding.

---

## Commit title

```
frontend T18: discard a callback whose state does not match the stored value
```

## Commit message

```
frontend T18: discard a callback whose state does not match the stored value

Compare the callback's state against the one value stored at authorize
time, and discard the arrival unless they are identical. Task 17 made a
MISSING state a discard; this makes a WRONG state one too, which is the
other half of R8 and the half that actually stops a cross-site request
forgery against the redirect endpoint.

The comparison is strict equality against the single sessionStorage item
under AUTHORIZE_STORAGE_KEYS.state. It is not trimmed, lowercased,
prefix-matched or length-compared on the stored side: a decoded callback
value that differs by one character is a different value. Task 17 trims
the SUPPLIED value because query padding is a client-side artefact, so
a percent-encoded trailing space still matches; the asymmetry is
deliberate and now pinned in both directions.

Read the item through a try/catch. There is no error boundary anywhere
in production source, so a browser that throws on blocked site data
would otherwise blank the screen instead of returning the visitor to
sign-in. A throw is treated as "nothing stored", which discards.

Leave all three stored items in place. Deleting them is task 23's, and
until then the same callback URL compares equal more than once. A test
asserts that and is written to fail the moment task 23 lands.

A mismatch is indistinguishable from a missing state: same neutral
copy, same path, same REPLACE, same empty query. The authorization
server's own error_description is asserted never to reach the DOM (L4).

Verified against the live stack in a real browser: the same
authorization code with one character of state changed produces the
opposite outcome.

17 mutants killed across phases 6, 9, 10 and 11. Two survive as
equivalent and are documented in the source; the coupling they defend
is covered by assertions on the property rather than the path.

Spec: task 18; R8; L4, L9, L11, L16.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

> **Deviation from the prompt template, recorded:** the template still prescribes a
> `Claude Opus 4.8 (1M context)` trailer. The session's standing attribution is
> `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`, which is what every T17 and T18
> commit carries. Carried forward as a template fix, gating nothing.

---

## Files changed

| File | Change | Lines |
|---|---|---|
| `frontend/src/auth/CallbackScreen.tsx` | the comparison, the guarded read, the corrected T17 comment | 114 total, **+50 / −17** net since T17 |
| `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | 71 new cases in 9 new blocks | 1,034 total, **+444** |
| `.ai/prompts/frontend/T18/artifacts/00…13` | the 14 phase artifacts | new |

**No other production file was touched.** No new module, no new route, no new catalogue string, no
dependency change, no spec file.

---

## Summary

Task 17 landed the callback screen and discarded an arrival with **no** `state`. R8 has a second half —
a `state` that is present but **wrong** — and that is the half that matters, because an attacker's forged
callback carries a perfectly well-formed `state`. T18 closes it.

The screen now reads the one item it stored at authorize time and continues only if the two are identical:

```ts
if (stored === null || stored === '' || stored !== state) { discard(); return; }
```

Four things about that line are deliberate and are the substance of the task.

**It is exact.** No trim on the stored side, no case folding, no prefix or length comparison, no `==`.
Six source-scan patterns and five mutants enforce it; a scan that could pass vacuously carries its own
negative control.

**It is bound to storage.** The single most valuable test in the task came from measuring rather than
reading: a comparison against the hardcoded literal `'xyz'` — the value every fixture happened to share —
**passed 125 of 126 cases**. The suite now runs the success path over two unrelated values and over a real
43-character token from the production generator, so the comparison can only be satisfied by the stored
value.

**It cannot blank the screen.** `sessionStorage.getItem` can throw, not merely return `null`. With no
error boundary in production source, an unguarded read would leave a blocked-site-data visitor on a white
page. The read is wrapped; a throw discards.

**It reads without consuming.** Deleting the three items belongs to task 23, so T18 plants a test that
asserts they survive and that a replayed URL still compares equal — a tripwire that fails by design when
task 23 lands.

The first two clauses of that condition are redundant today, given T17's empty-`state` guard. They were
measured as equivalent mutants and **kept anyway**, with a comment saying not to fold them in: they defend
a coupling across two tasks, and three cases assert the property so the coupling cannot break silently.

Outcomes are indistinguishable per L4. The mismatch discard is compared **byte-for-byte** with T17's
missing-state discard on rendered text, pathname, search string and history action, and an authorization
server's `error_description` is asserted never to render.

---

## Testing performed

| | |
|---|---|
| **Named test** | `callbackWithMismatchedStateIsDiscarded` — **134 cases, 134 passing** |
| **Whole suite** | **695 passed / 1 failed (696)** |
| **Types** | `tsc --noEmit` clean |
| **Lint** | `oxlint` clean |
| **Mutants** | **17 killed** across phases 6, 9, 10 and 11; 2 survive as equivalent, documented |
| **Browser** | verified on the live Docker stack through the Caddy origin |

**The one failure is environmental and pre-dates this task.** `lintRejectsHandWrittenBackendFetch` **AC4**
asserts `oxlint` prints `Found 0 warnings and 0 errors`; this host's `oxlint@1.87.0` prints no summary line,
so the assertion sees `''`. It is unrelated to T18 and unchanged since T07.

> Worth stating plainly, because it has been misread three times in review: **AC4 is not the `pnpm`
> failure.** AC2 of the same file fails with `spawn pnpm ENOENT` under a bare invocation and **passes**
> under `npx -y pnpm@12.14.0`, which is how the suite is run here. Kimi attributed AC4 to `PATH` in both
> T17 reviews and again in T18 phase 8.

Mutants of record:

| Mutant | Result |
|---|---|
| `stored !== state` → `stored !== 'xyz'` | **survived 125/126** before phase 9 — the finding of the task; now fails 24 |
| read `AUTHORIZE_STORAGE_KEYS.nonce` instead of `.state` | fails 13 |
| remove T17's supplied-value trim | fails 5 |
| `!==` → `!=` | fails |
| `===` → `.startsWith` / `.toLowerCase()` / length compare | each fails |
| remove the `try`/`catch` | fails |
| delete the stored items on success | fails 1 — the OQ-2 tripwire |
| move the read above the error check | fails — a spy records a throwing call |
| the two redundant clauses | **survive, equivalent**, documented in source |

Phase 11's two highest-severity gaps were **rejected by measuring the mutant they predicted** rather than
by argument. Both premises were false.

---

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` — task 18 |
| **Requirement** | **R8** — missing **or mismatched** `state` is discarded without completing the exchange, returning the visitor to the sign-in start with neutral copy. Task 17 implemented "missing"; **T18 completes R8.** |
| **LOCKED** | **L4** neutral copy · **L9** single origin · **L11** one `main`/`h1`/live region · **L16** the three stored items, read-only here |
| **Task decisions** | T18-D1…D11 (`artifacts/04-frozen-task-brief.md`). **D11 deviated**: it predicted one existing case would change; eight did, all success-path arrivals that now require URL and storage to agree. Recorded in phase 6 when it happened. |
| **Named tests** | `callbackWithMismatchedStateIsDiscarded` — **done**. `stateSurvivesRedirectAndIsConsumedOnce` is **task 23's**, deliberately not implemented; the generated header lists it for context (OQ-5). |
| **Contracts** | none touched — T18 makes no network call. |

---

## Merge readiness

- Branch `spec/service-specs-and-ai-framework`, off `main`; `main` untouched and deployable.
- Every phase committed **and pushed** separately, 14 commits.
- No spec file modified.
- Nothing in this task gates another. Task 19 (nonce) and task 20 (the real exchange) both build on the
  seam T17 left and T18 did not move.

---

## Open questions

**OQ-2** deletion is task 23's, pinned by a failing-by-design tripwire · **OQ-3** copy unreviewed, owner's ·
**Gap 6** record the expected suite total where CI can enforce it, and confirm the AC4 failure is identical
on CI. All **gate nothing**.

**Carried ·** T16's OQ-1 · T12's OQ-1 · `design.md` §4c's stale "PROPOSED, pending sign-off (Q6)" heading
and its claim that auth T19 is "not built" · `package.md:172` mapping `serviceWorkerUpdateWaitsForReload`
to R57 · the phase 13 template's stale model trailer.

---

## Verdict

**T18 COMPLETE — 14/14 phases, PASS.**
