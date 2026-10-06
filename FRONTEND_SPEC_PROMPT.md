# Prompt — Spec out the complete Frontend roadmap (Checky Pro / Themistra) with the `spec-authoring` skill

Give this prompt to **Claude Opus** to author the **full-product frontend feature-spec package** for
this repository — every phase of the product roadmap through the final, go-to-market implementation —
using the frontend capability of the `spec-authoring` skill. The output plugs straight into this
repo's `.ai/` engineering framework, the same 14-phase prompt pipeline that already drives `auth`,
`crypto`, `notification`, and `payment`.

---

## 0. Role

You are a **Principal Product/System Architect** for Checky Pro (Themistra), an AI-powered trust
layer for blockchain commerce, and the owner of its **frontend specification across the full product
roadmap**. You do NOT write application code. You author specifications that a junior engineer +
Claude Code can implement with minimal back-and-forth, using the `spec-authoring` skill.

You are the **main authoring agent**. This session has a mandatory **adversarial review pass**
(§8 below): after you draft the full spec package, a fresh-context adversarial reviewer attacks it,
and you adjudicate and fold in accepted findings before the package ships. Nothing ships unreviewed.

## 1. Mission

Author the **complete frontend feature-spec package** — all five product phases, in release order,
ending in the market-ready implementation — for the React + TypeScript mobile-first **PWA** under
`frontend/`, writing it to **`spec/frontend/`** in the exact package format this repo already uses
for its backend services, so that `python3 .ai/generate.py` discovers it and generates the identical
14-phase task workflows under `.ai/prompts/frontend/`.

The product roadmap that MUST be spec'd, from `new_features.md` and the `ARCHITECTURE.md` extension
points:

- **Phase 1 — Authentication & Account UI, the GATEKEEPER (day one, before anything else is usable).**
  The auth service is the identity issuer and front door to the whole application (`spec/auth-service/`
  is authoritative — see §3). The UI that serves it must ship with the first release:
  - **Registration & email verification**: create account (email + platform password policy: 12–128
    chars, breached-password screening, no composition rules), uniform enumeration-safe 202
    "check your email" ack, verify-from-link / verification-token entry, resend verification.
  - **Login & sessions**: the app is a **public OIDC client** of Spring Authorization Server — login is
    the SAS interactive flow (authorize → SAS-hosted password step → SAS MFA step → auth code → SPA
    PKCE exchange), with a short-lived (10-min) access token + rotating refresh token families,
    reuse-detection → forced clean re-auth, session listing with device labels and last-rotation,
    revoke one / revoke all, logout/revoke, and userinfo-derived profile. 401 interception and 429
    backoff are first-class UI states.
  - **MFA (TOTP) day one**: for merchants and admins it is mandatory **before** any
    authorization code — so the first login of a privileged account must walk through an in-app
    **enrollment wizard** (provisioning `otpauth://` URI / QR, confirm with a live code, show the 10
    single-use recovery codes exactly once), then a **TOTP/recovery-code challenge** step inside the
    login flow, and **MFA management** (view, disable with current password + valid code).
  - **Password lifecycle**: reset request (uniform enumeration-safe ack), reset from token-link +
    new password (revokes all sessions), change own password (requires current).
  - **Merchant API keys**: create (MFA confirmed, `ck_live_`-prefixed, **plaintext shown exactly
    once**), list (no secrets), revoke — visibly connected to API-first flows.
  - **Enumeration safety extends to UI pixels, not just APIs**: copy, redirect targets, and timing
    must remain indistinguishable for unknown / locked / suspended / missing accounts and tokens.
  - **Admin**: account activation/unlock/suspend/reinstate/roles surfaces (bounded to what the CSP
    ships; admin role gates the screen, not a separate app).
- **Phase 1 — Blockchain Payment Verification (marketable day one).** Wallet monitoring/verification,
  token validation, confirmations, tamper-proof receipts, professional invoicing, payment state
  machine (`CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`, reorg + `HELD`),
  notification (email + in-app), tax-ready transaction history/exports, merchant-scoped data.
  Auth/account surfaces from the previous group stay on screen as profile/settings destinations;
  nothing here is reachable without a valid session.
- **Phase 2 — Themistra Intelligence Engine.** AI evidence interpretation (uploaded screenshots,
  exchange receipts, explorer pages, payment confirmations → extracted structured transaction data),
  evidence integrity analysis (metadata, editing detection, timestamps, provenance), multi-evidence
  correlation into an **evidence graph** / unified transaction narrative, automatic evidence
  collection from a tx hash.
- **Phase 3 — AI-Assisted Dispute Resolution.** Intelligent claim validation (supported /
  contradicted / incomplete), AI dispute narratives (timeline, verified facts, conflicting + supporting
  evidence, confidence, recommended resolution), chain-aware timeline reconstruction, smart-contract
  dispute analysis.
- **Phase 4 — Reputation & Trust Intelligence.** Wallet reputation profiles, merchant **Trust
  Passport**, counterparty behaviour graph.
- **Phase 5 — Fraud Intelligence Platform.** AI fraud detection (scam wallets, fraud rings, synthetic
  patterns, coordinated attacks, address poisoning), institutional intelligence / API portal for
  exchanges, fintechs, banks, regulators, cross-chain intelligence.

Each phase is a releasable, marketable slice; later phases build on earlier ones as new consumers of
existing events (`ARCHITECTURE.md` §1 principle 5), never as rewrites. The auth/account UI is the
foundation everything else hangs off — it ships **day one, Phase 1**, and its platform-level locks
(public OIDC client, session/token handling, enumeration-safe copy, MFA-first privilege) must survive
all five phases untouched.

The backend spec packages (`spec/auth-service/`, `spec/payment-service/`, …) are your format
exemplar. **Match their file structure, section discipline, ID conventions, and tone** — and note how
`spec/payment-service/tasks.md` groups tasks under `##` section headers; your phases will be your
`##` sections.

## 2. Skill — load it first

- Load the **`spec-authoring`** skill (it is on your agent skills list; source lives at
  `/Users/oluwafemi.ayeni/Downloads/skills/spec-authoring`). Follow `SKILL.md` step-by-step — do not
  invent a different workflow.
- The skill's domain references: `references/backend.md`, `references/react-native.md`,
  `references/react-native-gaps.md`, `assets/FEATURE_SPEC_TEMPLATE.md`.
- **Frontend capability.** The skill says to "create/use `references/frontend.md` following the same
  pattern" for web-frontend projects. **`references/frontend.md` does not exist yet** in the skill
  folder. **Create it** as part of this task, modeled on `references/backend.md` and informed by
  `react-native.md` + `react-native-gaps.md` (adapt the RN-specific advice for a **mobile-first web
  PWA**, not a native app: no Keychain/Keystore — address secure browser storage; add PWA/service-
  worker caching, OIDC/PKCE public-client flows with a server-side-auth redirect dance, the
  single-origin edge model, and the notion of a long-lived multi-phase product whose design must not
  paint later phases into a corner). Then author the spec using it. Cover at least: framework/bundler
  detection, routing (call out how auth-gated routes, account/settings, and later-phase deep workspaces
  compose), state management, HTTP client (this repo's generated TS client), secure token/session
  storage for the web, design system integration, PWA/offline, i18n, a11y, analytics/logging,
  push/in-app notifications, testing, and bundle/deploy concerns.

## 3. Repository context — read first, bounded to the frontend

Do NOT crawl the repo. Read, in this order, then stop:

1. `README.md` (repo map + ground rules) and `ARCHITECTURE.md` (system design, §3 services, §4 events,
   verification state machine, edge model, and the **Phase 2–5 extension points** in §1 principle 5).
2. `new_features.md` — the **authoritative product roadmap** for Phases 1–5 (the list in §1 is a
   condensation; the document is the source of truth).
3. `frontend/README.md` (the existing frontend intent) and the current state of `frontend/`.
4. `spec/auth-service/` end to end — **`requirements.md` (R1–R48), `agents.md`, and `package.md`** — this
   is the authoritative source for the auth/account UI surface. Confirm which auth endpoints exist in
   `contracts/api/auth.yaml` and which are pending (see step 6). Also read `.ai/prompts/auth/README.md`
   to see the task set (e.g. T16–T22 MFA, T28–T30 sessions) the SPA will depend on.
5. `.ai/WORKFLOW.md` + `.ai/README.md` (the pipeline that will consume your package) and
   `.ai/generate.py` — **read the parsing logic** so your `tasks.md` / `requirements.md` /
   `design.md` / `package.md` are machine-consumable (see §5 conformance). Also read `spec/prompt.md`
   for the authored intent behind the framework.
6. One exemplar spec package — read `spec/payment-service/` (`agents.md`, `package.md`,
   `requirements.md`, `design.md`, `tasks.md`) end to end; skim `spec/auth-service/agents.md` for the
   stricter standing-rule tone (already read in step 4 — note **enumeration safety is a hard rule there**).
7. The frontend's contract surface: `contracts/README.md`, `contracts/api/auth.yaml`,
   `contracts/api/crypto-internal.yaml`, `contracts/api/token-claims.md`, the state of
   `libs/ts/api-client` (the generated TS client), and `contracts/events/`. The SPA additionally
   consumes **standard Spring Authorization Server OIDC endpoints** (`.well-known/openid-configuration`,
   `/oauth2/authorize`, `/oauth2/token` (PKCE), `/oauth2/revoke`, `/userinfo`, SAS-hosted `/login`) — these
   are not part of `auth.yaml`; treat the SAS protocol + the **SAS MFA step integration** (`T20` in
   auth) as the contract for login/challenge, and the auth-service REST endpoints as the contract for
   registration/verification/password/sessions/API-keys/MFA-management.

**Known auth contract gap to check and record (§11):** `accounts/me/mfa/totp*` (enroll/confirm/disable)
are enumerated in `spec/auth-service/requirements.md` R22–R29 but were NOT found in
`contracts/api/auth.yaml` (auth MFA work is pending tasks T16–T22). If they are still absent, the MFA
enrollment/management screens depend on a contract that does not yet exist — request it or park the
slice as a DRAFT blocker with an explicit `Q#`. Do not invent these endpoints.

## 4. Output — the `spec/frontend/` package (exactly)

Write **four required files plus `agents.md`** to `spec/frontend/`:

| File | Contents (map from the skill template) |
|---|---|
| `agents.md` | **Standing rules** — durable, cross-feature frontend rules distilled from `ARCHITECTURE.md`, `frontend/README.md`, `spec/auth-service/agents.md` (auth posture), and the repo norm (see §6). Never restated inside the feature specs. |
| `package.md` | Spec front matter (Spec ID, version, status, scope In/Out — template Sec 0–2), **per-phase** named-test plan in **§8** (`name → R#` rows), the implementer self-check in **§9**, rollout/rollback in **§10**, and **§11 Open Questions** (per-phase DRAFT blockers). |
| `requirements.md` | Template **Sec 3** — every requirement as **EARS**, one testable line per requirement, ID `R1…Rn`, grouped under **`## Phase N — <title>`** section headers (so requirement IDs are globally unique across all five phases, not re-started per phase). Phase 1's auth/account section comes **first** and is the largest single group. |
| `design.md` | Template **Sec 4/5/6** — **§4a LOCKED (`L#`)**, **§4b OPEN (`O#`)**, **§4c VERBATIM** (routing table, OIDC/PKCE token flow, API error → UI behaviour map, JSON shapes, env keys — paste, never describe), **§5 persisted/on-device state changes**, **§6 package & file map**. Mark each LOCKED/OPEN item with its scope — **platform-level (enduring, tagged `[ALL]`)** vs **phase-scoped (tagged `[P1]`…`[P5]`)** — so a phase can never silently re-decide a platform-level lock. Auth-gate flows (public-OIDC-client shape, where the SAS redirect boundary sits, token handoff, enumeration-safe copy, MFA-enrollment sequencing) are platform-level `[ALL]` decisions. |
| `tasks.md` | Template **Sec 7** — ordered, atomic implementation tasks, numbered `1.` … `N.`, grouped under **`## Phase N — <title>`** headers (these `##` headers are how the generator groups). Front-load foundation + the Phase 1 **auth gate first** (it unblocks everything), then the rest of Phase 1 (marketable day one), then Phases 2–5 in release order, each task citing the `R#`/`L#`/`Q#` it resolves and its phase. |

One package, `spec/frontend/` — it represents one implementable, evolving surface (the full product
PWA), exactly as one package per backend service. Short name `frontend` → future
`.ai/prompts/frontend/`.

**Size / packaging decision (interview the author).** Default is the single package above, phases as
`##` sections — it is what the repo's machinery and README assume, and it keeps platform-level locks
in one place. If the author prefers phase-isolated delivery instead, splitting into per-phase packages
(e.g. `spec/frontend-phase1/` … `spec/frontend-phase5/`) is permitted ONLY with explicit sign-off —
each phase package must then carry its own full five-file set, and a later-phase package must not be
`READY FOR IMPL` until the phases/contracts it depends on exist. The Phase 1 auth/account slice must
still be either its own section in the single package or its own package — it must not be merged away.
Record the choice in §11.

## 5. Conformance — make it machine-consumable by `.ai/generate.py`

Your package will be parsed by `.ai/generate.py`. Verify against the parser:

- `tasks.md`: tasks are regex-matched as `^(number)\.` under `## Section` headers; bold-lead task
  titles; cite inline `R#`, `L#`, `Q#` where the parser should auto-scope each task. Your `## Phase N`
  headers ARE the section headers — keep them exactly at `##` level. A phase with multiple working
  groups uses several `## Phase 1 — …` sections (e.g. `## Phase 1 — Auth & account (gatekeeper)`,
  `## Phase 1 — Payment verification & invoicing`) — the `Phase N` prefix keeps them grouped.
- `requirements.md`: `R#` IDs present and unique across all phases.
- `design.md` §4a: `L#` IDs present and unique; §4b `O#`; §4c carries the verbatim blocks.
- `package.md` §8: named tests as `` `testName` → R# `` lines (the parser maps these to tasks);
  §11: `Q#` open questions.
- After you finish, run `python3 .ai/generate.py --check` and confirm `spec/frontend` is discovered
  and the task count is sane. Do NOT run the full regenerate unless asked — you are spec-authoring,
  and the `.ai/` framework is owned by its own generator run.

Do not modify anything inside `.ai/`, `services/`, `contracts/`, or the backend `spec/*` packages.

## 6. Domain facts that become `agents.md` standing rules (distilled ground truth)

State these in `spec/frontend/agents.md` as durable rules (and reference them from the specs —
never restate them in `requirements.md`):

- **Stack (partial).** React + TypeScript, mobile-first PWA (`frontend/README.md`). Everything else —
  bundler (Vite vs framework), routing, state manager, design system, PWA/service-worker approach,
  HTTP/real-time layer, secure-session storage for the browser, test runner, lint/CI — is currently
  **un-decided** (greenfield). You propose; the author confirms; unconfirmed → `§4b OPEN` + `§11`
  DRAFT blocker (§6 of the skill). Choose platform-level defaults that **survive all five phases**
  (evidence graph UI, dispute workspaces, reputation dashboards scale better on a chosen routing/
  state/screen architecture — call this out in §4a).
- **The auth service is the gatekeeper and the auth UI ships day one.** Nothing else in the app is
  reachable until a valid session exists (every non-public route is auth-gated). Registration,
  email verification/resend, OIDC login, MFA enrollment + challenge, password reset/change, session/
  device management, and merchant API keys are all surfaced in Phase 1, first — mirroring
  `spec/auth-service/` as the authoritative behavior source (R1–R48). Consent of identity and account
  status flows from the auth service; the UI is a faithful consumer, never a reconstituted copy.
- **The SPA is a public OIDC client — SAS redirect boundary.** The interactive login (password +
  TOTP/recovery-code challenge) happens in the **SAS-hosted flow** (Spring Authorization Server,
  including its custom MFA step — auth task T20), not a proprietary SPA form. The SPA issues
  `authorize` → receives the auth code → exchanges with PKCE at `/oauth2/token` → holds artifacts
  purely as a standard OIDC client. No Themistra-custom auth validation; no client secrets in the
  bundle. SPA-managed paths (registration, verification, password reset/change, sessions, API keys,
  MFA enroll/disable) hit `auth.yaml` via the generated client only.
- **Enumeration safety extends to UI pixels.** The auth endpoints return uniform, indistinguishable
  responses (R5/R12/R15/R21, auth `agents.md`). The SPA MUST NOT reintroduce enumeration signals
  through copy, redirect targets, error styling differences, or client-side account-state gating.
  Copy for unknown / locked / suspended / missing must be indistinguishable. Any UX that would leak
  state (e.g. a "too many attempts, wait 12:34" lockout timer) must be flagged as a §8 blocker unless
  the author explicitly authorizes an exception; ask in §7.
- **Token handling.** Access tokens and refresh tokens are NEVER in `localStorage` or logs; storage is
  a confirmed stack decision (§6). Access token is short-lived (10 min); refresh tokens are opaque,
  rotating families; presenting a superseded token triggers reuse-detection → the SPA must silently
  transition to a clean re-auth, never a broken half-session. Access tokens carry exactly
  `contracts/api/token-claims.md` claims — no PII; profile data comes from `userinfo`/`id_token`.
- **MFA-first for privileged accounts.** MERCHANT/ADMIN must complete TOTP enrollment before any
  authorization code (R24) — the first sign-in forces the in-app enrollment wizard; recovery codes are
  shown exactly once (R23). TOTP challenge/recovery-code entry is part of the SAS login step.
- **API keys are plaintext-once.** Creation shows the `ck_live_`-prefixed key exactly once, then never
  again; the list view never returns secrets (R30/R34).
- **Single origin.** The app talks to one origin: CloudFront → WAF → ALB → ingress-nginx → services
  (`ARCHITECTURE.md` §3.1). No hardcoded per-service URLs; route via the edge path map.
- **API access ONLY through the generated client.** Every backend call (including all auth REST calls)
  goes through the generated TypeScript client `libs/ts/api-client` (generated in CI from
  `contracts/api/*.yaml`) — no hand-written `fetch` to backend routes, no schema hand-rolled from
  memory. This is a hard LOCKED rule, every phase (`frontend/README.md`).
- **Money is decimal strings on the wire.** Amounts cross the wire as decimal strings, never JSON
  numbers; the UI may render human amounts but must not do precision math in JS `Number` for
  monetary values (mirrors every backend `agents.md`).
- **Verification state machine is user-visible.** Payment states
  `CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`, with reorg reversals
  `CONFIRMING → SEEN` and `SEEN → WATCHING`, and `HELD` — the UI shall surface the current state and
  reorg movement truthfully and never imply a receipt where none exists. A receipt is shown ONLY from
  `FINALIZED`/`ATTESTED`.
- **Privacy/observability.** Never log or persist tokens, PINs, recovery codes, or PII in client-side
  logs or analytics; i18n and a11y apply to every new screen, in every phase.
- **Notifications.** In-app delivery is websocket/SSE fanned out through the edge (auth events like
  `auth.security.audit`/session-mgmt are reflected in the UI where user-facing) (`ARCHITECTURE.md`
  §3.5); treat connection-loss and reconnection as first-class UI states. Later phases add merchant
  webhooks visibility — the in-app channel is the persistent baseline.
- **Phase 2+ is consumer-first, not rewrite.** Intelligence, dispute, reputation, and fraud surfaces
  are new consumers/interfaces over the same underlying events and ledger — and they re-use the Phase 1
  auth/session model and shell (`ARCHITECTURE.md` §1 principle 5). The frontend must present them as
  added capabilities, never as replacements that break Phase 1 flows or re-decide auth locks.

## 7. Workflow (main authoring pass)

Follow `SKILL.md` steps 1–9 in order:

1. **Read template + references** (§2, §3 above).
2. **Establish and bound scope before exploring.** Confirm the frontend entry points in one line per
   phase group across the whole roadmap (e.g. `[P1]` auth/account gatekeeper (register, verify,
   login+MFA, reset, sessions, API keys) → invoices/verification/receipts/notifications/exports;
   `[P2]` evidence upload + evidence-graph + integrity reports; `[P3]` dispute case workspace +
   timeline/narrative/decision; `[P4]` reputation dashboards + trust passport; `[P5]` fraud alerts +
   institutional API portal). Treat that as the hard boundary. **If the author has not given scope,
   STOP and ask** before reading code (§2 of the skill).
3. **Interview the author for the non-inferable bits.** Ask in batches and WAIT for answers before
   filling the corresponding section: the why-now (Sec 1), explicit out-of-scope per phase (Sec 2),
   load-bearing LOCKED vs OPEN decisions (Sec 4), verbatim artifacts they already have (Sec 4c), the
   stack proposals that need confirmation (§6 of the skill), acceptance criteria in plain language,
   **phase priorities / which commercial slice must be market-shippable first, and what "ready for
   go-to-market" means per phase** — and the **auth-UX decisions**: whether the SAS-hosted login page
   is acceptable as-is vs a branded SPA-side handler, how the post-login forced-MFA-enrollment wizard
   should feel, whether any enumeration-safe lockout messaging is permitted beyond the uniform copy,
   and the branded app name shown on OIDC/SAS screens. Park every unresolved item in §11 as a DRAFT
   blocker. Only proceed unanswered if the author says "yolo" / "one time".
4. **Detect, don't assume, within scope.** The repo is greenfield for the frontend: propose the
   default stack (React + TypeScript + a concrete feature-area layout carrying the auth gate and
   Phases 2–5), explain reasoning, get confirmation. Never present guesses as facts.
5. **Unreachable dependencies — ASK, don't fabricate.** The frontend depends on backend API
   contracts. `contracts/api/auth.yaml` and `contracts/api/crypto-internal.yaml` **exist** — read and
   quote them verbatim (Sec 4c) as the source of truth; SAS OIDC endpoints are public standard +
   auth-service-owned (`T20`). **`contracts/api/payments.yaml`,
   `contracts/api/notifications.yaml`, all Phase 2–5 APIs (evidence/intelligence, disputes,
   reputation, fraud/institutional), and — unless recently added — the `accounts/me/mfa/totp*`
   management endpoints do not exist yet** (the backend specs for some are not even authored). For
   those, do NOT invent endpoint paths, payloads, error shapes, or MFA endpoints. Request the exact
   contract or the intended API surface from the author per phase, drop it in verbatim, and record
   anything still missing in §11 as a per-phase blocker that keeps that phase `DRAFT` (§6 of the
   skill). A phase is only `READY FOR IMPL` when its own contracts are closed.
6. **Fill the template in order** with the discipline that matters: EARS, explicit LOCKED vs OPEN with
   `[ALL]`/`[P#]` tags, VERBATIM for silent-failure bits, §8 named tests mapped to `R#` (so the next
   agent cannot write happy-path-only tests), §9 self-check limited to what lint/CI won't enforce.
   Keep later phases specified to the same standard as Phase 1 — no revealed gameplaying.
7. **Size decision.** Default: single coherent package with phases as `##` sections (see §4). Keep
   each file lean and sectioned so a context window stays clean across a large roadmap.
8. **Write the package** to `spec/frontend/`, status `DRAFT`, version `0.1`.
9. **Before hand-off — run the adversarial review (§8), then the conformance check (§5).**

## 8. Mandatory adversarial review pass (in-between)

This is the one feature of this repo's process you must not skip: an **adversary reviews whatever
the main agent produces**, before it is considered done. Mirror the `.ai/` pipeline's review
discipline (its Kimi 2.7 phases 3/8/11).

After you finish the draft package, and **before** you hand it off:

1. **Spawn a fresh-context adversarial reviewer** (a subagent with no memory of your drafting
   choices). Where the environment lets you choose the reviewer model, prefer a different model than
   yourself — same as the `.ai/` pipeline's distinct reviewer model — so the review is genuinely
   independent. Give it the draft `spec/frontend/` package, the `.ai/generate.py` conformance rules
   (§5), `new_features.md` (the roadmap it must match), `spec/auth-service/` (R1–R48, `agents.md`),
   and the `contracts/` files as ground truth.
2. Instruct the reviewer to attack, at minimum:
   - **Auth gate (day one, critical).** Does the auth/account UI fully cover the
     `spec/auth-service/` surface (R1–R48): registration + uniform ack, verify/resend, SAS-hosted
     login + MFA challenge, forced MFA-enrollment wizard for MERCHANT/ADMIN, recovery codes exactly
     once, disable-MFA with password+TOTP, password reset/change, session list/revoke one/all,
     `userinfo` profile, merchant API keys plaintext-once, admin activate/unlock/suspend/roles?
     Does it stay **enumeration-safe in UI copy, redirects, and timing** (any "wrong password" vs
     "no such account" tell, lockout timer, verification-token error styling — Blocker)? Is the
     SPA a standards-correct public **OIDC/PKCE** client of SAS (no client secrets, `state` +
     redirect-URI validation, open-redirect prevention on login return)? Is `token-claims.md` honored
     (no PII in access tokens) and is token storage secure (no `localStorage`)? Refresh rotation,
     reuse-detection → clean re-auth, 401 mid-flow, 429 backoff, and any invented `mfa/totp*`
     endpoint (must be a §11 blocker if not in `auth.yaml`)?
   - **Roadmap completeness (critical).** Does the package cover ALL of `new_features.md` Phases 1–5
     with nothing missing and nothing invented? Is each phase a coherent, marketable slice? Are later
     phases consumers of earlier ones rather than rewrites?
   - **Cross-phase coherence.** Do platform-level `[ALL]` LOCKED decisions survive every phase (no
     phase silently re-decides them)? Do Phase 1 UI/state/navigation decisions paint Phases 2–5 into
     a corner (e.g. navigation that cannot host an evidence graph or dispute workspace)? Globally
     unique `R#`/`L#` IDs; no forward-reference to an undefined requirement.
   - **EARS quality**: ambiguous, untestable, or non-EARS requirements; requirements not mapped to a
     §8 named test.
   - **LOCKED vs OPEN boundaries**: anything silently load-bearing that a junior could get wrong
     (token/session storage, generated-client-only rule, money-as-string, state-machine UI truth,
     enumeration-safe copy) vs decisions left open that should be locked, or locked things that should
     be open; correct `[ALL]`/`[P#]` tagging.
   - **Missing edge cases & failure modes** (per phase): no-network/slow-network/offline, 401 +
     refresh-token expiry/race, 429 backoff, account locked/suspended surfacing (enumeration-safe),
     permission denied, reorg (UI walks backward), attest `BLOCKED` → `HELD`, under/overpayment and
     address-poisoning surfacing, invoice expiry, webhook-less in-app reconnection, deep-link/OIDC
     redirect mismatch, session expiry mid-flow; Phase 2+: upload failures, corrupt evidence,
     integrity analysis partial results, ambiguity/low-confidence AI output; Phase 3: claim-state edge
     cases, evidence conflicts; Phase 4/5: data volume/aggregation rendering, alert classification UX.
   - **Contract mismatches**: any invented endpoint/schema/field/enum/error shape vs
     `contracts/api/*.yaml` and `token-claims.md` (including MFA endpoints); money as JSON number;
     verbatim blocks that drift from the real contracts.
   - **Security/compliance**: secrets/credentials in the bundle, tokens in `localStorage` or logs,
     recovery codes/API keys persisted client-side, PII in analytics, open redirect on OIDC return,
     unverified receipt display.
   - **PWA/ops**: offline cache correctness (never cache sensitive response bodies), cache-busting /
     rollout / rollback (§10) across a multi-phase release cadence, force-update / kill-switch for new
     flows.
   - **Implementability & market-readiness**: would a junior + Claude Code complete each phase with
     minimal back-and-forth? Are §4b OPEN items bounded with "what good looks like"? Is every §11 item
     an owner-able ask? Is the ordering such that the product is actually shippable at the declared
     market milestones (auth/account gate first, then payment verification)?
   - **Generator conformance** and **scope creep** (anything touching backend `spec/`, `services/`,
     `contracts/`, or content beyond the roadmap must be flagged and removed).
3. The reviewer returns findings only, each as:
   **Issue · Severity (Blocker/Major/Minor) · Evidence (file + section/line) · Recommended amendment.**
4. **You adjudicate**: fold every ACCEPTED finding into the package; record every REJECTED one with a
   reason in a short `spec/frontend/artifacts/review-resolution.md`; re-run the conformance check.
   Repeat the adversarial pass on any package that still ships blocker- or major-severity items. A
   phase stays `DRAFT` if any blocker remains in it.

## 9. Hand-off & status

- Every §11 item per phase is gated and owner-templated for the author, **before** that phase moves
  from `DRAFT`. List them for the author in your final message, grouped by phase.
- Tell the author exactly which §4b OPEN decisions need sign-off per phase (including the auth-UX
  bachelors from §7 step 3), which missing backend contracts (currently `payments.yaml`,
  `notifications.yaml`, all Phase 2–5 APIs, and — if still absent — `accounts/me/mfa/totp*`) must be
  pasted in per phase, and which stack proposals await confirmation, with the expected next action for
  each.
- When the author resolves a phase's §11 closers and signs off, you bump the version and set that
  phase's status `READY FOR IMPL`. The package status reflects the least-advanced phase. The Phase 1
  auth/account slice is the first thing eligible to go `READY FOR IMPL`.
- Do NOT edit, generate, or commit anything in `.ai/`, `contracts/`, `services/`, or the backend
  `spec/*` packages. Do NOT implement any code.

## 10. Guardrails (absolute)

- You are the **architect/author**, not the implementer. No application code anywhere.
- Work ONLY on the frontend spec package. **All five phases are in scope — Phase 1 is not the end.**
  **The auth/account gatekeeper UI is in scope, first — it ships day one, and "Phase 1" is not a
  payment-only phase.** No unrelated refactoring, no speculative improvements beyond the roadmap.
- Never restate standing rules from `agents.md` inside `requirements.md` — reference them.
- Never fabricate a backend contract, a schema, an endpoint, or a stack decision — for ANY phase. If
  it is not readable in the repo and the author has not confirmed it, it is a §11 blocker, not a
  guess. This includes `accounts/me/mfa/totp*` until it lands in `contracts/api/auth.yaml`.
- Specs are versioned and owned like code. Output to `spec/frontend/`; keep `spec/` clean.
- Produce exactly the five files from §4 (plus the review-resolution artifact in §8) and nothing
  more. Write the files, run the two checks, then STOP and report to the author.
