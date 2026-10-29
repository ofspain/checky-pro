# frontend · T25 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Human Approval gate |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

Every claim in Kimi's challenge was checked against source, and **F4's two shapes were measured rather than taken
on trust**. **All eight findings accepted**, one of them (**F4**) **upgraded from Low to High**, because the
measurement says its two rows pin the two most dangerous plausible refactors in this module rather than being
coverage polish. Three measurements the challenge did not have are added below, and one open question from Phase 2
is closed by F5.

---

## 1 · Phase 3 disposition

| Finding | Decision |
|---|---|
| **F1** AC9 is only tested indirectly | **Accepted.** A measurement recorded in an artifact is not a test. The counterfactual becomes a committed control: a fresh router, `navigate(shape)`, and the throw asserted. **T25-D1.** |
| **F2** AC12 is not in the row requirements | **Accepted.** `rememberReturnTarget(shape)` + "nothing stored" joins every row. Kimi's reason is the good one: it guards against a refactor that decouples the producer's filter from `isAllowlistedTarget`. Measured for all twelve probe shapes: the producer stored **nothing** for any of them. |
| **F3** no discard case covers a refused target | **Accepted.** One row. Its content is narrow and worth stating: the discard path never inspects the value, and this is what pins that. |
| **F4** trailing slash and encoded separator are missing | **Accepted and UPGRADED to High.** See §2 — react-router matches **case-insensitively** and **ignores a trailing slash**, so `/home/` and `/HOME` are routes the router would honour. These are not strengtheners; they are the two rows that make a case-folding or slash-stripping "fix" fail. |
| **F5** AC4 per row, not one sweep | **Accepted**, and this **closes Phase 2's open question**. Kimi's reason is decisive: a single sampled sweep passes if the sampled family happens to match. **T25-D2** fixes the mechanics. |
| **F6** the traversal control should prove harm first | **Accepted.** Same shape as F1: the measurement becomes a control. `/home/../admin` → `/app/admin` is asserted, then refused through the real path. |
| **F7** a mixed shape should be named | **Accepted**, with Kimi's own string: `https://evil.example/../admin?x=1#fragment`. Measured: the predicate refuses it and the router **throws** on it. |
| **F8** `historyAction` is `REPLACE` per rejection row | **Accepted**, folded into **T25-D2** — it is one of AC4's four compared fields. |

**Rejected: none.**

## 2 · Measurements this gate added (react-router 8.4.0, jsdom)

Taken with `router.navigate(shape, { replace: true })` on the real `appRoutes` under `basename: '/app'`, from
`/app/callback`. The predicate and producer columns are the real `isAllowlistedTarget` and `rememberReturnTarget`.

| Shape | Predicate | Producer stored | Router |
|---|---|---|---|
| `/home/` | refused | nothing | **`/app/home/` — MATCHES the `home` route** |
| `/HOME`, `/Home` | refused | nothing | **`/app/HOME` — MATCHES the `home` route (matching is case-insensitive)** |
| `/account/sessions/` | refused | nothing | `/app/account/sessions/` — matches `account/sessions` |
| `/account%2Fsessions`, `/home%2F` | refused | nothing | catch-all (`*`) — the encoded separator is not a separator |
| `https://evil.example/../admin?x=1#fragment` | refused | nothing | **THREW** `External navigation is not allowed` |
| `  /account/sessions` (leading spaces) | refused | nothing | **`/app/callback/  /account/sessions` — resolved RELATIVE, then catch-all** |
| `/home/..` | refused | nothing | `/app` — the basename root, no leaf |
| `home`, `./home` | refused | nothing | `/app/callback/home` — relative, then catch-all |
| `/app/home` | refused | nothing | `/app/app/home` — catch-all |

**What these change.**

1. **Case-folding and trailing-slash are the two refactors that would actually cause harm.** The router honours
   `/HOME` and `/home/` as the `home` route, so a membership check that folded case or stripped a trailing slash
   would not merely admit a stray string — it would navigate. Every other family is refused twice over (predicate
   *and* router). These two are refused **once**, by the predicate alone. That is why F4 is High.
2. **Trimming has a demonstrable harm now.** A leading-space value resolves *relative* to the current route and
   lands on the catch-all; trim it and it becomes an absolute path the router honours. T24 already found that a
   padded *home* value cannot distinguish a trimming implementation; this is the shape that can.
3. **T24-D8 is confirmed by the router's own behaviour.** `/app/home` would resolve to `/app/app/home` — so the
   basename-carrying form is not merely "not a member", it is a value that could never have worked.

## 3 · Frozen decisions

| ID | Decision |
|---|---|
| **T25-D1** | *(F1, F6)* **Every family whose harm is demonstrable gets a counterfactual control**, in a case of its own, using a fresh `createMemoryRouter` and `router.navigate(shape)`: the external shapes **throw**, the traversal reaches `/app/admin`, `/home/` and `/HOME` reach the `home` route. Then the same shape goes through the real flow and lands on `/app/home`. Harm first, defence second — otherwise the row asserts that nothing happened, which is also what a broken test asserts. |
| **T25-D2** | *(F5, F8)* **AC4 is asserted inside every rejection row**, over four fields: `pathname`, `search`, rendered text, and `historyAction`. Each row therefore runs **three flows** from the same harness — the refused target, a no-target baseline, and an honoured target — and compares the first two while requiring the third to differ in pathname. A baseline captured once in another case is forbidden: T23 was bitten by order-dependent state between cases. If three flows per row proves slow, Phase 6 **records the measurement** and brings it back here; it does not quietly drop a flow. |
| **T25-D3** | *(F2)* **Every row also asserts the producer.** `rememberReturnTarget(shape)` stores nothing, and the control that the same helper stores an allowlisted target. One rule, checked at both ends (T24-D15), now per family. |
| **T25-D4** | *(F4, measured)* **`/home/` and `/HOME` are first-class rows, not variants.** They are the only families the router would honour, so they are the only ones where the predicate is the sole defence. Each is paired with its honoured near-miss (`/home`) in the same case. |
| **T25-D5** | **No normalisation may be introduced to make a row pass.** If Phase 6 finds a shape that is *not* refused, the narrowest fix that refuses it is in scope, and it is a declared deviation with its own decision ID recorded at the Phase 9 gate — never a quiet edit, and never a normalisation pass (T24-D2, T24-D8). |
| **T25-D6** | **jsdom cannot prove a browser refuses anything.** `window.location` is unchanged in every measurement because nothing here can change it. No row may assert an unchanged `window.location` as evidence of refusal; the counterfactual controls of T25-D1 are the honest substitute, and the real gap belongs to `oidcRoundTripEndToEnd`, which no task owns. |

## 4 · Scope — final

**In.** The fifteen `it.todo` rows at `returnTargetOutsideAllowlistFallsBackToHome.test.tsx:1055-1070`, as cases.
F4's two rows (`/home/`, `/account%2Fsessions`) and T25-D4's `/HOME`. F7's mixed row. F3's refused-target discard
row. The counterfactual controls of T25-D1. Per-row AC4 and AC12 (T25-D2, T25-D3). A mutation harness over the
membership rule and the fallback.

**Out.** Everything in Phase 2's Out list, unchanged: any production edit absent a Phase 6 finding (T25-D5);
normalisation, decoding, pattern matching, prefix handling; parameterised routes (T24-D5); queries and fragments
(T24-D6); a home screen (T24-D11); the producer's caller (task 41); the two-constant carve-out guard (task 26);
the real-browser round trip.

## 5 · Files — final

**Create:** none.

**Modify:** `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` — the T25 `describe` block
only, plus harness helpers if a row needs one.

**Conditional (T25-D5), only on a Phase 6 finding:** `frontend/src/auth/returnTarget.ts`.

**NOT to modify:** `spec/**` · `auth/CallbackScreen.tsx` · `app/routes.tsx` · `auth/authorizeRequest.ts`
(T24-D4) · `auth/codeExchange.ts`, `auth/idTokenNonce.ts` (T23-D1) · `auth/accessToken.ts` · `api/*` · `pwa/*` ·
`.oxlintrc.json` (T22-D12) · `src/i18n/*` · `package.json` · **the T24 cases in the same file** — a row that needs
one of them changed is a finding, not an edit.

## 6 · Acceptance criteria — final

AC1–AC15 as Phase 2 wrote them, plus:

**AC16** the counterfactual holds for every demonstrable family: the external shapes throw from a bare router, the
traversal reaches `/app/admin`, and `/home/` and `/HOME` reach the `home` route — each proven before the defence
is asserted (T25-D1) · **AC17** AC4's four fields are compared inside every rejection row, never by a single sweep
(T25-D2) · **AC18** AC12 is asserted inside every rejection row, with the allowlisted control (T25-D3) ·
**AC19** `/home/`, `/HOME` and `/account%2Fsessions` are rows, each paired with its honoured near-miss
(T25-D4) · **AC20** a discard leaves a **refused** target exactly where it leaves an honoured one (F3).

## 7 · Required tests — final

Phase 2's list, plus: F4's two rows and T25-D4's `/HOME`; F7's mixed row; F3's discard row; T25-D1's
counterfactual controls as their own cases; and the leading-space row from §2, which is the shape that gives the
trimming mutant its harm.

**The mutation list is now written from the measurements**, which is the point of taking them: prefix test in each
direction, substring match, **case-folding**, **trailing-slash strip**, **trimming**, `decodeURIComponent` before
the check, a leading-`/` strip, a basename strip, the stored value returned unvalidated, and `null` instead of
home. The three in bold are the ones the router would honour, so a table that does not kill them is a table that
proves nothing about the shapes that matter most.

## 8 · Open questions

**No blockers.** Phase 2's one open item — how far AC4's sweep goes — is **closed by F5 as T25-D2**.

**Two things the owner should know, neither blocking:** the task is expected to add **no production code**, and
that expectation is now conditional in writing (T25-D5); and react-router's case-insensitive, trailing-slash-
tolerant matching means this SPA's allowlist is the *only* thing standing between a stored `/HOME` and a real
navigation — worth knowing if anyone later proposes "just normalise it".

## Next — Phase 5 — implementation plan.
