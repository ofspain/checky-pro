# frontend · T25 · Phase 1 — Specification Extraction

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

> **Return target outside allowlist.** A return target off the allowlist falls back to the home route. Verifies
> `returnTargetOutsideAllowlistFallsBackToHome`. Cites R7, L1. — `spec/frontend/tasks.md:37`

T24 built the mechanism; this task is the other half of the spec's own split of the same named test. Extraction
only — no design.

**One measurement taken this phase changes what this task is about**, and it is recorded under Dependencies and
Open Questions rather than acted on: **react-router 8.4.0 `throws` on an external navigation**, and
`/home/../admin` resolves to the real authenticated route `/app/admin`. So the allowlist is not only an
open-redirect defence; it is also what keeps a thrown router error off the success path, and what keeps a
traversal from reaching a route the visitor was never sent to.

---

## Business Rules

| ID | Rule, one sentence | Source |
|---|---|---|
| **R7** | When the SAS login returns with a matching `state`, the SPA validates `nonce`, exchanges the code at `/oauth2/token` with the PKCE verifier, and holds the access token in memory only. | `requirements.md:23-24` |
| **R8** | If the callback `state` is missing or does not match, the SPA discards the attempt, does not complete the exchange, and returns the visitor to the sign-in start with neutral copy. | `requirements.md:25-26` — in scope only because a discard must not honour a target |

R7 is the requirement the named test is mapped to (`package.md:158`). The clause this task verifies is not in R7's
own sentence but in L1 and `agents.md:21`, which R7's "exchange … and hold the token" journey ends at.

## Locked Decisions

| ID | What it constrains here | Source |
|---|---|---|
| **L1** | Public OIDC client with PKCE; **"allowlisted post-login return target, falling back to the home route"**. `agents.md:21` states the same rule and adds the operative clause: **"never to an arbitrary URL."** This is the decision the task exists to verify. | `design.md:8-10`; `agents.md:19-22` |
| **L3** | Tokens in memory only — never written to storage, logs, or analytics. A rejection row must not become a way for a token to be observed, and the fallback must not log the refused value. | `design.md:13` |
| **L4** | Neutral copy; no outcome distinguishes one cause from another. **A refused target must be indistinguishable from no target** — no "your destination was rejected" anywhere. | `design.md` §4a |
| **L16** | The only sessionStorage carve-out: `state`, `nonce`, `code_verifier`, **and the post-login return target, single-use and deleted at callback**. A refused target is still an item of this carve-out and is still single-use. | `design.md:58-60` |
| **L9 / L10** | Single origin, and secrets stay out of the bundle. Relevant as a **boundary**, not a constraint on tests: the absolute-URL guard scans the built bundle, and test files are not in it (Phase 0 §5.4). Production source may still carry no scheme-qualified address. | `design.md:44-46` |
| **L11** | Accessibility and i18n on every screen, catalogue-only strings. A rejection adds no string, so there is nothing to translate — which is also how L4 is satisfied. | `design.md:47-48` |

## Files involved

### Existing, to extend

| File | What this task needs from it |
|---|---|
| `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | the named test. **15 `it.todo` rows at `:1055-1070` are this task's**, inside `describe('a target outside the allowlist falls back to home (T25)')`. The harness is already there: `arriveAt`, `startSignIn`, `seedRaw`, `signInAndLand`, `watchStorage`, `denyReadOf`, `denyRemovalOf`, `logReturnKeyOrder`, `deferredAnswer`, `EXPECTED_ALLOWLIST` |
| `frontend/src/auth/returnTarget.ts` | the module under test. `isAllowlistedTarget` (`:70-72`) is exported **for this task by name** (T24 F6), so the rejection table exercises the real predicate instead of rebuilding the allowlist beside it |

### Existing, to read only

`frontend/src/auth/CallbackScreen.tsx` (`:194-195`, the navigation) · `frontend/src/app/routes.tsx`
(`PUBLIC_ROUTE_PATHS` for the public-route row, `AUTHENTICATED_ROUTE_PATHS` and `HOME_PATH`).

### New files the spec expects

**None.** `package.md` §8 names one test for tasks 24 and 25 together and T24 created it. No new production module
is named by R7, L1 or `tasks.md:37`.

## Dependencies

| Kind | Item | Note |
|---|---|---|
| Module under test | `auth/returnTarget.ts` | `RETURN_TARGET_KEY`, `isAllowlistedTarget`, `takeReturnTarget`, `rememberReturnTarget` |
| Screen | `auth/CallbackScreen.tsx` | the one call site |
| Route data | `app/routes.tsx` | `APP_BASENAME`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`, `HOME_PATH` |
| Library | `react-router` 8.4.0 | **measured this phase**, and it matters: `router.navigate()` **throws `External navigation is not allowed`** for `https://evil.example/…`, `//evil.example/…`, `javascript:alert(1)` and `/\evil.example`; it **normalises** `/home/../admin` to `/app/admin` (a real authenticated route); and it carries `?x=1` and `#top` through onto `/app/home`. `window.location` was unchanged in every case under jsdom |
| Config keys | none | nothing new |
| Contracts | none exercised | `contracts/api/*` are untouched by a client-side destination decision |
| Catalogue | none added | L4 and L11 are satisfied by there being no new string |

## Acceptance Criteria

Mapped to requirement and decision IDs. Phase 2 will turn these into the brief's numbered AC list.

| # | Criterion | Maps to |
|---|---|---|
| 1 | Each of the eleven rejection families named in T24's `it.todo` rows lands the visitor on the home route, through the real flow. | L1, `agents.md:21`, R7 |
| 2 | Each family is refused by the **real exported predicate**, not by a list rebuilt in the test. | L1; T24 F6 |
| 3 | Every rejection row carries the control that an **honoured** target was reachable from the same harness, so the row cannot pass against a flow that navigates nowhere. | the house vacuity rule |
| 4 | A refused target is **indistinguishable** from no target at all: same pathname, same search, same rendered text, same history action. No copy names a cause. | **L4** |
| 5 | A refused target is still **consumed** — single-use applies to a target that was rejected, so it is not handed to the next sign-in. | **L16** |
| 6 | No discard honours a target, and a discard leaves a refused target alone exactly as it leaves an honoured one. | R8, T24-D14 |
| 7 | Nothing enters storage because of a rejection, and no token or code appears anywhere as a result. | **L3**, **L16** |
| 8 | A traversal target does not reach the route it would resolve to if it were navigated: the control is that the same string handed to `navigate()` reaches `/app/admin`, while through `takeReturnTarget()` it reaches `/app/home`. | L1 — "never to an arbitrary URL" |
| 9 | An external-navigation shape never reaches `navigate()`, so the router's throw never happens on the success path. | L1, **T24-D10** (totality) |
| 10 | A target carrying a query or fragment is not honoured, and the query does not travel. | T24-D6 |
| 11 | A parameterised route with a real id substituted is not honoured. | T24-D5 |
| 12 | No new catalogue string, no new dependency, no production change unless Phase 2 identifies one. | L10, L11, AC11's inherited shape |
| 13 | Suite green **by exit code** with the one known environmental failure; `tsc --noEmit` exit 0. | project convention |

## Tests required

**Named test (`package.md` §8 → `package.md:158` → R7):** `returnTargetOutsideAllowlistFallsBackToHome` —
**this task fills its T25 section.** The fifteen inherited `it.todo` rows, verbatim from `:1056-1070`:

absolute URL on another origin · absolute URL on this origin · protocol-relative host · `javascript:` target ·
`data:` target · backslash path · traversal · encoded traversal · prefix match of an allowlisted path · public
route · a route that does not exist · parameterised route with a real id substituted (T24-D5) · allowlisted path
carrying a query string (T24-D6) · allowlisted path carrying a fragment (T24-D6) · and the AC9 sweep over all of
them.

The other four named tests in the header are **inherited and must stay green**, not re-authored:
`callbackValidatesNonceAndKeepsAccessTokenInMemory`, `tokensAreNeverWrittenToAnyStorage`,
`callbackValidatesNonce` · `oidcRoundTripEndToEnd` has no suite and no owning task.

**Boundary tests implied, beyond the fifteen rows:**

- the **near-miss pair** for each prefix row — `/homeward` refused while `/home` is honoured, from one harness;
- the **traversal control** of AC8 above, which is the only row whose harm is demonstrable rather than asserted;
- a **mixed** shape (`/account/sessions?a=1#b`) — more than one reason to refuse, one answer;
- **case** (`/HOME`) and **whitespace-padded non-home** shapes, since membership neither folds case nor trims, and
  a padded *home* value cannot distinguish a trimming implementation (T24's measured trap);
- the **consumed-on-rejection** row per family, or once over the table, for L16;
- a rejection under a **throwing store** and under a **throwing removal**, so totality is not re-proven only on
  the honoured path.

## Open Questions

**No blockers.** `package.md` §11's open questions (Q1–Q3) are all closed or concern Phase 1b payments and
notifications; none touches R7 or L1.

Two items that are **not** blockers but that Phase 2 must decide explicitly:

1. **Is T25 verification-only?** Phase 0 measured all 21 shapes already refused, with no shape-specific code. If
   so, this task adds tests and no production code, and the brief should say so at the top rather than leave a
   reader wondering what was missed. **The counter-consideration Phase 2 must weigh:** react-router *throws* on
   the external shapes, so if the allowlist were ever loosened the failure mode is a blank page at the end of a
   successful sign-in, not a redirect — which is an argument for a test that pins the *reason* the throw cannot
   happen, not merely the destination.
2. **How far the AC9 sweep goes.** T24's row reads "every row above leaves the visitor with the same copy as an
   honoured target". Taken literally that is a full flow per family times a text comparison; Phase 2 should decide
   whether it is one sweep over the table or an assertion inside each row.

**One standing package gap, restated because it bounds this task's evidence:** `oidcRoundTripEndToEnd` has no
Playwright suite and no owning task, so no rejection family can be proven against a real browser. jsdom performs
no document navigation, and `window.location` was unchanged in every measurement above *because nothing can change
it here* — a row must not read that as proof that a real browser would refuse.

---

## Next — Phase 2 — task implementation brief.
