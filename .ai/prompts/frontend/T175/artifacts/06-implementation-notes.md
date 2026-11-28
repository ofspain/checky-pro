<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T175 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

```
suite 2146 / 2146  (42 files, +9 cases, +1 file)  ·  lint 0  ·  typecheck 0
```

---

## 1 · Every new case failed before the fix (AC13)

| Suite | Before the fix |
|---|---|
| `verifyFromLinkShowsSuccessState` | **2 failed** — both arms rendered *"This verification link is not valid."* for a token the server had **accepted** |
| `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` | **3 failed** — the form was still mounted, so nothing visible happened |
| `generatedClientCallsDoNotParseBodiesTheyNeverRead` | **2 failed**, naming both unfixed calls |

Then the two-line fix, and **2146 / 2146**.

**And non-vacuity was re-measured after the fix**, because a scan that has only ever been green is not known
to work: removing `parseAs` from `VerifyScreen` makes the scan fail and **name
`features/account/VerifyScreen.tsx (POST)`**, with both the census case and the invariant case firing.

## 2 · The production change

Two call sites, each with the requirement that justifies it written above it — R3 on `VerifyScreen`, R2 on
`RegisterScreen`. `response` survives the option, which is what keeps `VerifyScreen`'s `ok` test working and
R4 intact for a genuine `4xx` or a dropped connection.

## 3 · The scan, and a finding bigger than the bug it guards

`src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` — its own file, because
`lintRejectsHandWrittenBackendFetch` is about the **lint script** as a build gate and a shared failure would
be ambiguous between two unrelated claims.

**Both bugs the parked version recorded are fixed, and the second was more interesting than its symptom.**

### A brace is not a statement boundary when the statement starts by destructuring

`codeExchange.ts` was classified a **non-reader**. Its call is
`const { data } = await createOidcClient().POST(…)`, and the first draft walked backwards to the nearest `;`
or `{` — which is the **destructuring brace**, so the slice began after `const {` and the binding that makes
it a reader had been cut off. Now line-based: up to the line that *opens* a statement, down to the line that
*closes* one, because an `await` binding sits **before** the call and a `.then` handler **after** it.

### A scan keyed on factories is structurally blind to the shape a testable module has

The two `profile/` readers were **not found at all**, and the reason is not a regex detail:

```ts
export async function readProfile(api: OidcApi, tokens: …)
export async function readAccount(api: AuthApi, tokens: …)
```

**They call no factory anywhere** — the client arrives as a **parameter**. A scan that looks for
`create…()` cannot see any call site that takes its client as an argument, **which is precisely the shape a
module written to be testable has.** The better a module's seams, the more invisible it is to that scan.

So a client-bearing identifier is now *either* bound from a factory *or* declared with a client **type**, and
both lists are audited against `api/client.ts`:

```
factories: ['createAuthApi', 'createOidcApi', 'createPublicAuthApi']
types:     ['AuthApi', 'OidcApi']
```

**That audit immediately caught a slip of mine** — it asserted bare factory *names* against a pattern that
requires the opening parenthesis, so it failed on every factory. The whole value of running a claim rather
than reading it.

**A limitation, stated:** `codeExchange` imports `createOidcClient` from `@themistra/api-client` directly,
not from `api/client.ts`. The call-finding pattern catches it, but the **reach audit only covers
`api/client.ts`**, so a new client factory added to the *package* would not break that case. Recorded as
**OQ-T175-7** rather than papered over.

## 4 · Deviations from the plan

### D-T175-5 — AC4, AC5 and AC6 got **no new cases**, and the brief was wrong to ask

`verifyFailureShowsOneUniformMessage` already holds all three:

| Claim | Owner |
|---|---|
| `400` and `500` render the uniform failure | the **nine-status loop**, `[400, 401, 403, 404, 409, 410, 429, 500, 503]` |
| A dropped connection renders it | *"renders the uniform failure when the transport never answers at all (AC3)"* |

Writing them again would be the duplicate instrument T173's rule forbids. **The brief should not have asked,
and the information was already in my own Phase 0 artifact**, which described that suite as *"38 cases, the
nine-status loop"*. I had the fact and did not apply it one phase later. **That suite is untouched by this
task and stayed green**, which is itself the AC6 evidence.

### D-T175-6 — a third `installTypedTransport`, and a control case for an impossibility

`verifyFromLinkShowsSuccessState`'s `installTransport` always answers `null` — **which is exactly why this
defect was invisible to that file** — so a body-carrying helper was added beside it rather than changing a
function its existing cases call. **FU-T43-2** again, stated rather than quietly worsened.

And a **control case** pins D-T175-1's impossibility: `new Response(body, { status: 204 })` **throws**. If a
`204` ever accepts a body, the `200` arms stop being the only reachable shape and that file needs a new one.

## 5 · Two of my own assertions were wrong, and both failed loudly

| | |
|---|---|
| `en.registerSent.body` **does not exist** | The sentence is `register.success`; `registerSent` carries only `heading`. **The second guessed catalogue key in two days** — and under a *negative* assertion it would have passed while measuring nothing, exactly as a guessed key did to a probe the day before. |
| `router.state.location.pathname` **carries the basename** | It is `/app/register/sent`, not `/register/sent` — the distinction T41 paid for. |

Both are noted at the lines where they happened.

## 6 · Open items

**OQ-T175-5** (the error inverts), **OQ-T175-6** (`codeExchange`'s visitor-facing behaviour, unmeasured),
**OQ-T175-7** (the reach audit does not cover the package's own factories), **FU-T43-2**.

---

**Phase 6 complete. Next is Phase 7 — Self-Review and the mutation harness.**
