# Frontend spec — adversarial review resolution (v0.1 → v0.1 revised)

Reviewer: fresh-context adversarial subagent (general-purpose, model Opus, distinct from the authoring model). Findings
were checked against source before adjudication. Findings were accepted unless noted.

## Verified before adjudication

- `services/auth/docs/architecture/auth-decisions.md` D-012 selects "pure PKCE public client — access token in memory, rotating
  refresh token via the OIDC client, SAS httpOnly session cookie enables silent re-auth". The draft's O1 left this open.
- `RegisteredClientSeeder.java:53` registers `checky-spa` with `ClientAuthenticationMethod.NONE`. The client is public.
- `contracts/api/auth.yaml` `/accounts/resend-verification` declares `security: []`.
- `.ai/generate.py` `parse_tasks` reads only the numbered line (`^(\d+)\.\s+(.*)$`). Cites on wrapped lines are dropped.
- **Not verified by the author**: the reviewer's claim that SAS 1.5.1 `OAuth2RefreshTokenGenerator` withholds a refresh token from a
  public client on the authorization-code grant. Accepted as a verification task (Q5), not as a fact.

## Adjudication

| # | Finding (severity) | Disposition | Where the fix lands |
|---|---|---|---|
| 1 | Refresh-token model unworkable for the public client (Blocker) | ACCEPTED | `agents.md` identity rule; `design.md` L3, O1; R19, R21, R22; Q5 |
| 2 | Forced MFA enrollment cannot run in the SPA before a code (Blocker) | ACCEPTED | `agents.md` MFA rule; L5 (closes O5); R10, R11; Q10; task 19 |
| 3 | Public-route lock leaves registration and pre-auth screens unreachable (Blocker) | ACCEPTED | L14 exact public set; R28; named test `publicRouteSetIsExactlyTheDeclaredSet` |
| 4 | Generator drops cites on continuation lines (Major) | ACCEPTED | `tasks.md` one physical line per task; parser check: 41/41 tasks parsed |
| 5 | Money lock lets a junior show raw base units (Major) | ACCEPTED | `agents.md` money rule; L7; R37; `baseUnitAmountsScaledByTokenDecimalsWithoutNumber` |
| 6 | Receipt at FINALIZED conflicts with payment-service; HELD after ATTESTED unhandled (Major) | ACCEPTED | `agents.md` state rule; L8; R33, R34; `receiptShowsHoldAfterAttestedReorg` |
| 7 | Phase 1b ignores payment-service invoice states (Major) | ACCEPTED | R31 states (PROPOSED); Q2 points to payment-service design |
| 8 | Service-worker fallback can hijack SAS navigations (Major) | ACCEPTED | `agents.md` service-worker rule; L17; `sasNavigationBypassesServiceWorker` |
| 9 | Sign-out leaves the SAS session alive (Major) | ACCEPTED | `agents.md` sign-out rule; R19; `signOutThenSignInRequiresCredentials` |
| 10 | PKCE state and verifier storage unspecified across redirect (Major) | ACCEPTED | L16 carve-out; R8; `stateSurvivesRedirectAndIsConsumedOnce` |
| 11 | Concurrent and multi-tab refresh races (Major) | ACCEPTED | `agents.md` client boundary; L18; R21; `parallel401sTriggerExactlyOneRenewal` |
| 12 | 429 requirement uses an absent header and conflicts with the no-timer rule (Major) | ACCEPTED | R23 fixed copy, no countdown, no replay; `Retry-After` PROPOSED under Q12 |
| 13 | Resend-verification contradicts the contract (Major) | ACCEPTED | R5 public form; backend R6 divergence raised as Q12 |
| 14 | R25 and R18 rely on fields the contract lacks (Major) | ACCEPTED | R25 uses `ApiKeyMetadata` fields; R18 fallback label and `rotatedAt`; prefix PROPOSED under Q12 |
| 15 | Admin scope incomplete, excludes COMPLIANCE, conflicts with L4 (Major) | ACCEPTED | R27 role matrix with named operations; L4 carve-out for privileged admin views |
| 16 | Profile and scope requirements underspecified (Major) | ACCEPTED | `agents.md` scope rule; R20 (`openid profile email`, `/accounts/me`) |
| 17 | Task cites undefined R47 (Major) | ACCEPTED | Task 2 cites L6 only; the cite that generated the fault is removed |
| 18 | Roadmap capabilities silently dropped (Major) | ACCEPTED | Added R38, R39 (wallet monitoring, unknown token), R42 (tx-hash), R46 (timeline), R47 (smart contract), R50 (counterparty graph), R52 (cross-chain) |
| 19 | Payer and public passport conflict with the public-route lock (Major) | ACCEPTED | R54 capability links; L14 exception only via ADR; Q11 |
| 20 | Security claims in §8 have no named tests (Major) | ACCEPTED | Security test block in `package.md` §8; every R1–R54 has a test |
| 21 | Rollout and rollback internally inconsistent (Minor) | ACCEPTED | `package.md` §10 rewritten: flag source, kill switch, SW update policy, rebuild rollback |
| 22 | Q1 understates the MFA gap and misstates backend status (Minor) | ACCEPTED | Q1 names the missing controller (T19), SAS step (T20), and recovery-code regeneration |
| 23 | Several requirements are not EARS or not testable (Minor) | ACCEPTED | R2, R13, R27, R28, R37, R46 rewritten |
| 24 | R30 narrows the enumeration lock (Minor) | ACCEPTED | R30 covers unknown, missing, locked, suspended, deleted, and token states |
| 25 | API-key MFA precondition cannot be determined by the SPA (Minor) | ACCEPTED | R24 maps the server rejection to fixed copy; problem type PROPOSED under Q12 |
| 26 | 12–128 check ambiguous on the client (Minor) | ACCEPTED | R1 counts Unicode code points; client check is advisory, server authoritative |

## Rejected findings

None. Every finding was either fixed in the package or, where the reviewer's premise is not yet verified (finding 1's SAS
refresh-token claim), converted into an owner-verifiable question (Q5) instead of an assertion.

## Re-verification

- `python3 .ai/generate.py --check`: `frontend` discovered, 41 tasks.
- Parser check with the generator's own `parse_tasks` and `parse_test_map`: 41/41 tasks carry IDs, 54/54 requirements carry
  at least one named test, no duplicate requirement IDs, 62 test-map entries.

## Remaining items for the author

See `package.md` §11 (Q1–Q12). A phase stays DRAFT while its blockers are open. No blocker from the review is left unrecorded.

## Second adversarial pass — dispositions

Second pass: fresh-context reviewer (Opus), same brief. Every finding was checked against source before acceptance. All 15
findings were ACCEPTED. The first finding was verified directly: `TotpAuthenticationProvider` (auth T20) is built, and it refuses
unenrolled MERCHANT and ADMIN accounts with the wrong-password error without enrolling them. The package had wrongly said T20 was
not built.

| # | Finding | Disposition |
|---|---|---|
| 1 | First-login enrollment described as built inside SAS (Blocker) | ACCEPTED. L5, R10, Q10, Q13 rewritten. Bootstrap path left open as Q13. |
| 2 | R27 role matrix contradicted auth.yaml (Major) | ACCEPTED. Derived operation by operation from `bearerAuth`. One test per operation. |
| 3 | L4 carve-out overrode an `[ALL]` rule without an ADR (Major) | ACCEPTED. Carve-out removed. Admin status view blocked on Q14. |
| 4 | Refresh-family dependency and D-012 misquoted (Major) | ACCEPTED. D-012 quoted verbatim. R18, R19, R22 blocked on Q5. Backend R39 cite dropped. |
| 5 | Revoke and reset do not end SAS sessions (Major) | ACCEPTED. R15 limited to this device. Q15 asks the backend. |
| 6 | Service-worker denylist missed the end-session path (Major) | ACCEPTED. Added `/connect/logout`, `/logout`, `/error`. R57 and test added. |
| 7 | SPA routes collided with backend prefixes (Major) | ACCEPTED. All SPA routes under `/app`. Exact list in `design.md` §4c (PROPOSED, Q6). |
| 8 | SSE stream could leak a token into a URL (Major) | ACCEPTED. R59 and O7 added. Blocked on Q3. |
| 9 | Address-poisoning flag not displayed (Major) | ACCEPTED. R55 added, blocked on Q2. |
| 10 | HELD entry paths misread (Minor) | ACCEPTED. R56 added. HELD may be entered from any state. Q2 now depends on payment-service Q1. |
| 11 | Blocker IDs disagreed across files (Minor) | ACCEPTED. R24 and R25 use Q12. Q5 added to the Phase 1a row. |
| 12 | Storage rules contradicted each other (Minor) | ACCEPTED. Stale O1 reference removed from `agents.md`. L16 carve-out moved into `agents.md`. PII excluded from R29. Test renamed to cover all storage. |
| 13 | Tests mapped to unrelated requirements; L17 and L10 untested (Minor) | ACCEPTED. R57 and R58 added with their own tests. Stale mappings removed. |
| 14 | Several EARS requirements untestable (Minor) | ACCEPTED. R10 restated as SPA behaviour. R41 threshold tied to contract. R46 event order given. R30 limited to SPA-observable copy. |
| 15 | Rollout service-worker policy inconsistent (Minor) | ACCEPTED. One worker-wide policy (prompt-to-reload). Rollback uses a remote denylist with forced reload. |

**Not re-reviewed.** These revisions were not put through a third adversarial pass. The prompt requires one whenever blocker or major
items remain shipped, and the first-pass blockers were fixed in this round. Any reviewer should start from this table.

**Parser checks after revision**: 59 requirements, no duplicate IDs, every requirement has a named test. 44 tasks, each carrying cites.
No dangling R or L references. `generate.py --check` discovers `frontend` with 44 tasks.
