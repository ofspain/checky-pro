# frontend · T23 · Phase 10 — Test Generation

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx`, this manifest |

**`frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` — 738 lines, 26 cases, all passing.**
No production code changed in this phase. Suite **1005/1006** (the one failure being the known environmental
`oxlint`-summary case), `tsc --noEmit` exit 0, and **16/16 mutants accounted for: 15 killed, 1 survivor that is
argued rather than excused** (§4).

The file is built around one fact: the two halves of the task pull against each other. The three items must
survive long enough to be read, then not survive at all — and both mistakes that follow from getting the moment
wrong are **invisible in an end state**. A deletion one line too early and a deletion at the right time leave the
same empty store; a deletion on a failure path and no deletion at all leave the same held token. So most of the
file is about making the **order** and the **path** observable, not the end state.

---

## 1 · Case manifest

### `the three survive the journey` — AC1, AC9, T23-D5 (3 cases)

| Case | Verifies | How it avoids being vacuous |
|---|---|---|
| sends state and nonce to the server and keeps the verifier out of the URL | **AC1** | This is the *mechanism*, not a restatement: `state` and `nonce` are asserted present in the authorize URL (they come back from the server), the verifier is asserted **absent** from it, and the challenge is asserted present and different from the verifier. An implementation holding the three in a module variable passes no part of this. |
| reads all three back through a module graph that no longer shares an instance | **AC1**, T23-D5 | `vi.resetModules()`, then a fresh `authorizeRequest.ts`; the control asserts the new `AUTHORIZE_STORAGE_KEYS` is **not** the same object identity, so the graph really was rebuilt. The nearest observable thing to a reloaded document in an environment that performs no navigation. |
| puts the three in session storage and nothing in local storage | **AC9**, L16 | whole-store key equality, not three named reads |

### `a consumed callback takes all three with it` — AC2, AC5 (3 cases)

| Case | Verifies | Control |
|---|---|---|
| removes all three once the token is held | **AC2** | **two** controls: T23-D13's presence assertion immediately before the arrival, and the held token + non-discard route afterwards — a discarded callback would also end with the three gone under a buggy implementation, and then this case would be measuring the wrong path |
| removes exactly the three keys, once each, in the order they were written | **AC5** | the write order and the removal order are both read off a prototype watch installed **before** the flow |
| leaves a key that is not its own alone | **AC5**, **AC9** | `ui.theme` seeded first. The only case a `sessionStorage.clear()` implementation fails — as far as the three items go, `clear()` and three removals are indistinguishable |

### `and only once` — AC3, AC10, AC11 (3 cases)

| Case | Verifies | Note |
|---|---|---|
| refuses the same callback a second time, with no network call at all | **AC3** | `fetch` **never called** on the replay is the assertion that separates T23 from the pre-T23 behaviour, where the replay was exchanged and refused server-side. Also asserts the tab is left holding nothing, which is T23-D16's handoff stated rather than discovered |
| is indistinguishable from any other discard | **AC11**, L4 | full `outcomeOf` equality against a mismatched-state discard, with `en.signIn.discarded` asserted present first so two empty strings cannot pass |
| does not stop the next sign-in: a fresh attempt completes end to end | **AC10** | a **different** access token, and the second exchange's `code_verifier` asserted to be the second attempt's — so neither success can be a constant |

### `a discarded callback leaves all three where they are` — AC4, T23-D2 (6 rows + 1 case)

Six rows over the screen's five discard shapes: no state, a state that does not match, no code, an authorization
error, a refused exchange, a nonce that is not the stored one. Each asserts the discard route (the control), a null
holder, all three present **with their original values**, and `removeItem`/`clear` never called.

Then the case the decision exists for: **cannot be used to end a pending sign-in from outside.** A stranger's
arrival with a `state` of their choosing is discarded, the three survive, and the real callback for the pending
sign-in **still completes** with the right verifier. `/app/callback` is public, so a deleting discard would be a
denial of service on every sign-in in progress, triggered by a link.

### `when the store will not cooperate` — AC5, AC8, T23-D4 (2 cases)

| Case | Verifies | Note |
|---|---|---|
| still signs the visitor in, and leaves all three, when every removal throws | **AC8**, **AC5** | **R-I2**: three *attempts* are asserted, and that all three **threw** (`mock.results`), not merely that the outcome was unchanged — otherwise a flow that never tried to delete passes |
| removes the two it can when the store refuses exactly one | **AC5**, T23-D3 | the honest shape of "all or nothing": per-key guards mean a store refusing one key removes the other two. It also records the consequence — a surviving `state` can match again, so the token endpoint's own single-use remains the outer defence |

### `under StrictMode` — AC7, Kimi F4 (1 case)

One handling (`fetch` once), **one** deletion (three removals, not six), the final route **not** the discard
route, and the token held. T23's deletion is what makes the second pass observable for the first time: a second
pass that re-ran the handler would find an empty store and discard a sign-in that had already succeeded.

### `the order is not a coincidence` — AC6, Kimi F6 (2 cases)

| Case | Verifies | Mechanism |
|---|---|---|
| reads all three before it removes any of them | **AC6** | an ordered `getItem`/`removeItem` log through the prototype, calling through to the real methods. Asserts the last read index is below the first removal index, **with two controls**: all three reads are present (an empty log satisfies the ordering trivially) and a removal really happened |
| hands the exchange the verifier the authorize step stored | **AC6** | the serialized POST body's `code_verifier`. This is the case that makes the ordering behavioural rather than structural: a deletion moved above the exchange leaves this field empty and the sign-in fails outright |

### `consumeAuthorizeState on its own` — R-I9, R-I1, AC9 (5 cases)

Direct cases, no rendering harness: removes exactly its three keys (with `ui.theme` surviving); **idempotent** —
called twice, six attempts, three keys, no throw, no `clear`; **never throws** when the store refuses, leaving
what it could not remove; **R-I1's key-set pin**, whose failure message tells task 24 what to decide; and AC9's
last clause — the three literals exist in exactly one place, the function's own body contains no `themistra`, the
screen contains none, and there is exactly **one** call site.

---

## 2 · Acceptance criteria coverage

| AC | Where | Status |
|---|---|---|
| AC1 the three survive the journey | `the three survive the journey` ×3 | **covered**, with T23-D5's limit stated in the header |
| AC2 a consumed callback leaves none | `removes all three once the token is held`; T22's whole-store equality | **covered** |
| AC3 single-use by replay | `refuses the same callback a second time` | **covered** |
| AC4 no discard deletes | six rows + the public-route case; two inherited T18 cases | **covered** |
| AC5 one call site removes all three; a throwing store leaves all three | `removes exactly the three keys…`, both store-denial cases | **covered** |
| AC6 only after every consumer has read — behavioural | the ordering log + the `code_verifier` case; mutants M2, M3 | **covered** |
| AC7 StrictMode: one handling, one deletion, not the discard route | `handles once, deletes once…` | **covered** |
| AC8 a throwing removal leaves the outcome unchanged | `still signs the visitor in…` | **covered** |
| AC9 nothing else touched, no `clear`, no key hand-spelled | `leaves a key that is not its own alone`, `spells no storage key of its own`, T22's allowlist | **covered** |
| AC10 a new sign-in after a consumed callback | `does not stop the next sign-in` | **covered** |
| AC11 no new string, L4 copy identical | `is indistinguishable from any other discard` | **covered** |
| AC12 inherited assertions updated | Phase 6; six inverted, four deliberately unchanged | **covered** |
| AC13 suite green by exit code | 1005/1006, `tsc` 0 | **covered** |
| AC14 no new dependency; no behaviour change outside the two files | the diff; T23-D14 | **covered** |

Phase 9's four requirements: **R-I1** the key-set pin · **R-I2** three attempts, all throwing · **R-I5** all five
previously unverified ACs now have their own cases in this file · **R-I9** five direct cases. All landed.

## 3 · Mutation results — 15 killed of 16

Ordering and scope mutants, not leaks (the plan's own list, extended). The harness compares collected **totals and
exit code**, not pass counts, and scores an unparseable run as BROKEN rather than KILLED. Baseline: 439 tests, 0
failed, exit 0, over the named test plus the four files T23 touched.

| Mutant | Verdict | Failures |
|---|---|---|
| M1 the deletion never happens | KILLED | 18 |
| M2 the deletion moves above the exchange | KILLED | 43 |
| M3 the deletion moves above the nonce check | KILLED | 36 |
| M4 a discard deletes too | KILLED | 34 |
| M5 only a discard deletes | KILLED | 48 |
| M6 only two of the three keys are removed | KILLED | 19 |
| M7 only the first key is removed | KILLED | 19 |
| M8 the store is cleared instead of three removals | KILLED | 12 |
| M9 the per-key guard is dropped | KILLED | 3 |
| M10 one guard wraps the whole loop | KILLED | 3 |
| M11 the deletion runs twice | KILLED | 6 |
| M12 the removal order is reversed | KILLED | 6 |
| M13 a fourth key joins the constant | KILLED | 8 |
| M14 the deletion also removes a key that is not its own | KILLED | 6 |
| M15 the screen hand-spells the three keys | KILLED | 5 |
| **M16 the deletion moves one line earlier, above `retainAccessToken`** | **SURVIVED** | 0 |

**M13 is Kimi I1's finding as a mutant, and it dies** — adding `returnTo` to `AUTHORIZE_STORAGE_KEYS` now fails
eight cases instead of silently enrolling task 24's key in the deletion.

**M9 and M10 each kill with only three failures**, which is the two store-denial cases doing exactly the work they
were written for. Nothing else in the package can see the difference between one guard and three.

## 4 · The survivor, argued rather than excused

**M16 moves `consumeAuthorizeState()` from after `retainAccessToken(response.accessToken)` to before it.** Nothing
between those two statements reads the three items, and neither can throw in a way the other observes, so the two
orderings are **semantically identical**: 439 tests, 0 failed, exit 0 either way.

Killing it would mean asserting a statement order with no observable difference — a structural assertion of exactly
the kind the frozen brief rejected when it corrected AC6 from structural to behavioural (F6). AC6 requires the
deletion to happen *after every consumer has read*, and **both** positions satisfy that. The call site's comment
already explains why it cannot move **earlier than the exchange**, which is the boundary that matters and which M2
and M3 kill with 43 and 36 failures.

Recorded as a survivor rather than removed from the harness, because the next reader deserves to know the ordering
claim has a defined edge: it is about the exchange and the nonce check, not about the line above it.

## 5 · What this file still cannot prove

- **A real full-page redirect.** jsdom performs no navigation (T23-D5). The header says so in its own paragraph,
  and no task in this package owns a real-browser end-to-end check.
- **That `sessionStorage` really survives a browser-level reload.** The module-graph case is the nearest
  observable, and it is labelled as such rather than presented as the thing itself.
- **A store that denies *access* rather than removal.** `denyRemoval` throws from `removeItem`; a context where
  touching `globalThis.sessionStorage` itself throws is covered only by the production guard's shape, which Kimi
  I2 accepted and R-I2 answered as far as a jsdom store allows.

## 6 · Next

Phase 11 is Kimi's test review. The named test, the harness results and the survivor's argument are the material.
