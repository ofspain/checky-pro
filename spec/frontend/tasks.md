# 7. Tasks — ordered execution plan

Execute in order. Each task leaves the app buildable and the test suite green. Phase 1a is the auth gate and ships first; nothing else is reachable without a session. A task that depends on an open blocker is marked DRAFT and waits for its `Q#`. Each task is one physical line so the generator can read its cites.

## Phase 1 — Foundation

1. **Scaffold the PWA.** Create the Vite, React, and TypeScript app under `frontend/` with React Router, TanStack Query, Zustand, `vite-plugin-pwa`, Vitest, and Playwright. Cites L13.
2. **Wire the generated client.** Generate `libs/ts/api-client` from `contracts/api/auth.yaml` in CI, and add a lint rule that fails on any hand-written `fetch` to a backend route. Cites L6.
3. **Build the client boundary.** Implement single-flight 401 renewal, fixed generic 429 handling, and network states at the client wrapper, not per screen. Cites R21, R23, L6, L18.
4. **Service worker boundaries.** Configure the navigation denylist, NetworkOnly for API routes, and the no-cache rule for sensitive bodies. Cites L17, R7.

## Phase 1 — Auth & account (gatekeeper)

5. **Session owner and OIDC client.** Implement the PKCE authorization-code flow, `state` and `nonce` validation, exact-match redirect URIs, and the return-target allowlist. Cites L1, L16, R6, R7, R8.
6. **Token storage per D-012.** Keep the access token in memory only and use silent re-authorization through the SAS session. No tokens in localStorage or sessionStorage. Cites L3, R7, R19. Blocked on Q5 for any refresh-family behaviour.
7. **Route guards and the public route set.** Implement the exact public route set and redirect every other route to sign-in without a session. Cites L14, R28, R30.
8. **Sign-in through SAS.** Start login by redirecting to `/oauth2/authorize` with `openid profile email`, exchange the code at `/oauth2/token`, and render no password field. Cites L2, R6, R9.
9. **Registration.** Build the form with advisory 12-to-128 code-point guidance and the uniform acknowledgement screen. Cites R1, R2, L4.
10. **Verify from link and resend.** Build verify-from-link with one uniform failure, and the public resend form with a uniform acknowledgement. Cites R3, R4, R5, R30.
11. **Password reset and change.** Build the reset request, reset-from-token with sessions revoked and sign-in again, and change-password requiring the current password. Cites R14, R15, R16, R17.
12. **Sessions and sign-out.** Build the session list with fallback labels, rotation time, and revoke actions, and implement sign-out with `/oauth2/revoke` plus the OIDC end-session call. Cites R18, R19.
13. **Profile from userinfo and account status.** Read profile from `/userinfo` and account status from `GET /accounts/me`, never from the access token. Cites R20.
14. **Reuse detection and clean re-auth.** Route a failed renewal or a superseded token to clean re-auth, and propagate sign-out across tabs. Cites R22, L18.
15. **Expired session mid-task.** Keep non-sensitive input in sessionStorage with a 15-minute TTL and keep secrets out of storage. Cites R29, L16.
16. **Enumeration-safe error pass.** Compare every identity screen side by side across unknown, missing, locked, suspended, and deleted accounts, and across bad, expired, and used tokens. Cites R30, L4.
17. **Voluntary MFA enrollment (DRAFT, blocked on Q1).** Build the wizard: URI and QR, live-code confirm, recovery codes shown once with acknowledgement. Cites R11, R13, L5. Blocked on Q1.
18. **MFA disable (DRAFT, blocked on Q1).** Require the current password and a valid TOTP before the disable call. Cites R12. Blocked on Q1.
19. **First-login enrollment is SAS-hosted (DRAFT, blocked on Q1 and Q10).** Verify that the SAS flow enforces enrollment for privileged accounts before a code is issued, and add `firstLoginPrivilegedEnrollmentRunsInsideSasBeforeAuthorizationCode`. Cites R10, L5. Blocked on Q1, Q10.
20. **Merchant API keys.** Build create with plaintext shown once and acknowledgement, list with contract fields only, and revoke with confirmation. Cites R24, R25, R26. Prefix and MFA problem type are PROPOSED under Q12.
21. **Admin area by role matrix.** Gate the area on ADMIN and COMPLIANCE, and offer exactly the operations the role matrix lists. Cites R27, L4.

## Phase 1 — Payment verification & invoicing

22. **Invoices.** Build the invoice list and detail with the states from R31 and decimal-string amounts. Cites R31, L7. Blocked on Q2.
23. **Payment state machine view.** Show the current state, reorg reversals, and `HELD` truthfully. Cites R32, L8. Blocked on Q2.
24. **Receipts.** Show no receipt before `ATTESTED`, and show the hold state alongside the receipt after an attested reorg. Cites R33, R34, L8. Blocked on Q2.
25. **Wallet monitoring and unknown-token state.** Build the wallet setup and show unknown-token and confirmation-count conditions. Cites R38, R39. Blocked on Q2.
26. **In-app notifications.** Build the SSE list with reconnecting and no-duplicate states. Cites R35. Blocked on Q3.
27. **History, exports, and base-unit scaling.** Show merchant-scoped history and exports, and scale token base units without `Number`. Cites R36, R37. Blocked on Q2.
28. **Phase 1 release gate.** Run the full Playwright OIDC round trip, the accessibility checks, the bundle secret scan, and the enumeration-safety pass against a faithful identity provider. Cites L1, L4, L10, L11.

## Phase 2 — Intelligence engine (DRAFT, blocked on Q4)

29. **Evidence upload.** Build upload with progress and safe failure reasons. Cites R40. Blocked on Q4.
30. **Evidence analysis view.** Show extracted fields and the integrity assessment, with low confidence labelled. Cites R41. Blocked on Q4.
31. **Transaction-hash collection.** Show automatically collected evidence and mark what the user still needs to upload. Cites R42. Blocked on Q4.
32. **Evidence graph.** Show the correlated graph and narrative, with partial analysis marked. Cites R43. Blocked on Q4.

## Phase 3 — Dispute resolution (DRAFT, blocked on Q4)

33. **Dispute case view.** Show each claim as supported, contradicted, or incomplete. Cites R44. Blocked on Q4.
34. **Dispute narrative and timeline.** Show the narrative with AI text distinct from facts, and the reconstructed timeline. Cites R45, R46. Blocked on Q4.
35. **Smart-contract evidence.** Show contract execution evidence, marking failures as observed. Cites R47. Blocked on Q4.

## Phase 4 — Reputation & trust (DRAFT, blocked on Q4)

36. **Wallet reputation.** Show contributing signals, not only a score. Cites R48. Blocked on Q4.
37. **Trust passport and counterparty graph.** Show metrics with source windows, and the behaviour graph with cluster evidence. Cites R49, R50. Blocked on Q4.

## Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4)

38. **Fraud alerts.** Show classification and evidence, distinguishing confirmed from suspected. Cites R51. Blocked on Q4.
39. **Cross-chain relationships.** Show the chains and linking evidence for one commercial relationship. Cites R52. Blocked on Q4.
40. **Institutional API portal.** Apply the plaintext-once rule to portal keys. Cites R53. Blocked on Q4.

## Cross-phase — Capability links (DRAFT, blocked on Q11)

41. **Capability links.** Serve payer and passport links through unguessable identifiers, with no PII and no caching, after an ADR amends L14. Cites R54. Blocked on Q11.
