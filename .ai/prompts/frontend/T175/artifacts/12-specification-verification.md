<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). -->

# frontend · T175 · Phase 12 — Specification Verification

| | |
|---|---|
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

```
suite 2148 / 2148  ·  lint 0  ·  typecheck 0
harness 9/9 killed  ·  canary died  ·  survivors none  ·  broken none  ·  disagreements none
production change: TWO LINES
```

---

## 1 · The whole production change, measured

```diff
- void createPublicAuthApi().POST('/accounts', { body: { email, password } })
+ void createPublicAuthApi().POST('/accounts', { body: { email, password }, parseAs: 'stream' })
-   .POST('/accounts/verify-email', { body: { token } })
+   .POST('/accounts/verify-email', { body: { token }, parseAs: 'stream' })
```

**Two lines.** `git diff` over the whole task, filtered to non-comment lines, finds nothing else.
`ResendVerificationScreen`'s comment-only delta inside the same commit range is **T51's** — task 175 was
appended to `tasks.md` mid-T51, so three T51 commits land after that spec commit.

## 2 · Acceptance criteria

| AC | Held by | Evidence |
|---|---|---|
| **AC1** `200` + HTML → success | `verifyFromLinkShowsSuccessState` | *"renders the success state for an HTML error page served with 200"* — failed before the fix, **TR31** kills it |
| **AC2** `200` + truncated JSON → success | same | *"…for JSON truncated mid-document"* — failed before, **TR31** kills it |
| **AC3** declared `204` → success | T48's suite, **untouched** | *"renders the success state for a 204 at /app/verify?token=…"*, in TR33's 31-case list |
| **AC4** `400`/`500` → uniform failure | T49's **nine-status loop**, untouched | all nine named in **TR33**'s kills |
| **AC5** dropped connection → uniform failure | T49, untouched | *"renders the uniform failure when the transport never answers at all"* |
| **AC6** the loop stays green | — | 2148/2148 with the fix applied |
| **AC7** `202` + HTML → acknowledgement | `registrationAcknowledgement…` | failed before, **TR32** kills it |
| **AC8** `202` + truncated → acknowledgement | same | failed before, **TR32** kills it |
| **AC9** byte-identical across arms | same | three-arm comparison with **three baseline guards** (non-empty, carries the sentence, has no `<form`) |
| **AC10** the scan | `generatedClientCallsDoNotParseBodies…` | census asserted as a **whole table**; reach audited against `api/client.ts` **and the package**; classifier measured on a fixture. **TR34/35/37/38** each kill a distinct case |
| **AC11** no new key, route or primitive | — | the diff touches **no** `catalogue.ts`, `routes.tsx` or `app/ui` |
| **AC12** suite, lint, types | — | 2148/2148 · 0 · 0 |
| **AC13** every new case fails first | — | 2 verify, 3 registration, 2 scan — and the scan's non-vacuity **re-measured after** the fix |

**All thirteen held.** Requirements: **R3** (a verified email reaches success) and **R2** (one acknowledgement,
identical) are what the fix restores; **R4** is preserved, and TR33's 31 kills are the proof rather than the
claim.

## 3 · Phase 11's verdict is right and its account is wrong in five places

Kimi **ran** the harness and the suite — 66/66, 2148/2148, **9/9 killed, 0 disagreements** — and those numbers
reproduce. The verdict stands. But the prose describing what Phase 10 did is wrong often enough that letting
it stand would leave a false record of my own work, so each is corrected against source.

| Phase 11 says | Source says |
|---|---|
| *"**TR37** removes `createOidcClient` from the audit"* | It **adds** `createExtraClient` to the package. **Inverted** — the row exists to prove a *new* factory breaks the audit |
| *"**TR38** changes the fixture to destructure `data` without `parseAs`"* | It mutates the **scan's `namesData` regex** to `/\.data\b/` — i.e. it applies **Kimi's own I11 recommendation**. Nothing in the fixture moves |
| *"**TR39** retags the inherited case as belonging to T175 (probably via a comment or tag)"* | It mutates **`RegisterScreen`'s reject arm** to `() => undefined`. The retag was an **expectation label in the harness**, not a mutant. *"probably"* concedes the guess |
| *"the pin … finds the owning test by name **in the test run output**"* | It reads the owning **file's source** with `read(...)`. (The cited line **623 is correct**.) |
| *"`.then((r) => r.data)` is ambiguous … **the team preferred** the explicit destructuring signal"* | The stated reason was the **safety asymmetry**: a false non-reader fails loudly, a false reader is *exempt from the rule*. A different and weaker rationale was substituted for the one in the artifact |

**Why this matters beyond pedantry:** three of the four harness rows added at Phase 10 are described as doing
something other than what they do, and **TR38 is the row that justifies declining Kimi's own
recommendation.** A reader of Phase 11 alone would not learn that the recommendation was tested, let alone
that it kills exactly one case.

## 4 · Two open questions were closed in the register, and are **not** closed

Phase 11's table conflates the **OQ** series with the **D** series — they share numbers 5 and 6.

| | Phase 11 | Actually |
|---|---|---|
| **OQ-T175-5** | "Closed — no new AC4/5/6 cases needed" | That is **D-T175-5**. OQ-T175-5 is **open** — see below |
| **OQ-T175-6** | "Closed — third helper + 204 control" | That is **D-T175-6**. OQ-T175-6 is **open**: what a visitor sees when `codeExchange` meets an edge interstitial, unmeasured |
| **OQ-T175-8** | "Withdrawn — not pursued" | Withdrawn **as a duplicate of OQ-T43-9**, which the owning case cites by name. Not the same thing as dropped |

### OQ-T175-5 is the one that deserves the owner's attention, because **the fix causes it**

`RegisterScreen` reads **neither `data` nor `response`**. `openapi-fetch` resolves for every HTTP answer, so
with `parseAs: 'stream'`:

| Reply | Before | **After** |
|---|---|---|
| `403` + HTML interstitial (a WAF blocking the request) | parse rejected → the form stayed | **resolves → "Check your email."** |

**A registration the edge refused now shows the acknowledgement.** That is not a regression against R2 — R2
demands one acknowledgement for existing and new addresses, and this screen is built to know nothing — but it
is a **real behaviour change in a direction worth an owner's eyes**, and the asymmetry is visible in the
bundle: `VerifyScreen` consults `response.ok`, `RegisterScreen` consults nothing.

The fix was still correct: the alternative was the status quo, where **a registration the server accepted**
showed nothing at all. One of those two errors is silent and common, the other is loud and rare.

## 5 · Final open items

| | |
|---|---|
| **OQ-T175-5** | the error inverts on `RegisterScreen` — §4, an owner call, needs `response.ok` or a decision not to |
| **OQ-T175-6** | `codeExchange` under an edge interstitial, unmeasured |
| **OQ-T175-7** | **CLOSED** at Phase 10 by the package audit |
| **OQ-T175-8** | **WITHDRAWN** → OQ-T43-9 |
| **OQ-T43-9** | a failure word for `RegisterScreen` — carried, not mine |
| **FU-T43-2** | no shared test-util module; `installTypedTransport` now copied a third time |

---

**Phase 12 complete. Next is Phase 13 — PR Preparation.**
