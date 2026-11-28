<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# frontend · T175 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T175 — A reply the client cannot parse is not a verification/registration failure |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-codebase-probe.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## The defect

`openapi-fetch` parses JSON responses by default. For screens whose settle handler only checks `response.ok` or simply resolves, a success-status reply whose body is not valid JSON — an edge interstitial, a truncated response, a proxy's HTML answer — causes the promise to **reject with a `SyntaxError`**. The screen then treats the reply as a transport failure.

The probe measured the consequences:

- `VerifyScreen`: a valid-token visitor with a `200` HTML body sees *"This verification link is not valid."* instead of *"Your email has been verified."*.
- `RegisterScreen`: a visitor with a `202` HTML body sees the unchanged form instead of the acknowledgement, and may submit again.

`ResendVerificationScreen` was fixed by T51 with `parseAs: 'stream'`. The same one-line fix applies to the other two non-reading callers.

## The fix

### OQ-T175-1 — No third state is needed

**Decision:** Add `parseAs: 'stream'` to both affected calls. Do not add a third state, new copy, or new catalogue key.

**Rationale:**
- The probe measured that `response.ok` is still readable when `parseAs: 'stream'` is used; only `data` becomes a stream.
- `VerifyScreen` can therefore keep distinguishing genuine HTTP failures (`response.ok === false`) from successes, preserving R4's *"fails for any reason"* for transport failures while fixing the false-negative for non-JSON success bodies.
- `RegisterScreen` simply navigates on resolve; with `parseAs: 'stream'` the resolve arm fires for any HTTP status, including success statuses with non-JSON bodies.

**Production changes:**

```diff
// VerifyScreen.tsx:100
- .POST('/accounts/verify-email', { body: { token } })
+ .POST('/accounts/verify-email', { body: { token }, parseAs: 'stream' })

// RegisterScreen.tsx:95
- void createPublicAuthApi().POST('/accounts', { body: { email, password } })
+ void createPublicAuthApi().POST('/accounts', { body: { email, password }, parseAs: 'stream' })
```

---

### OQ-T175-2 — Both screens in one unit

**Decision:** T175 modifies both `VerifyScreen.tsx` and `RegisterScreen.tsx`, and tests for both, in one task.

**Rationale:** `tasks.md` names both screens, the mechanism is identical, and the probe's sweep shows the set of affected callers is closed at these two.

---

### OQ-T175-3 — No third caller to decide about

**Decision:** The three callers that read `data` — `codeExchange.ts`, `readProfile.ts`, `readAccount.ts` — remain unchanged.

**Rationale:** For them, a parse failure means no usable data, which is a real failure. Adding `parseAs: 'stream'` would force them to parse manually and would not improve correctness.

---

### OQ-T175-4 — A source scan that distinguishes readers from non-readers

**Decision:** Add a tree-wide source scan that requires `parseAs: 'stream'` on any generated-client call whose settle handler does not read `data`.

**Rationale:**
- A blanket "always use `parseAs: 'stream'`" rule would break the three legitimate readers.
- A blanket "never use it" rule would allow the defect to reappear in a fourth non-reading screen.
- The correct invariant is: *if you do not read the body, do not let the library parse it*.

**Implementation sketch:**
- Scan every production `.ts` / `.tsx` file for calls through `createPublicAuthApi()` (or later `createAuthApi()`).
- For each call, inspect the `.then` callback:
  - If the callback parameter references `data` (e.g., `({ data })`, `response.data`, destructured `data`), it is a **reader** — exempt from the `parseAs` requirement.
  - If the callback has no parameter or does not reference `data`, it is a **non-reader** — assert the call contains `parseAs: 'stream'`.
- Fail with a message naming the file and call.

The scan is expected to be mechanical and may need a small allow-list if a non-reading call has a legitimate reason not to stream (none known today). The scan lives in `src/api/lintRejectsHandWrittenBackendFetch.test.ts` or a dedicated sibling file; the exact file is left to Phase 5 after measuring which keeps concerns grouped cleanly.

---

## Test design

### VerifyScreen

Add cases to `verifyFromLinkShowsSuccessState.test.tsx` (the suite that owns the success-state claim) that mock:

- `204` with an HTML body
- `204` with truncated JSON
- `200` with an HTML body
- `200` with truncated JSON

Each asserts the success text is rendered, not the failure text, and that the token is still sent. These cases must fail before the fix.

A case should also assert that a genuine `400`/`500` still renders failure, confirming `response.ok` is still consulted.

### RegisterScreen

Add cases to `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` that mock:

- `202` with an HTML body
- `202` with truncated JSON

Each asserts the form is replaced by the acknowledgement (i.e., the screen navigates to `register/sent`). These cases must fail before the fix.

### Source scan

Add the scan described under OQ-T175-4. It should pass with the fix and fail if a new non-reading call is added without `parseAs: 'stream'`.

---

## Files touched and why

| Path | Change | Why |
|---|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | Add `parseAs: 'stream'` | Fix R3 false-negative on non-JSON success bodies. |
| `frontend/src/features/account/RegisterScreen.tsx` | Add `parseAs: 'stream'` | Fix R2 false-negative on non-JSON success bodies. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | Add non-JSON success-body cases | The only instrument that can hold VerifyScreen's fix. |
| `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | Add non-JSON success-body cases | The only instrument that can hold RegisterScreen's fix. |
| `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` (or new sibling) | Add reader/non-reader scan | Prevent recurrence. |

## Files explicitly NOT touched

| Path | Why |
|---|---|
| `frontend/src/features/account/ResendVerificationScreen.tsx` | Already fixed by T51. |
| `frontend/src/auth/codeExchange.ts` | Reads `data`; parse failure is a real failure. |
| `frontend/src/features/profile/readProfile.ts` | Reads `data`; correct as-is. |
| `frontend/src/features/account/readAccount.ts` | Reads `data`; correct as-is. |
| `frontend/src/i18n/catalogue.ts` | No new copy needed. |
| `frontend/src/app/routes.tsx` | No route change. |
| `frontend/src/app/ui/**` | No primitive change. |

## Required tests mapped

| # | Test | AC / rule |
|---|---|---|
| 1 | VerifyScreen: `204` + HTML body renders success | R3 |
| 2 | VerifyScreen: `204` + truncated JSON renders success | R3 |
| 3 | VerifyScreen: `200` + HTML body renders success | R3 |
| 4 | VerifyScreen: `200` + truncated JSON renders success | R3 |
| 5 | VerifyScreen: genuine `400`/`500` still renders failure | R4 preserved |
| 6 | VerifyScreen: dropped connection still renders failure | R4 preserved |
| 7 | RegisterScreen: `202` + HTML body navigates to acknowledgement | R2 |
| 8 | RegisterScreen: `202` + truncated JSON navigates to acknowledgement | R2 |
| 9 | RegisterScreen: `409`/etc still navigates to acknowledgement (already true) | R2 |
| 10 | Source scan: non-reading generated-client calls use `parseAs: 'stream'` | OQ-T175-4 |
| 11 | Whole suite, `oxlint`, `tsc --noEmit` green | — |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| `parseAs: 'stream'` changes the response type and breaks TypeScript | Probe measured `tsc --noEmit` clean. |
| The scan is too brittle | Start with a regex-based scan and an explicit allow-list; refine if it flags legitimate patterns. |
| A case passes before the fix | Each new case mocks a non-JSON success body; before the fix these reject and render failure/no-navigation. |
| Genuine readers are flagged | The scan explicitly looks for `data` usage in the callback to exempt them. |

## Design complete

Next: Phase 4 — owner gate. The two-line production fix and the tests above are ready for ratification before implementation begins.
