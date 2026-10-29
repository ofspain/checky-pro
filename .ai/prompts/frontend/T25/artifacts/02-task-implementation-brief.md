# frontend · T25 · Phase 2 — Task Implementation Brief

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Fill the T25 section of `returnTargetOutsideAllowlistFallsBackToHome` — the fifteen `it.todo` rows T24 left at
`returnTargetOutsideAllowlistFallsBackToHome.test.tsx:1055-1070` — so that `agents.md:21`'s clause, **"never to an
arbitrary URL"**, is verified family by family. `tasks.md:37`. Cites R7, L1.

## Purpose

The spec splits one named test across two tasks: `tasks.md:36` is "a return target **on** the allowlist is
honoured" and `:37` is "**off** the allowlist falls back to the home route". T24 built the mechanism — set
membership over paths derived from the route table — and proved the honoured path. What is still unproven is the
clause that makes this a security control rather than a convenience: that an absolute URL, a protocol-relative
host, a `javascript:` target, a traversal, a prefix match and seven other shapes are all refused.

**This is expected to be a verification-only task**, and the brief says so at the top so that no reviewer spends
time looking for the missing production code. Phase 0 measured all twenty-one shapes already refused by the real
predicate, with no shape-specific code anywhere. If Phase 6 finds a shape that is *not* refused, that is a
behaviour defect and the brief's scope expands to fixing it; nothing in this brief assumes it will.

Two measured facts give the task more weight than "write eleven more rows":

- **react-router 8.4.0 throws `External navigation is not allowed`** for `https://evil.example/…`,
  `//evil.example/…`, `javascript:alert(1)` and `/\evil.example`. `CallbackScreen` calls `takeReturnTarget()`
  inside `.then(onFulfilled, discard)`, where a synchronous throw is **not** caught by the second argument, so a
  loosened allowlist does not produce a redirect — it produces a blank page at the end of a successful sign-in.
- **`/home/../admin` resolves to `/app/admin`**, a real authenticated route. The traversal family is therefore the
  one whose harm is demonstrable rather than asserted.

## Scope

**In.** The fifteen `it.todo` rows, as cases. The boundary rows Phase 1 implied: near-miss pairs, the traversal
control, a mixed shape, case and whitespace shapes, consumption on rejection, and rejection under a throwing store
and a throwing removal. A mutation harness whose mutants are the **membership rule** and the **fallback**.

**Out.** Any change to `returnTarget.ts`, `CallbackScreen.tsx` or `routes.tsx` **unless Phase 6 finds a shape that
is not refused** — and then only the narrowest change that refuses it. Normalisation, decoding, pattern matching
and prefix handling of any kind (T24-D2, T24-D8). Honouring parameterised routes (T24-D5) or queries and fragments
(T24-D6). A home screen (T24-D11). The producer (task 41). The two-constant storage carve-out guard (task 26). The
real-browser round trip (`oidcRoundTripEndToEnd`, unowned).

## Business Rules

- **R7** — on a matching `state`, validate `nonce`, exchange the code with the PKCE verifier, hold the token in
  memory only; this task verifies where that journey ends when the remembered destination is not admissible.
- **R8** — a discard returns the visitor to sign-in; no discard may honour a target, refused or otherwise.

## Locked Decisions

- **L1** (`design.md:8`) — allowlisted post-login return target, falling back to the home route; `agents.md:21`
  adds **"never to an arbitrary URL"**. The decision under verification.
- **L3** (`design.md:13`) — tokens in memory only; a rejection must not surface or store a token, and the refused
  value must not be logged.
- **L4** — neutral copy; **a refused target must be indistinguishable from no target**, with no copy naming a
  cause.
- **L16** (`design.md:58`) — the sessionStorage carve-out; the return target is single-use and deleted at
  callback, **including when it is refused**.
- **L9 / L10** (`design.md:44-46`) — single origin, secrets out of the bundle. A boundary, not a constraint on
  tests: the absolute-URL guard scans the **built bundle**, and test files are not in it, so rejection rows may
  use real scheme-qualified URLs; production source may not.
- **L11** — catalogue-only strings; a rejection adds none, which is also how L4 holds.

## Dependencies

`auth/returnTarget.ts` — `RETURN_TARGET_KEY` (`:39`), **`isAllowlistedTarget` (`:70-72`, exported for this task
by name, T24 F6)**, `takeReturnTarget` (`:120`), `rememberReturnTarget` (`:88`) · `auth/CallbackScreen.tsx:194-195`
(the one call site) · `app/routes.tsx` — `APP_BASENAME`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`,
`HOME_PATH` · `react-router` 8.4.0, whose measured behaviour is in Purpose · the named test's existing harness:
`arriveAt`, `startSignIn`, `seedRaw`, `signInAndLand`, `watchStorage`, `removedKeys`/`writtenKeys`/`storedKeys`,
`denyReadOf`, `denyRemovalOf`, `logReturnKeyOrder`, `deferredAnswer`, `EXPECTED_ALLOWLIST`.

No new dependency, no config key, no contract, no catalogue entry.

## Inputs

A string in `sessionStorage` under `RETURN_TARGET_KEY`, placed there by `seedRaw` because the producer now filters
on write (T24-D15) and would refuse every one of these shapes — which is itself one of the facts to assert, not a
workaround.

## Outputs

A destination: `/app/home` for every family. `takeReturnTarget()` returns a path, never an option (T24-D10).

## State Changes

None in production. In the store: the refused value is **removed** — read before delete, by construction — which
is L16 applied to a rejection.

## Files to Create

**None.**

## Files to Modify

`frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` — the T25 `describe` block at
`:1055-1070`: fifteen `it.todo` rows become cases, plus the boundary rows above. Harness additions only if a row
genuinely needs one.

**Conditional, and only on a Phase 6 finding:** `frontend/src/auth/returnTarget.ts`, if and only if a measured
shape is not refused. Any such change is a declared deviation with its own decision ID.

## Files NOT to Modify

`spec/**` · `auth/CallbackScreen.tsx` · `app/routes.tsx` · `auth/authorizeRequest.ts` (T24-D4) ·
`auth/codeExchange.ts`, `auth/idTokenNonce.ts` (pure readers, T23-D1) · `auth/accessToken.ts` · `api/*` · `pwa/*` ·
`.oxlintrc.json` (T22-D12) · `src/i18n/*` · `package.json` · the T24 cases in the same file (they are T24's
evidence; a row that needs one changed is a finding, not an edit).

## Acceptance Criteria

**AC1** each of the eleven families lands the visitor on `/app/home` through the real flow · **AC2** each family
is refused by the real exported predicate, not a list rebuilt beside it · **AC3** every rejection row carries the
control that an honoured target was reachable from the same harness · **AC4** a refused target is
indistinguishable from no target — pathname, search, rendered text and history action all equal (**L4**) ·
**AC5** a refused target is consumed, so it is not handed to the next sign-in (**L16**) · **AC6** no discard
honours a target, and a discard leaves a refused target where it is (R8, T24-D14) · **AC7** nothing enters
storage because of a rejection, and no token or code appears as a result (**L3**, **L16**) · **AC8** the traversal
control: the same string handed to `navigate()` reaches `/app/admin`, while through `takeReturnTarget()` it reaches
`/app/home` · **AC9** an external-navigation shape never reaches `navigate()`, so the router's throw never happens
on the success path (**T24-D10**) · **AC10** a query or fragment is not honoured and the query does not travel
(T24-D6) · **AC11** a parameterised route with a real id is not honoured (T24-D5) · **AC12** the producer refuses
every one of these shapes on write, so none can be stored by a caller (T24-D15) · **AC13** no new catalogue
string, no new dependency, and no production change unless a shape is found unrefused · **AC14** suite green
**by exit code** with the one known environmental failure; `tsc --noEmit` exit 0 · **AC15** the T24 cases in the
same file pass **unchanged**.

## Required Tests

Named test `returnTargetOutsideAllowlistFallsBackToHome` (`package.md:158` → R7), T25's section.

**The eleven families, one row each** (the `it.todo` names are the spec of the table and should survive as case
names): absolute URL on another origin; absolute URL on this origin; protocol-relative host; `javascript:`;
`data:`; backslash path; traversal; encoded traversal; prefix match of an allowlisted path; public route; a route
that does not exist; a parameterised route with a real id; a query string; a fragment — followed by the AC4 sweep.

**Each row asserts four things, because the destination alone is weak:** the pathname is `/app/home`; the sign-in
nonetheless **completed** (the token is held, so the row is not passing against a discard); the refused value is
**gone** from the store; and the predicate itself refuses it.

**The controls, because the house risk is a row that passes against a flow that navigates nowhere:**

- every row runs in a harness that has just been shown to honour `/account/sessions`, or asserts it alongside;
- the **near-miss pairs** — `/homeward` refused while `/home` is honoured; `/accou` refused while `/account` is
  honoured — which is what makes "membership, not prefix" observable;
- the **traversal control** of AC8, the only family whose harm is demonstrable;
- a **mixed** shape with more than one reason to be refused, yielding one answer;
- **case** (`/HOME`) and **whitespace-padded non-home** shapes — a padded *home* value cannot distinguish a
  trimming implementation, because the trimmed value is the fallback (T24's measured trap).

**A mutation harness over the membership rule and the fallback**, including at least: prefix test in each
direction, substring match, case-folding, trimming, `decodeURIComponent` before the check, a leading-`/` strip, a
basename strip, returning the stored value unvalidated, and returning `null` instead of home. **Tests committed
and pushed before it runs** — it reverts with `git checkout`.

## Constraints

**Security.** The clause is "never to an **arbitrary** URL", so no row may be satisfied by a mechanism that
happens to refuse it; AC2 ties every row to the exported predicate. No normalisation may be introduced to make a
row pass — a normalising fix is a design change and would need a new decision at a gate.

**Module boundaries.** One file is edited. `returnTarget.ts` is read, not changed, unless a shape is found
unrefused.

**Null handling.** `takeReturnTarget()` is total (T24-D10) and returns a path for every input; a row must assert
the returned value, not merely the absence of a throw.

**Performance / thread-safety / transactions.** Not applicable — no persistence, no concurrency beyond React's
effect, no transaction.

**Evidence boundary.** jsdom performs no document navigation, so `window.location` cannot change here. No row may
present an unchanged `window.location` as proof that a real browser would refuse; `oidcRoundTripEndToEnd` is
unowned and that gap is the package's, not this task's.

## Open Questions

**No blockers.** `package.md` §11's Q1–Q3 are closed or concern Phase 1b.

One item for the Phase 4 gate, carried from Phase 1 and not a blocker: **how far AC4's sweep goes** — one
comparison over the whole table, or an assertion inside every row. The literal reading of T24's row ("every row
above leaves the visitor with the same copy as an honoured target") is a full flow per family plus a text
comparison; the gate should pick one and say why.
