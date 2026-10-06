# 7. Tasks — ordered execution plan

Execute in order. Each task leaves the app buildable and the test suite green. Phase 1a is the auth gate and ships first. A task
that depends on an open blocker is marked DRAFT and waits for its `Q#`. Each task is one physical line so the generator can read its cites.

## Phase 1 — Foundation

1. **Scaffold the PWA.** Create the Vite, React, and TypeScript app under `frontend/` with React Router under the `/app` prefix, TanStack Query, Zustand, `vite-plugin-pwa`, Vitest, and Playwright. Cites L13, L14.
2. **Wire the generated client.** Generate `libs/ts/api-client` from `contracts/api/auth.yaml` in CI, and add a lint rule that fails on any hand-written `fetch` to a backend route. Cites L6.
3. **Build the client boundary.** Implement single-flight 401 renewal, fixed generic 429 handling, and network states at the client wrapper, not per screen. Cites R21, R23, L6, L18.
4. **Service-worker boundaries.** Configure the navigation denylist, NetworkOnly for API routes, the no-cache rule for sensitive bodies, and the prompt-to-reload update policy. Cites R57, L17.
5. **Bundle secret scan.** Add a CI check that fails the build if a secret value or a non-public environment key appears in the bundle. Cites R58, L10.

## Phase 1 — Auth & account (gatekeeper)

6. **Session owner and OIDC client.** Implement the PKCE authorization-code flow, `state` and `nonce` validation, exact-match redirect URIs, and the return-target allowlist. Cites L1, L16, R6, R7, R8.
7. **Token storage per D-012.** Keep the access token in memory only and renew through the SAS session. No tokens in any storage. Cites L3, R7, R19. Blocked on Q5 for any refresh-family behaviour.
8. **Route guards and the public route set.** Implement the exact public route set from `design.md` §4c and redirect every other route to sign-in without a session. Cites L14, R28.
9. **Sign-in through SAS.** Start login by redirecting to `/oauth2/authorize` with `openid profile email`, exchange the code at `/oauth2/token`, and render no password field. Cites L2, R6, R9.
10. **Registration.** Build the form with advisory 12-to-128 code-point guidance and the uniform acknowledgement screen. Cites R1, R2, L4.
11. **Verify from link and resend.** Build verify-from-link with one uniform failure, and the public resend form with a uniform acknowledgement. Cites R3, R4, R5, R30.
12. **Password reset and change.** Build the reset request, reset-from-token with a same-device sign-in message, and change-password requiring the current password. Cites R14, R15, R16, R17. Q15 governs the cross-device claim.
13. **Sessions and sign-out.** Build the session list with fallback labels and rotation time, revoke actions, and sign-out with `/oauth2/revoke` plus the OIDC end-session call. Cites R18, R19. Blocked on Q5 for the family list.
14. **Profile from userinfo and account status.** Read profile from `/userinfo` and account status from `GET /accounts/me`, never from the access token. Cites R20.
15. **Renewal failure and cross-tab coherence.** Route a failed renewal to clean re-auth with no half-session, share one renewal across sibling tabs, and propagate sign-out to every tab. Cites R21, R22, L18. The reuse half of R22 is blocked on Q5.
16. **Expired session mid-task.** Keep only non-sensitive input in sessionStorage with a 15-minute TTL, and keep secrets and PII out of storage. Cites R29, L16.
17. **Enumeration-safe error pass.** Compare every identity screen side by side across all account and token states in R30. Cites R30, L4.
18. **Voluntary MFA enrollment (DRAFT, blocked on Q1).** Build the wizard: URI and QR, live-code confirm, recovery codes shown once with acknowledgement. Cites R11, R13, L5. Blocked on Q1.
19. **MFA disable (DRAFT, blocked on Q1).** Require the current password and a valid TOTP before the disable call. Cites R12. Blocked on Q1.
20. **Privileged-account bootstrap (DRAFT, blocked on Q13).** Implement the path chosen for enrolling unenrolled MERCHANT and ADMIN accounts, and verify that the SAS refusal shows wrong-password copy. Cites R10, L5. Blocked on Q13.
21. **Merchant API keys.** Build create with plaintext shown once and acknowledgement, list with contract fields only, and revoke with confirmation. Cites R24, R25, R26. Prefix and MFA problem type are blocked on Q12.
22. **Admin area by role matrix.** Gate the area on ADMIN and COMPLIANCE and offer exactly the operations R27 lists, one test per operation and role. Cites R27. The status view is blocked on Q14.

## Phase 1 — Payment verification & invoicing

23. **Invoices.** Build the invoice list and detail with the states from R31 and decimal-string amounts. Cites R31, L7. Blocked on Q2.
24. **Payment state machine view.** Show the current state, reorg reversals, `HELD` from any state, and the neutral hold copy. Cites R32, R56, L8. Blocked on Q2.
25. **Receipts.** Show no receipt before `ATTESTED`, and show the hold state alongside the receipt after an attested reorg. Cites R33, R34, L8. Blocked on Q2.
26. **Wallet monitoring and unknown-token state.** Build the wallet setup and show unknown-token and confirmation-count conditions. Cites R38, R39. Blocked on Q2.
27. **Address-poisoning warnings.** Show the warning on any payment and receipt flagged for address poisoning. Cites R55. Blocked on Q2.
28. **In-app notification list.** Build the list with reconnecting and no-duplicate states on top of the chosen transport. Cites R35. Blocked on Q3.
29. **Notification stream authentication.** Implement the stream authentication option chosen in O7, never placing a token in a URL, and reopen the stream after renewal. Cites R59. Blocked on Q3 and O7.
30. **History, exports, and base-unit scaling.** Show merchant-scoped history and exports, and scale token base units without `Number`. Cites R36, R37. Blocked on Q2.
31. **Phase 1 release gate.** Run the full Playwright OIDC round trip, accessibility checks, the bundle secret scan, and the enumeration-safety pass against a faithful identity provider. Cites L1, L4, L10, L11.

## Phase 2 — Intelligence engine (DRAFT, blocked on Q4)

32. **Evidence upload.** Build upload with progress and safe failure reasons. Cites R40. Blocked on Q4.
33. **Evidence analysis view.** Show extracted fields and the integrity assessment, with low confidence labelled against the contract threshold. Cites R41. Blocked on Q4.
34. **Transaction-hash collection.** Show automatically collected evidence and mark what the user still needs to upload. Cites R42. Blocked on Q4.
35. **Evidence graph.** Show the correlated graph and narrative, with partial analysis marked. Cites R43. Blocked on Q4.

## Phase 3 — Dispute resolution (DRAFT, blocked on Q4)

36. **Dispute case view.** Show each claim as supported, contradicted, or incomplete. Cites R44. Blocked on Q4.
37. **Dispute narrative and timeline.** Show the narrative with AI text distinct from facts, and the timeline in the declared order. Cites R45, R46. Blocked on Q4.
38. **Smart-contract evidence.** Show contract execution evidence, marking failures as observed. Cites R47. Blocked on Q4.

## Phase 4 — Reputation & trust (DRAFT, blocked on Q4)

39. **Wallet reputation.** Show contributing signals, not only a score. Cites R48. Blocked on Q4.
40. **Trust passport and counterparty graph.** Show metrics with source windows, and the behaviour graph with cluster evidence. Cites R49, R50. Blocked on Q4.

## Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4)

41. **Fraud alerts.** Show classification and evidence, distinguishing confirmed from suspected. Cites R51. Blocked on Q4.
42. **Cross-chain relationships.** Show the chains and linking evidence for one commercial relationship. Cites R52. Blocked on Q4.
43. **Institutional API portal.** Apply the plaintext-once rule to portal keys. Cites R53. Blocked on Q4.

## Cross-phase — Capability links (DRAFT, blocked on Q11)

44. **Capability links.** Serve payer and passport links through unguessable identifiers, with no PII and no caching, after an ADR amends L14. Cites R54. Blocked on Q11.
