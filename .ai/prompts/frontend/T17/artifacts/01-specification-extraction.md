# frontend · T17 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## The scoped requirement

**R8** (`requirements.md:25-26`), verbatim:

> IF the callback `state` is missing or does not match, THEN the SPA SHALL discard the attempt, SHALL NOT complete
> the exchange, and SHALL return the visitor to the sign-in start with neutral copy.

Three obligations. T17 takes the **"missing"** antecedent; task 18 takes **"does not match"**. The three
consequents apply in full to T17's half:

1. **Discard the attempt** — the authorization code in the URL is not used, and nothing is carried forward.
2. **Do not complete the exchange** — the vacuity problem from phase 0; see below.
3. **Return the visitor to the sign-in start, with neutral copy** — the obligation phase 0 could not place, now
   placed: it is in R8, R8 is T17's scoped requirement, and no other task cites it. Tasks 28 and 29 cite **R10**
   and **R30**, which are *SAS* failures. A discarded callback is not a SAS failure — SAS did its job. **T17 owns
   the return and the copy.**

---

## Locked decisions in scope

| | Bearing on T17 |
|---|---|
| **L1** Public OIDC client with PKCE — "`state` and `nonce` validated" | The decision this task implements. `state` is the flow's CSRF defence; a callback without one has no proof it belongs to a request this tab made. |
| **L4** Enumeration-safe UI | The reason the copy must be *neutral*. The screen may not say whether the problem was a missing parameter, an expired attempt, or a forged link. |
| **L9** Single origin | The return is an in-SPA navigation to `/app/sign-in`, not an absolute address — which T01's scan enforces anyway. |
| **L11** Accessibility and i18n | A transient screen still needs a live region and catalogue-only strings. A visitor who is moved somewhere must be told, not silently relocated. |
| **L14** SPA route set under `/app` | `callback` is already declared public (`routes.tsx:9`). T17 attaches an element and adds no path. |
| **L16** The only `sessionStorage` carve-out | **Binding and sharp here.** The carve-out is exhaustive. T17 may not add a key — so whatever carries the "your attempt was discarded" fact to the sign-in screen may not be a new `sessionStorage` entry. |
| **L3** Tokens in memory only | Not yet reached — there is no token until task 20 — but it establishes the house pattern for in-memory state that T17's notice may follow. |
| **L12** Phase additivity | T17 extends; it does not rewrite T16's screen beyond what R8 requires of it. |

---

## The vacuity problem, stated as a requirement on the design

Phase 0 flagged it; phase 1 makes it binding.

**"SHALL NOT complete the exchange" cannot be tested today by observing absence**, because there is nothing to
observe. No module posts to `/oauth2/token`; task 20 builds it. A test asserting "no exchange occurred" would pass
against an empty file, against a screen that renders a single `<p>`, and — worst — it would keep passing after
task 20 lands and wires an exchange that fires unconditionally, because nothing in the assertion is connected to
the exchange's existence.

**AC4 below therefore requires a seam**: a named collaborator the callback calls on the success path, declared by
T17, left unimplemented by T17, and asserted *not called* on the missing-`state` path. "Not called" is then a fact
about a real function with a real call site, and task 20's job becomes filling it in rather than inventing the
call. A test can also prove the seam is reachable at all, by driving the path that does call it — which is what
stops the whole arrangement being decorative.

The precise shape is phase 2's. The requirement that there be one is fixed here.

---

## Acceptance criteria

- **AC1** — a callback at `/app/callback` **with no `state` parameter** is discarded: nothing is carried forward,
  no exchange collaborator is called, and the visitor ends at the sign-in start.
- **AC2** — the same holds when `state` is present but **empty** (`?state=&code=…`), which is "missing" in every
  sense that matters and is the shape a stripped query produces.
- **AC3** — the same holds when the callback carries **no query string at all** — a visitor who opens
  `/app/callback` directly or from a bookmark. (Phase 0's unknown 5, resolved: identical treatment, because a
  distinct message would disclose which case occurred, and L4 forbids that.)
- **AC4** — **the exchange seam is not called** on any of the above, *and* a positive case proves the seam is
  reachable, so the negative assertion is about a real collaborator. This is the non-vacuity requirement above.
- **AC5** — the visitor is returned to the **sign-in start** (`/app/sign-in`), by in-SPA navigation, replacing
  rather than pushing history — a discarded callback must not be reachable by the back button.
- **AC6** — **neutral copy** is shown, from the catalogue, saying the sign-in did not complete and inviting a
  retry. It names no cause, discloses no account state, and is identical for AC1, AC2 and AC3.
- **AC7** — **no `sessionStorage` key is added** (L16). Whatever carries the notice is not a new stored item.
- **AC8** — the stored items are **not consumed** by T17 — see OQ-2. Whatever the resolution, it is asserted
  explicitly rather than left to chance, so task 23 inherits a stated starting position.
- **AC9** — accessibility (L11): the end state has one `h1`, a `main` landmark, the notice in a live region, and
  no focus left on a destroyed element.
- **AC10** — catalogue-only copy, with the inverted scan T16 established (the component source contains none of
  the catalogue's values as literals) and the property guard (the copy names no credential type and discloses no
  account state).
- **AC11** — **T01, T02, T13 and T16 stay green.** T02's `SCREENS_ATTACHED_SO_FAR` is widened deliberately to
  include `callback`, with the predicate still shown to count.
- **AC12** — the circular-import guard still holds with a second strand added
  (`routes → CallbackScreen → authorizeRequest → routes`).
- **AC13** — no new dependency; measured suite delta; no file from T01–T16 modified beyond `routes.tsx`,
  `catalogue.ts`, T02's suite, and whatever AC5/AC6 require of T16's screen.
- **AC14** — non-vacuity sweep: every matcher fed a deliberately wrong input, per the standing rule.

---

## Open questions

**OQ-1 — how the neutral notice reaches the sign-in screen, given L16.**
R8 requires the visitor to arrive at the sign-in start *with* neutral copy, so something must cross the
navigation. The options, with the objection to each:

| Option | Objection |
|---|---|
| A new `sessionStorage` key | **Forbidden.** L16's carve-out is exhaustive and names four items; this is not one. |
| React Router location `state` | Lives in `history.state`, which the browser persists across reload. Arguably "stored client-side", which L16 closes. Not obviously forbidden — L16's text is about `sessionStorage` — but it is close enough to the line that I will not take it silently. |
| A module-scoped in-memory variable | Follows L3's house pattern, cannot be read by anything outside the tab, and is lost on reload — which is correct behaviour, since a refreshed sign-in page should not keep scolding the visitor. |
| The callback screen shows the copy itself and offers the sign-in action | Simplest, and touches no T16 file — but it does not "return the visitor to the sign-in start", so it does not satisfy R8 as written. |

**My recommendation is the in-memory variable.** It is the only option that satisfies R8 without either violating
L16 or arguing about what `history.state` counts as. Phase 2 will design it; recorded as an OQ because it
interprets L16's scope, and that interpretation will bind tasks 23–26.

**OQ-2 — does a discarded callback clear the stored `state`, `nonce` and `code_verifier`?**
R8 says "discard the attempt". L16 says the items are single-use and deleted at callback. **Task 23** owns
"deleted at callback". Two defensible readings:

- *T17 deletes them* — "discard the attempt" includes not leaving a reusable verifier behind, and an abandoned
  verifier sitting in storage until the next sign-in overwrites it is a security smell.
- *T17 leaves them* — deletion is task 23's by name, and a task that deletes what another task is specified to
  delete creates two owners for one behaviour.

**I lean to T17 leaving them and asserting that it does**, so the behaviour is pinned and visible rather than
accidental, and task 23 arrives to a stated position rather than a surprise. But this is a security-relevant
choice and the task list is genuinely ambiguous. **Flagged for the owner or for phase 3 to settle.** It does not
block: either answer is a small, well-tested change.

**OQ-3 — the neutral copy is unspecified, as T16's was.** Same situation and same handling: neutral strings go in
the catalogue, flagged for the owner, with the *properties* enforced by test (no cause named, no account state
disclosed, identical across all three discard shapes). Gates nothing.

**Carried, gating nothing:** T16's OQ-1 (the five sign-in strings are unreviewed); T12's OQ-1 (a refresh cannot
promote a waiting worker); `design.md` §4c's stale "PROPOSED, pending sign-off (Q6)" heading and its claim that
auth T19 is "not built" while L5 records it as built.

---

## Next

Phase 2 — implementation brief: the screen's shape, the exchange seam, how the notice crosses the navigation,
and the T02 widening.
