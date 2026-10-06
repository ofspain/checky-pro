# 7. Tasks — ordered execution plan

Execute in order. Each task leaves the app buildable and the test suite green. Phase 1a is the auth gate and
ships first; nothing else is reachable without a session. A task that depends on an open blocker is marked
DRAFT and waits for its `Q#`.

## Phase 1 — Foundation

1. **Scaffold the PWA.** Create the Vite, React, and TypeScript app under `frontend/` with React Router, TanStack
   Query, Zustand, and `vite-plugin-pwa`. Add Vitest and Playwright. Cites L13.
2. **Wire the generated client.** Generate `libs/ts/api-client` from `contracts/api/auth.yaml` in CI. Add a lint
   rule that fails on any `fetch` to a backend route. Cites L6, R47.
3. **Build the client boundary.** Implement 401 refresh-once, 429 `Retry-After` backoff, and network states at
   the client wrapper, not per screen. Cites R21, R23, L6.

## Phase 1 — Auth & account (gatekeeper)

4. **Session owner and OIDC client.** Implement the PKCE authorization-code flow, `state` validation,
   exact-match redirect URIs, and the return-target allowlist. Cites L1, R6, R8.
5. **Token storage per O1.** Implement the chosen token mechanism with no `localStorage` or `sessionStorage` for
   tokens. Add the test `tokensAreNeverWrittenToLocalOrSessionStorage`. Cites L3, R7, R19. Blocked on Q5.
6. **Route guards.** Public routes are sign-in start, callback, verify-from-link, and reset entry. Every other route
   redirects to sign-in without a session. Cites L14, R28.
7. **Sign-in through SAS.** Start login by redirecting to `/oauth2/authorize`, exchange the code at `/oauth2/token`,
   and render no password field. Cites L2, R6, R9.
8. **Registration.** Build the form with the 12-to-128 guidance and the uniform acknowledgement screen. Cites R1, R2,
   L4.
9. **Verify from link and resend.** Build the verify-from-link view with one uniform failure, and the resend action
   with a uniform acknowledgement. Cites R3, R4, R5, R15, R30.
10. **Password reset and change.** Build the reset request (uniform ack), reset-from-token (sessions revoked, sign
    in again), and change-password (requires current password). Cites R13, R14, R15, R16, R17.
11. **Sessions and sign-out.** Build the session list with device label and rotation, revoke one and revoke all,
    and sign-out with `/oauth2/revoke`. Cites R18, R19.
12. **Profile from userinfo.** Read profile data from `/userinfo` only. Cites R20.
13. **Reuse detection and clean re-auth.** Route a reused refresh token to clean re-auth with no half-session. Cites
    R22, L3.
14. **Expired session mid-task.** Preserve non-sensitive input across re-auth and keep secrets out of storage. Cites
    R29.
15. **Enumeration-safe error pass.** Compare every identity screen side by side for unknown, locked, suspended, and
    missing accounts. Cites R30, L4.
16. **MFA enrollment wizard (DRAFT, blocked on Q1 and Q10).** Build the blocking step-by-step wizard: QR and URI,
    live-code confirm, recovery codes shown once. Cites R10, R11, L5. Blocked on Q1, Q10.
17. **MFA disable (DRAFT, blocked on Q1).** Require current password and valid TOTP before the disable call. Cites
    R12. Blocked on Q1.
18. **Merchant API keys.** Build create (plaintext shown once with acknowledgement), list (no secrets), and revoke
    (with confirmation). Cites R24, R25, R26.
19. **Admin area.** Gate the admin area on the admin role and offer only the actions the admin contract defines. Cites
    R27.

## Phase 1 — Payment verification & invoicing

20. **Invoices.** Build the invoice list and detail, with amounts as decimal strings. Cites R31, L7. Blocked on Q2.
21. **Payment state machine view.** Show the current state, reorg reversals, and `HELD` truthfully. Cites R32, L8.
    Blocked on Q2.
22. **Receipts.** Show a receipt only from `FINALIZED` or `ATTESTED`. Cites R33, R34, L8. Blocked on Q2.
23. **In-app notifications.** Build the SSE list with reconnecting and no-duplicate states. Cites R35. Blocked on Q3.
24. **History and exports.** Show merchant-scoped history and exports. Cites R36, R37. Blocked on Q2.
25. **Phase 1 release gate.** Run the full Playwright OIDC round trip, the accessibility checks, and the
    enumeration-safety pass against a faithful identity provider. Cites L1, L4, L11.

## Phase 2 — Intelligence engine (DRAFT, blocked on Q4)

26. **Evidence upload.** Build upload with progress and safe failure reasons. Cites R38. Blocked on Q4.
27. **Evidence analysis view.** Show extracted fields and the integrity assessment, with low confidence labelled. Cites
    R39. Blocked on Q4.
28. **Evidence graph.** Show the correlated graph and narrative, with partial analysis marked. Cites R40. Blocked on Q4.

## Phase 3 — Dispute resolution (DRAFT, blocked on Q4)

29. **Dispute case view.** Show claims with supported, contradicted, or incomplete status. Cites R41. Blocked on Q4.
30. **Dispute narrative.** Show the timeline, facts, evidence, confidence, and recommendation, with AI text distinct
    from facts. Cites R42. Blocked on Q4.

## Phase 4 — Reputation & trust (DRAFT, blocked on Q4)

31. **Wallet reputation.** Show contributing signals, not only a score. Cites R43. Blocked on Q4.
32. **Trust passport.** Show merchant metrics with their source windows. Cites R44. Blocked on Q4.

## Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4)

33. **Fraud alerts.** Show classification and evidence, distinguishing confirmed from suspected. Cites R45. Blocked on Q4.
34. **Institutional API portal.** Apply the plaintext-once key rule to the portal. Cites R46. Blocked on Q4.
