<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). -->

# frontend · T48 · Phase 12 — Specification Verification

| | |
|---|---|
| **Consumes** | every prior artifact; `spec/frontend/{requirements,design,tasks}.md`, `agents.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

```
suite 2048 / 2048  (39 files)  ·  lint 0  ·  typecheck 0  ·  named suite 32 cases
harness: 22/22 regressions killed · 2/2 canaries dead · survivors ['TR20'] · broken none · disagreements none
every touched file also passes when run alone
```

---

## Requirement traceability

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R3** "WHEN a visitor opens a verification link and the token is valid, THEN the SPA SHALL show the verified success state" (`requirements.md:14-15`) | **Yes** | `routes.tsx:116` attaches the screen; `VerifyScreen.tsx:58,69` reads `?token`; `:72-78` posts it and sets success on `response.ok`; `:84-85` renders the state | **Yes** — success on 204 and on 200; the token from the URL is the body; trailing-slash link end to end | No | No |
| **L14** route set under `/app`, paths verbatim in §4c; `?token=<token>` author-confirmed (`design.md:53,138,145`) | **Yes** — an element on an already-declared path, attached by constant | `routes.tsx:50`, `:63`, `:116` | **Yes** — the declared set asserted unchanged; a source assertion that the attach branch matches the constant | No | No |
| **L11** WCAG AA, keyboard operation, managed focus, catalogue-only copy (`design.md:47`) | **Yes** | focus `VerifyScreen.tsx:63-65`; copy `:84-85` from `catalogue.ts:159-162` | **Yes** — focus with and without a token, a body control, no tab stop, the region mounted empty **before** it is filled, both keys as literal English, no untranslated key, nothing the server sent | No | No |
| **L6** generated client only (`design.md:38`) | **Yes** — `createPublicAuthApi()`, which has no session boundary | `VerifyScreen.tsx:4,72` | **Yes** — no `Authorization` header; `POST` with a JSON content type; a scan forbidding a bare `fetch(` | No | No |
| **L4** enumeration-safe UI (`design.md:21-26`; `agents.md`) | **Yes, and correctly scoped** — the lock requires identical copy **among** bad/expired/used tokens, which is R4 and task 49's; `AccountController.java:80-84` states the two outcomes are meant to be distinguishable | the success state is distinct by design | **Yes** — *PINS A LIMIT* asserts a rejected token and a tokenless arrival are indistinguishable **today**, which is the handoff | No | No |
| **L16** the only sessionStorage carve-out (`design.md:58`) | **Yes** — the token is on no permitted list and is written nowhere | no storage call exists in `VerifyScreen.tsx` | **Yes** — `Storage.prototype` spy **plus an intercept control**; the token in no storage; **the token in no DOM in any state**; **nothing logged, through a spy list derived from `console` at runtime** | No | No |
| **L17** service-worker boundaries (`agents.md:87-89`) | **Yes, by absence** — `/app/verify` is not on the bypass list, so a cold arrival receives the shell | — | Not directly | No | No — recorded so it is not "fixed" |
| **D7** every rendered catalogue key is measured | **Yes** | `catalogue.ts:159-162` | **Yes** — both keys spelled as literal English, plus the catalogue-key set pin | No | No |

## Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| **AC1** | **PASS** | success at `/app/verify?token=…`; **and from a trailing-slash link**, which until this task could only be claimed against a synthetic table of probes |
| **AC2** | **PASS** | the body is the token **from the URL**; base64url round trip; **first of two tokens**; the `+` latent trap pinned |
| **AC3** | **PASS** | one public POST; **no `Authorization`**; **`POST` with `application/json`** |
| **AC4** | **PASS** | 204 → success; **200 → success**, which holds `response.ok` against `=== 204` |
| **AC5** | **PASS** | both keys literal; no untranslated key; nothing from the response body |
| **AC6** | **PASS** | focus on arrival, focus **with no token**, a body control, no tab stop, and the live region **mounted empty before being filled** — the ordering `StatusRegion`'s own header requires |
| **AC7** | **PASS** | one request; one under StrictMode; the guard **before** the request (source order); **a token arriving after a tokenless visit** — the guard's *meaning*, which nothing measured until Phase 9 |
| **AC8** | **PASS** | prototype spy + control; token in no storage; **token in no DOM in any state**; **nothing logged at all** |
| **AC9** | **PASS** | `PUBLIC_ROUTE_PATHS` unchanged; attached by constant |
| **AC10** | **PASS** | no lowercase tag; no props; `RENDER_SITES` 10, union 23 |
| **AC11** | **PASS** | 2048/2048, and each touched file green alone |
| **AC12** | **PASS** | no token and empty token send nothing; *PINS A LIMIT* pins the collapse and must fail when task 49 lands |

## Deviations

| ID | Status |
|---|---|
| **D1** | The screen is shared with task 49. Ratified at the freeze. |
| **D2 / R-T48-1** | POST on arrival. Owner-decided at Phase 4 after the consequence was **measured** at `VerificationTokenService.java:133`, with Kimi's contrary rationale rejected. Documented in `VerifyScreen.tsx:44-54`, which is the file a mitigation would change. |
| **D3** | No working-state message. The live region must be mounted and empty anyway, so the missing `verifying` key costs nothing structurally — and a case proves the region is there, empty, on arrival. |
| **D-T48-1** | Five test files, one a T43 suite the freeze forbade. **Ratified (Phase 9.)** Numbers and assertion messages only. **FU-T48-1** carries the process fix. |
| **D-T48-2** | The `+`/`%`/`/` round-trip test replaced, because `generateRawToken` cannot emit those characters. **Ratified (Phase 9.)** |

---

## Comment audit

**Clean for this task.** Every surviving mention of `/app/verify` having no element is past-tense and
correct: `VerifyScreen.tsx:9`, `catalogue.ts:141`, `verifyFromLinkShowsSuccessState.test.tsx:7`,
`routesResolveUnderAppPrefixOnly.test.tsx:151` ("Before task 48, `verify` had no element"). The
"N of the eight have no element" count was moved to **three** and its remaining paths named.

### One false comment found, in a file this task did not touch — reported, not fixed

`src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx:155-157` states:

> `/app/home` and the rest are registered paths with no element, and no task in the package builds one yet
> (T24-D11) … It disappears on its own as screens land.

**False since task 174.** `routes.tsx:138` attaches `HomeScreen` to `HOME_PATH` and `:136` attaches
`AuthenticatedLayout`. The last sentence is the tell: screens landed and nobody came back. The regex it
documents is **not** dead — the other authenticated paths still have no element, so the filter still has
work — but its stated reason is wrong, and this is a file whose claim is *"no token in any log"*.

**Not fixed here, deliberately.** It belongs to T24/T42/T174, this task has already broken one *Files NOT to
Modify* line, and editing a security-relevant suite for a comment outside my scope is how blast radius grows.
Recorded as **FU-T48-2**.

## Principal engineer's answers

**1 · Is the task fully complete?** Yes. `/app/verify` has carried no element since task 16, so every
verification email the platform has sent landed on a blank document. It resolves to a screen that reads the
token, posts it once, and shows the verified state, with a 32-case named suite.

**2 · Does it satisfy every acceptance criterion?** Yes — all twelve, and three were strengthened past the
brief: AC7 by measuring the guard's *meaning* rather than only its source order, AC4 by holding `response.ok`
against the narrower test, AC8 by proving the token reaches no DOM and no console however a log is spelled.

**3 · Does it violate any LOCKED decision?** No. **L4 is the one worth stating explicitly**: it was read as
requiring uniformity *among failure reasons*, not between success and failure, which both `agents.md` and
`AccountController`'s javadoc support. A success state indistinguishable from failure would have been the
violation.

**4 · Remaining risks.**

| Risk | Reading |
|---|---|
| **R-T48-1 — a JavaScript-executing email gateway burns the token before the human clicks** | **The task's real-world risk, and it is not hypothetical**: `markConsumed` is durable, so the account verifies and the human is then shown task 49's uniform failure, which R4 forbids task 49 from explaining. Owner-decided because R3's wording compels on-arrival; the mitigation is one effect in one file. **This is the item I would put in front of the owner first.** |
| **OQ-T48-4 — `auth.yaml` declares no failure response for `verifyEmail`** | **A contract-accuracy defect, NOT a blocker — corrected below after measuring the backend.** |
| `idle` is one rendering for three situations | By design, pinned, and the pin must fail when task 49 lands. |
| TR20, and the `POST` assertion | Both declared unfalsifiable at the point of use rather than hidden in a score. |
| **FU-T48-1** | Two consecutive tasks have edited a closed suite for source-text count pins. A process fix, not a code one. |
| **FU-T48-2** | The false comment above, in a file this task did not own. |
| OQ-T48-5 | The heading still reads "Verify email" after verification, and focus lands there, so it is announced first. Copy; owner's call. |

---

**PASS** — R3 is implemented end to end from the emailed link, all twelve ACs hold with 2048/2048 green and 22/22 mutants killed, no LOCKED decision is violated, and the one material risk (R-T48-1) was measured against the backend rather than assumed and is owner-decided with its mitigation point documented in the code.

---

## Correction to this artifact, after measuring the backend (2026-11-28)

**I recorded OQ-T48-4 as blocking task 49. That is too strong, and the difference changes what happens
next, so it is corrected here rather than left to be discovered.**

`contracts/api/auth.yaml` really does declare `'204'` and nothing else for `verifyEmail`. But the behaviour
task 49 needs already exists and is already uniform **at the backend**:

| Evidence | |
|---|---|
| `AccountExceptionHandler.java:46-52` | **One** mapping for `VerificationTokenRejectedException` → a fixed `400`, fixed `ProblemTypes.INVALID_TOKEN`, fixed title, and a javadoc that says it *"never distinguishes 'not found' from 'expired' from 'already used' from 'wrong account state'"* |
| `AccountService.java:38, :46, :47-48` | All three paths throw **the same** exception: token missing/expired/consumed, account missing, and account not `PENDING_VERIFICATION` — the last covering already-verified, suspended **and deleted** |

So **all four of task 49's reasons** — invalid, expired, used, deleted-account — already produce one
indistinguishable response. R4's uniformity is enforced server-side, which is the right place for it.

**And the frontend does not need the contract to declare the 400.** `VerifyScreen` branches on
`response.ok`; openapi-fetch resolves on HTTP errors and rejects only on transport failure, so a `400`
arrives as `!response.ok` whether or not the contract documents it. The missing declaration would only matter
to code that wanted to *type and read* the error body — and **R4 forbids task 49 from doing that**, since
reading `type` or `title` is how uniform failure stops being uniform.

**The contract gap is harmless here precisely because R4 forbids using what is missing.** Task 49 is
therefore **not blocked**: it can be specified and built against measured behaviour.

**What remains** is a documentation defect worth fixing by whoever owns `contracts/api/auth.yaml` (not this
task — `spec/` and `contracts/` are not mine to edit): an undeclared `400` means the generated client has no
error schema for an endpoint that really does fail. Recorded as **OQ-T48-4, reclassified from blocking to a
contract-accuracy defect.**
