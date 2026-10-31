# frontend · T31 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/02-task-implementation-brief.md` + `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

Kimi's phase 3 returned **eleven findings** — its own closing line says ten, which is a miscount, not a
missing finding. **Ten accepted, one rejected on fact**, and two of the accepted ones are resolved differently
from the way they were recommended.

The blocker is real and it was a contradiction inside my own brief.

---

## F1 — ACCEPTED (Blocker). The brief required something its own ownership rule forbade

AC5 told T31 to extend the forbidden-claim list; the ownership rule says T30 owns *"the imports, the setup,
every helper below, and every `describe('T30: …')` block"* and allows T31 to touch a T30 block only to repair
a rename. `FORBIDDEN_TOKEN_CLAIM` is a module-level helper constant at
`profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:183`. **Adding to it is neither of the permitted
things.** Verified against source, and the contradiction is mine, not Kimi's misreading.

**Resolved WITHOUT amending the ownership rule**, which is the part that differs from the recommendation.
Kimi offers either "permit T31 to extend the shared constants" or "require T31 to define its own and compose".
The second needs no amendment at all, because **calling a T30 helper is use, not modification**:

- T31 declares its own `T31_FORBIDDEN_TOKEN_CLAIM` (`status`, `emailVerified`) **inside its own `describe`
  block**, where ownership is unambiguous.
- T31 **calls** T30's `tokenDecodes()`, `hitsOf()`, `synthetic()`, `refuseEmpty()`, `surfaces()`, `leaks()`
  and `watchConsole()` without touching them.
- T31 keeps its **own instruments table** in its own AC12 block rather than adding rows to T30's
  `INSTRUMENTS`.

Amending a frozen ownership rule to make one task's job easier is how a shared file stops being safely
shared. The rule stands exactly as T30 froze it.

## F2 — ACCEPTED (Major), resolved the OPPOSITE way to the recommendation

Verified: the generated type really is a closed union —
`status: "PENDING_VERIFICATION" | "ACTIVE" | "LOCKED" | "SUSPENDED" | "DELETED"` at
`libs/ts/api-client/src/generated/auth.ts:494`. So AC2 (derive the type) and AC8 (an unknown status is not
silently ACTIVE) genuinely conflict, exactly as described.

**The hazard is worse than the finding states**, and that is what decides the resolution. With a closed union,
a consumer writing an exhaustive `switch (account.status)` is **compile-time complete and runtime
incomplete**: TypeScript proves all five cases are handled, and a sixth value walks straight through to
whatever the default path happens to be. The type system actively reassures the author.

Kimi recommends widening to `Omit<AccountResponse,'status'> & { status: string }` and passing unknowns
through. **Refused**, because it pays for one reader's honesty with every future consumer's narrowing: from
then on, every screen touching `status` must handle an arbitrary string forever, and the exhaustive `switch`
that *should* be sound becomes impossible to write.

**FROZEN: the derived union stays, and an unrecognised status is a FAILURE, not a passthrough.** The contract
says `status` is one of five; a value outside that set means the server is out of contract, which is precisely
the `unavailable` case. This satisfies AC8 by rejecting rather than by widening, needs no cast, and keeps
consumer narrowing sound.

It fails **closed**, and on a field that gates access that is the only safe direction — treating an
unrecognised status as usable is the dangerous default. The cost is explicit: **a sixth server-side status
becomes a hard failure in the SPA until the contract is regenerated.** Under **L6** the generated client *is*
the contract, so that coupling is correct rather than unfortunate, and it fails loudly instead of silently
granting access.

## F3 — REJECTED ON FACT. The measurement exists, and it is of this endpoint

Kimi says AC9 inherits T30's `/userinfo` measurement by analogy and that `/accounts/me`'s 401 was never
measured against auth-service. **It was**, in Phase 0, and the table is at
`artifacts/00-repository-understanding.md:120-126` — `GET http://localhost:18080/accounts/me`, the auth
container's own host port, 2026-10-31:

| Request | Response |
|---|---|
| No bearer | `401`, `WWW-Authenticate: Bearer`, **empty body** (`Content-Length: 0`), `Set-Cookie: JSESSIONID` |
| Malformed bearer | `401`, empty body, `WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Malformed token", error_uri="…rfc6750#section-3.1"` |

So there is **no RFC 9457 body, no Spring HTML error page, and no different header format** — the three
alternatives the finding raises are each measured absent. The recommendation's substance ("do not inherit the
OIDC measurement by analogy") is right in principle and was already followed.

**The wording invited the error, though, and that is fixed:** phase 2's AC9 said "the measured 401" without
naming which endpoint, next to a brief full of T30 references. AC9 now cites the Phase 0 line range and names
`GET /accounts/me` explicitly. This is the third Kimi finding across T30–T31 that was wrong on fact while
pointing at a real weakness in how the claim was written.

## F4 — ACCEPTED (Major). The decision was real and recorded nowhere durable

Verified: phase 1 records OQ-T31-3 as *"Disposition, pending owner input"*, and phase 2 asserts
*"Owner decision, 2026-10-31"* with nothing cited. The decision **did** happen — but it existed only in
conversation, and a conversation is not an artifact. A reader of these files six months from now would find an
unsupported assertion where a decision should be, which is a fair complaint and worth more than its "Major"
rating.

**Recorded here verbatim**, which is what makes this freeze the citation phases 5–13 point at.

### Owner decisions, 2026-10-31

**Question 1 — T31 scope**, given the measurement that suspending an account does not invalidate its token:
> **Answer: "Read only, log the gap."** T31 implements the read and proves the status is reported faithfully
> and never invented — which is exactly what its statement says. It invents no route, copy key or redirect,
> since **L14** fixes the route set and **L11** requires catalogue copy. The readable-but-not-ACTIVE gap stays
> logged as **OQ-T31-3** for the owner to assign to task 33, a new unit, or the account screen when it is
> built.

**Question 2 — `auth.yaml`'s missing root-level `security` requirement** (and its undocumented 401):
> **Answer: "Log only, don't touch it."** The fix is a root-level `security: [- bearerAuth: []]` affecting
> every operation in a contract shared by every service and client — far wider than T31's one endpoint. T31's
> code is identical either way: the server requires a bearer and the client sends one. Stays **OQ-T31-1** and
> **OQ-T31-2**.

OQ-T31-3 is therefore **not** removed from Open Questions by assertion. It remains open, owned by the owner,
and out of T31's scope **by decision** rather than by omission.

## F5 — ACCEPTED (Major). `false` is a value, not an absence

Verified: AC7 said "missing or empty in turn", and `emailVerified` is a `boolean` whose `false` is
contract-valid and measured-real. A literal implementation would reject it, contradicting AC6. "Empty" has no
meaning for a boolean at all.

**AC7 now distinguishes absence from falsity per field type**, and a control proves `emailVerified: false` is
accepted and returned as `false`. This is the same class of error as T30's `sub: ''` check — the difference
being that an empty string genuinely is unusable, where `false` is an answer.

## F6 — ACCEPTED (Minor). T31 builds its own harness, in its own block

Verified: `harness()` at `:385` constructs `createOidcApi`. T31 needs `createAuthApi`. Resolved the same way
as F1 — **T31 declares `authHarness` in its own block** rather than adding a `client: 'oidc' | 'auth'` option
to T30's helper. Two small harnesses that can be read side by side beat one with a mode switch, and it keeps
the ownership rule intact. The duplication is deliberate and bounded.

## F7 — ACCEPTED (Minor). AC10 is aligned with T30's seven surfaces

Verified: `surfaces()` at `:270` returns body, title, head, `window.name`, URL, `sessionStorage`,
`localStorage`; AC10 had listed five of the seven, omitting **body** and **head**. Since T31 reuses
`surfaces()` unchanged, the omission was in the criterion's prose rather than in the sweep — but a criterion
that under-describes its own instrument is how an instrument later gets quietly narrowed. Aligned.

**Also inherited, and restated rather than silently carried:** `document.cookie` and `indexedDB` are **not**
swept, because jsdom gives an empty cookie jar and no `indexedDB`, so sweeping either reports clean for an
environmental reason. T30 declared this as **T30-D7** after Kimi's phase 11 made the same point about T30
inheriting T29's list without its caveat. T31 inherits it **with** the caveat this time.

## F8 — ACCEPTED (Minor). The JSON-`null` body goes into the criterion, not a constraint note

Verified: the warning was in Constraints while AC7 spoke only of fields. A `null` body has no fields, so a
per-field check cannot reach it. **This is not hypothetical — it is a defect that shipped in T30** and was
found in phase 11: `data === undefined` is false for `null`, so `typeof data.sub` threw
`TypeError: Cannot read properties of null (reading 'sub')` out of the reader, past a `try/catch` that only
wrapped the transport. AC7 now requires it explicitly.

## F9 — ACCEPTED (Minor), and the example it gives is the strongest argument for it

Verified: AC5 said "anywhere in the comment-stripped tree". For T30's claim list that phrasing was already
loose; for T31's two fields it is dangerous, because **`response.status` appears throughout commissioned
code** — `readProfile.ts` alone reads it three times, and `rateLimitNotice.ts` and `sessionBoundary.ts` are
*required* by R23 and R21 to read it. A tree-wide scan for `status` would fail on the code the spec mandates.

**AC5 rephrased as Kimi recommends:** neither field is read from a token **in any file that decodes a token**,
proven by composing T30's decode scan — which bounds that set to exactly one, asserted — with a claim scan
over only that set. The composition is not a convenience; it is the only way the claim is checkable at all.

## F10 — ACCEPTED (Minor). FROZEN: T31 defines `ReadAccountError`

Option (a). Renaming `ReadProfileError` to a shared `ReadError` would edit a T30-owned module, and the two
readers' failures are **not** the same set even where the names coincide: T30's `no-session` exists because a
bearer-less `/userinfo` answers `302` into a followed HTML page, while T31's exists because an unauthenticated
read sets a `JSESSIONID`. Same word, different reason.

**Frozen failure set:** `no-session` · `unauthorized` · `unavailable`. Message looked up **by key** from a
closed `Record`, so the type cannot carry detail — and that property is pinned, because it is what made T30's
MP09 mutant inert rather than an escape.

## F11 — ACCEPTED (Minor). FROZEN: yes, short-circuit on no token

The finding correctly notes T30's reason does not transfer: `/accounts/me` answers a real 401, which the
boundary handles. **But the measurement supplies a different reason that does transfer** — a bearer-less
`/accounts/me` returns `Set-Cookie: JSESSIONID`, so an unauthenticated read has a **server-side side effect**
even though its status code is well behaved. It also provokes a renewal attempt that cannot succeed.

**Frozen:** with no token held, `readAccount` returns `no-session` and **issues no request at all**, asserted
by an empty call list. Not an optimisation — a side-effect avoidance, for the same reason as T30 but through a
different mechanism. **And `''` is guarded alongside `null`**, because `sessionBoundary.ts:89` attaches the
bearer with `if (token)`, so an empty string sends no `Authorization` header at all and produces exactly the
bearer-less request this guard exists to prevent. That cost T30 a phase-11 finding; it is not repeating here.

---

## The frozen brief

Everything in `artifacts/02-task-implementation-brief.md` stands except as amended below. **This document is
the specification phases 5–13 use.**

### Amended acceptance criteria

- **AC2** — the returned type is **derived** from the generated `AccountResponse`, union included and
  unwidened. (F2)
- **AC5** — neither `status` nor `emailVerified` is read from a token **in any file that decodes a token**,
  proven by composing T30's decode scan (which bounds that set to exactly one, asserted) with a claim scan
  over only that set. Not a tree-wide scan: `response.status` is read by code R21 and R23 require. (F9)
- **AC7** — an incomplete body is a failure, not a partial account. Per field type: `accountUuid`, `email`,
  `status` and `createdAt` are rejected when **missing, `null`, non-string or empty**; `emailVerified` is
  rejected when **missing, `null` or non-boolean**, and **`false` is accepted and returned as `false`** (F5).
  A **JSON-`null` body is rejected without reaching any property access** (F8).
- **AC8** — an **unrecognised** status string is a **failure**, not a passthrough and not ACTIVE. (F2)
- **AC9** — a failed read surfaces no detail, no stack and no cause, including from **`WWW-Authenticate`**, as
  measured for **`GET /accounts/me`** specifically at `artifacts/00-repository-understanding.md:120-126` —
  `401` with an **empty body** and the detail in the header. No RFC 9457 body and no HTML error page exist on
  this path; all three alternatives are measured absent. (F3)
- **AC10** — the account body is absent from **all seven** of T30's surfaces — `document.body.innerHTML`,
  `document.title`, `document.head.innerHTML`, `window.name`, the URL, `sessionStorage`, `localStorage` — and
  all six console channels, raw and percent-decoded. `document.cookie` and `indexedDB` are **declared
  unswept** (jsdom). (F7)
- **AC13, new** — with **no token held (`null` or `''`)**, `readAccount` returns `no-session` and issues
  **no request at all**, asserted by an empty call list. (F11)

AC1, AC3, AC4, AC6, AC11 and AC12 stand unchanged.

### Frozen design decisions

| ID | Decision |
|---|---|
| **G1** | T31 adds only `describe('T31: …')` blocks. It **declares its own** constants, harness and instruments table inside them, and **calls** T30's helpers without modifying any. The ownership rule is **not** amended. (F1, F6) |
| **G2** | `Account` is the derived `AccountResponse`, union unwidened. An unrecognised status **fails**. (F2) |
| **G3** | T31 defines **`ReadAccountError`** with `no-session` · `unauthorized` · `unavailable`, message by key from a closed `Record`. (F10) |
| **G4** | **Short-circuit on no token**, guarding `null` **and** `''`. (F11) |
| **G5** | The `api.GET` call is wrapped in a `try/catch` converting any throw into `unavailable` — `openapi-fetch` rejects when the transport throws. (T30's I1, a shipped defect) |
| **G6** | Both the client and the token accessor arrive as **parameters**. No module-level construction. |

### Open Questions

**No blockers.** **OQ-T31-1** and **OQ-T31-2** (`auth.yaml`'s absent root-level `security`; its undocumented
401/403) and **OQ-T31-3** (nothing owns a readable-but-not-ACTIVE account) are **open and owner-assigned**,
recorded above with the decisions verbatim. None blocks implementation.

---

**Phase 4 complete. Approved to proceed.** Eleven findings: ten accepted, one rejected on fact, two resolved
against their recommendation and the reasons recorded. Phase 5 plans the implementation.
