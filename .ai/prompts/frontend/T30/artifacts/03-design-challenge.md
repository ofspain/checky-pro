<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T30 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

**Issue: AC5's "no token decode" scan will fail on the existing ID-token nonce validator.**
· Severity: **Blocker**
· Evidence: `frontend/src/auth/idTokenNonce.ts:53-81` decodes the `id_token` using `split('.')`, `globalThis.atob(...)`, and `JSON.parse(...)` to validate `nonce` (R7, T19). AC5 currently proposes scanning the comment-stripped tree for those exact forms and forbidding reads of PII/verification claims from any token. Unless `idTokenNonce.ts` is explicitly allowlisted, the scan will report a violation in already-committed, spec-required code.
· Recommended brief amendment: Add a `PERMITTED_TOKEN_READS` allowlist to AC5 with one row — `{ file: 'auth/idTokenNonce.ts', forms: ['split', 'atob', 'JSON.parse'], claim: 'nonce', why: 'R7 nonce validation only; no PII or verification state read' }`. Add a control proving the scan flags the file when the row is removed, per AC10/T22-D14.

**Issue: The OIDC client factory has no stated public-operations set.**
· Severity: **Major**
· Evidence: `frontend/src/api/client.ts` hand-maintains `AUTH_PUBLIC_OPERATIONS` because the session boundary needs to know which requests must not carry a bearer and must not renew on 401. The brief calls for a new `createOidcApi` factory, but `/oauth2/token` is `security: []` while `GET /userinfo` requires a bearer and renewal. Without an `OIDC_PUBLIC_OPERATIONS` set passed to the boundary, the factory cannot distinguish the two.
· Recommended brief amendment: Add `OIDC_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set(['POST /oauth2/token'])` in `frontend/src/api/client.ts` and pass it to `boundary.middleware(OIDC_PUBLIC_OPERATIONS)` inside `createOidcApi`.

**Issue: AC8 incorrectly frames `/userinfo` failures as RFC 9457 errors.**
· Severity: **Major**
· Evidence: `/userinfo` is an OIDC endpoint. It returns OAuth2-style errors (e.g., `401 Unauthorized` with a `WWW-Authenticate` header), not `application/problem+json`. The brief's standing RFC 9457 rule (`agents.md` API access) governs auth-service controller endpoints, not SAS's OIDC protocol surface. Applying it literally to `/userinfo` would make the reader's error-handling requirement untestable and misleading.
· Recommended brief amendment: Rephrase AC8 as: "The reader surfaces a failed `/userinfo` call without exposing problem detail, stack trace, or copy that names a cause, whether the failure body is OIDC/OAuth2 format or RFC 9457."

**Issue: AC5 does not decide whether `sub` may be read from the access token.**
· Severity: **Minor**
· Evidence: R20(c) forbids "PII or verification state" from the access token. `token-claims.md` lists `sub` (account UUID) as present in every issuance path. The brief's AC5 forbids a specific claim set (`email`, `name`, `given_name`, `family_name`, `preferred_username`, `picture`, `email_verified`) but omits `sub`. An account UUID is linkable PII, yet the session boundary already transports the token that contains it, and some future module could read `sub` as a cheap substitute for calling `/userinfo`.
· Recommended brief amendment: Clarify AC5: reading `sub` from the access token is permitted only for transport-bound signing/audience checks performed by the boundary; using it as profile data or displaying it is forbidden. Add `sub` to the forbidden-claim list for any non-transport read.

**Issue: No defined behavior when `readProfile` is called without a token.**
· Severity: **Minor**
· Evidence: The brief says the reader depends on the session boundary for the bearer. If the tab has no token, the boundary will still send a bearer-less request to `/userinfo`, receive 401, attempt silent renewal, fail (renewal is not built), and return the 401. This works but is wasteful and makes "no session" indistinguishable from "renewal failed" at the reader.
· Recommended brief amendment: Decide and document whether `readProfile` short-circuits when the boundary reports no current token (returning a defined "no session" result) or whether it always lets the boundary handle the 401. Add a test for whichever behavior is chosen.

**Issue: The shared test file has no stated ownership boundary between T30 and T31.**
· Severity: **Minor**
· Evidence: The brief creates `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` for T30 and says T31 extends it. Without a convention, T31 could rewrite setup, rename imports, or reorder cases in ways that silently change T30's meaning during a later task.
· Recommended brief amendment: Add a constraint that T30 owns all `describe('T30: ...')` blocks and the file's imports/setup; T31 must add only new `describe('T31: ...')` blocks and must not edit T30 blocks except to fix compilation errors caused by renamed exports.

**Issue: The L6 lint rule's coverage of `/userinfo` is assumed, not verified.**
· Severity: **Minor**
· Evidence: AC3's control relies on `lintRejectsHandWrittenBackendFetch` to reject a hand-written `fetch` to `/userinfo`. The rule is in `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` and scans production source for backend route prefixes. `/userinfo` is an OIDC path, not an auth-service REST path, and may not be in the rule's current denylist.
· Recommended brief amendment: Before treating the lint-rule control as evidence, verify that the rule flags `/userinfo`. If it does not, add `/.well-known/`, `/oauth2/`, `/userinfo` to the rule's SAS-surface prefix list as part of this task.

**Issue: AC6/AC7 leak sweep omits an explicit console-channel assertion.**
· Severity: **Minor**
· Evidence: The brief says "the profile is never logged or sent to analytics" and "absent from ... every console channel." T29 already has a reusable `watchConsole()` helper that covers six channels. The brief does not explicitly require reusing it, so a leaner implementation might miss one channel.
· Recommended brief amendment: State that AC6/AC7 reuse T29's `watchConsole()` over all six channels (`log`, `info`, `warn`, `error`, `debug`, `trace`) and include a sentinel control for each channel.

**Issue: The shape of the O6 pin in AC11 is not concrete enough.**
· Severity: **Minor**
· Evidence: AC11 says one case asserts the reader's behavior on a full profile fixture and records that the live endpoint returns only `sub`. Without a concrete test shape, closing `spec/auth-service/design.md` O6 could silently change the meaning of a passing test from "handles the aspirational shape" to "matches live behavior today."
· Recommended brief amendment: Use two explicit test rows: (1) `mocks a complete UserInfo response (full name/email/verified claims)` — always runs; (2) `live /userinfo returns only sub — pending O6` — skipped with `it.skip(...)` and a comment citing O6, so enabling it is a deliberate act when O6 closes.

**Issue: The reader's return type and error contract are unspecified.**
· Severity: **Minor**
· Evidence: The brief says "a typed profile value ... returned to a caller in memory" but does not say whether `readProfile` returns a `Promise<Profile>`, a `Promise<Result<Profile, Failure>>`, or throws. AC8's failure-surface requirement depends on this shape.
· Recommended brief amendment: Specify `readProfile(): Promise<Profile>` where `Profile` is derived from the generated `UserInfoResponse`, and define failure as a rejected promise carrying a neutral, detail-free `ReadProfileError`. Add a control that proves throwing a server-detail error causes AC8 to fail.

---

**Phase 3 complete.** Ten findings returned; no code or contract changed.
