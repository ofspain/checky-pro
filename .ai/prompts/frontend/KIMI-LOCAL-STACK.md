# Handoff to Kimi: run the whole platform and test frontend work against it

Written 2026-10-22. Read this once, before your first frontend task. It explains how to start the whole platform (every
backend service, every dependency and the SPA dev server) in Docker. You can then check any frontend task against the
real backend yourself: by hand-style HTTP calls, headless with Playwright, or by reading Mailpit and the chain. Each task's
own phase prompts still apply. This file only covers the environment.

Everything lives in `infra/local/`. Its `README.md` is the reference; this note is the working guide.

---

## 1. Rules

1. **Never touch the containers `auth-postgres-1` or `auth-kafka-1`.** They belong to the developer (host ports 5432 and 9094)
   and are used by some backend integration tests. Do not stop, restart, remove or reuse them. This stack uses its own
   containers (project `themistra-local`) on other ports.
2. **Don't change backend code or `infra/local/` just to make a frontend task easier.** If the stack or a service blocks a
   task, stop and report it with a reproduction: the exact command, the response, and the relevant
   `docker compose -p themistra-local -f infra/local/compose.yaml logs <service>` lines. Known open backend items (section 8) stay as they are unless one
   actually blocks the task in front of you.
3. **Local only.** Every password, key and AWS credential here protects nothing (`*-local-only`, anvil's public dev keys,
   `test`/`test`). Never copy them into `frontend/` source, the bundle or any non-local config (frontend R58, L10).
4. **Don't commit runtime state:** no `node_modules`, no forge `chain/out`/`chain/cache` (already ignored), no Playwright
   reports or traces, no account JSON from `bin/dev-account`.
5. The developer's own untracked `prompt` file at the repository root is not yours. Never stage it.

## 2. Prerequisites (check once)

| Need | Check | Note |
|---|---|---|
| Docker Desktop running | `docker info` | Nothing else is installed on the host: Java, Node, pnpm and Maven all run in containers. |
| Docker memory ≥ 8 GB | `docker info --format '{{.MemTotal}}'` | **On 2026-10-22 it was 3.8 GB.** The stack alone uses about 2.5 GB; the dev server and Playwright need more, and running out silently kills containers (exit code 137). If it is below 8 GB, ask the developer to raise it (Docker Desktop → Settings → Resources); you cannot change it yourself. |
| `python3` and `curl` on the host | `python3 --version` | The `bin/` helpers are standard-library Python and bash. |
| Free host ports | `lsof -i :5173 -i :8025 -i :8545 -i :15432 -i :19094 -i :4566 -i :18080-18083` | If one is taken by something that is not `themistra-local`, stop and report it. Don't kill it. |

## 3. Start from zero

```
infra/local/bin/up                 # builds 4 service images (first time: several minutes), starts everything
infra/local/bin/status --wait      # blocks until auth, crypto, notification and payment are ready
infra/local/bin/smoke              # end-to-end check; must end with "SMOKE PASSED"
```

If `smoke` passes, the backend is fully working: register, email verification from Mailpit, PKCE sign-in, MFA, MERCHANT
role, invoice, on-chain payment, ATTESTED, signed receipt, HELD payment, sign-out. If it fails, see section 7 before
anything else.

**The SPA dev server** (`frontend` container, Vite with hot reload) starts with `infra/local/bin/up --frontend`. It runs
`corepack enable && pnpm install && pnpm -C frontend dev --host 0.0.0.0 --port 5173` from the repository root, so:

- **Frontend task 1 must leave a root `package.json`** (the repo has `pnpm-workspace.yaml` but no root `package.json` yet).
  Pin pnpm with a `packageManager` field. Otherwise `pnpm install` fails and the container exits.
- **The Vite config must have** `base: '/app/'`, `server.host: true`, `server.port: 5173`, `server.strictPort: true`, and
  **no `server.proxy`**. The edge (Caddy) already routes `/app*` to the dev server and every API prefix to its service, on
  one origin (frontend L9, design.md §4c note of 2026-10-22). Hot reload works through the edge; file changes are polled
  (`CHOKIDAR_USEPOLLING`) because file events don't cross the macOS Docker VM reliably.
- Run the frontend's own commands inside that container, not on the host:
  `docker compose -p themistra-local -f infra/local/compose.yaml exec frontend pnpm -C frontend test` (Vitest), `... lint`, `... build`.

## 4. What is where

| URL / port | What |
|---|---|
| http://localhost:5173/app/ | The SPA, through the edge. The only origin the browser and the OIDC issuer use. |
| http://localhost:5173/{oauth2,login,logout,connect,.well-known,userinfo,error,accounts,admin,api-keys} | auth-service |
| http://localhost:5173/api/v1/... | payment-service (`contracts/api/payments.yaml`) |
| http://localhost:5173/notifications/... | notification-service (SSE stream, unbuffered) |
| http://localhost:8025 | Mailpit UI; API at `/api/v1/search?query=to:<email>` and `/api/v1/message/<ID>` |
| localhost:8545 | anvil (chain id 31337, a block every 2 s) |
| 18080 / 18081 / 18082 / 18083 | auth / crypto / notification / payment directly (debugging only; the SPA must only use the edge) |
| 15432 | Postgres, user `checky`, password `checky-local-only`, one database per service |

The SPA's OIDC client is `checky-spa` (public, PKCE). Its redirect URIs are `http://localhost:5173/app/callback`, and
`http://localhost:5173/app/signed-out` after logout. The scopes are `openid profile email`.

## 5. Test data: accounts, payments, states

**A ready account in one command** (registered, email verified, TOTP enrolled, role granted, signed in through the real
PKCE flow):

```
infra/local/bin/dev-account MERCHANT        # or USER, ADMIN; optional --email you@example.com
# → {"email", "password", "role", "totpSecret", "accessToken", "idToken"}
infra/local/bin/totp <totpSecret>           # the current 6-digit code, e.g. to type into the SAS login page
```

The access token lasts 15 minutes and can be used straight away for API checks:
`curl -H "Authorization: Bearer $TOKEN" http://localhost:5173/api/v1/invoices`. To sign in through the UI (or Playwright),
use the email and password on the SAS login page, then the code from `bin/totp`. Codes are single-use, so wait for the next
30-second window before reusing a secret.

To promote an existing account: `infra/local/bin/grant-role <email> MERCHANT|ADMIN`. The account must be ACTIVE, and
MERCHANT and ADMIN need confirmed MFA (D-031).

**Invoices and payments.** Create an invoice with chain `ETHEREUM`, token
`0x5FbDB2315678afecb367f032d93F642f64180aa3`, decimals `6`, amounts in base units (1 USDC = `1000000`), and a
**fresh pay-to address for every invoice** from `infra/local/bin/chain new-address`. crypto-service accepts only EIP-55
mixed-case addresses and matches transfers by address.

| To see | Do |
|---|---|
| SEEN → CONFIRMING | `bin/chain pay <payTo> <amount>`; confirmations rise each block (2 s) |
| FINALIZED → ATTESTED + receipt (+ receipt email) | `bin/chain finalize` (mines 64 blocks), or wait about 2 minutes |
| UNDERPAID / OVERPAID | pay less or more than the invoice amount, then finalize |
| HELD (no receipt) | `bin/chain pay-blocked <payTo> <amount>`, then finalize (local screening blocks that sender) |
| Reorg (back to SEEN/WATCHING) | `bin/chain pay ...`, then `bin/chain reorg 3` before finalizing |
| EXPIRED | restart with a short TTL: `LOCAL_INVOICE_TTL_MINUTES=1 LOCAL_EXPIRY_SWEEP_CRON="*/20 * * * * *" infra/local/bin/up` → EXPIRED about 80 s after creation. Run plain `infra/local/bin/up` afterwards to go back to 60 minutes. |
| Emails (verify, reset, invoice, receipt) | Mailpit UI or API; links point at `http://localhost:5173/app/...` |

`bin/chain help` lists every chain command. `bin/chain status` shows the latest and finalized blocks.

## 6. Headless browser tests (Playwright) in Docker

The OIDC flow redirects to `http://localhost:5173`, so a browser in a container must see the edge at that exact origin.
The edge listens on `:5173` inside its own container too, so run Playwright **inside the edge container's network**:

```
docker run --rm --network container:themistra-local-edge-1 \
  -v "$PWD":/repo -w /repo/frontend \
  -e BASE_URL=http://localhost:5173 \
  mcr.microsoft.com/playwright:v<same version as @playwright/test>-noble \
  npx playwright test
```

- Pin the image tag to exactly the `@playwright/test` version in `frontend/package.json`. A mismatch fails at browser
  launch.
- Seed accounts with `bin/dev-account` on the host before the run and pass them in as environment variables (`-e`), or call
  the same registration → Mailpit → TOTP steps from a global setup. Mailpit's API is reachable from that container only
  through the edge network, so use `http://host.docker.internal:8025` from inside the Playwright container.
- Generate TOTP codes in the test with the same RFC 6238 algorithm as `bin/totp` (SHA-1, 30 s, 6 digits).

## 7. When something goes wrong

| Symptom | Cause and fix |
|---|---|
| TOTP code "invalid" though it's correct | The Docker VM clock drifted after the Mac slept. `infra/local/bin/fix-clock` (`bin/status` warns when this happens). |
| Invoice creation returns 503 just after a (re)start | crypto-service is still warming up. Wait for `bin/status --wait` and retry once. If it persists, `docker compose -p themistra-local -f infra/local/compose.yaml logs payment | grep -i "crypto"`: the WARN line names the cause class. |
| Invoice creation returns 503 every time | Usually a non-checksummed pay-to address: crypto returns 400 and payment reports it as 503 (known, section 8). Use `bin/chain new-address`. |
| Payment never appears | A pay-to address reused from another invoice, or a TRON invoice (TRON has no local chain; only ETHEREUM moves). |
| A container keeps restarting or exited with 137 | Out of memory; see section 2. `docker compose -p themistra-local -f infra/local/compose.yaml ps -a`. |
| Dev server returns 500 "Cannot find module …/node_modules/.pnpm/…" | A dependency was added or removed while it ran, so pnpm re-linked packages under new paths. `docker compose -p themistra-local -f infra/local/compose.yaml restart frontend`. Restart it after every `pnpm add`/`remove`. |
| Need a clean slate | `infra/local/bin/down --reset` (deletes all data), then `bin/up`. |
| Backend code changed (pulled a fix) | `docker compose -p themistra-local -f infra/local/compose.yaml up -d --build <service>` |
| See what a service is doing | `docker compose -p themistra-local -f infra/local/compose.yaml logs -f <service>` |

After any restart of LocalStack, receipts signed before it can no longer be verified, because the KMS key is recreated.
Make new ones.

## 8. Known open backend items (leave them alone unless one blocks your task)

- A crypto 400 (e.g. a bad pay-to address) is reported by payment as 503 `crypto-service-unavailable`.
- `chain.tx.confirmed` can arrive before `chain.tx.seen` (separate topics) and is dropped, so a payment's confirmation count
  can stay low until it finalizes. The state still reaches FINALIZED/ATTESTED. Don't build UI logic that requires the count
  to rise monotonically through every value.
- Emails greet "Hi ," because registration collects no name.
- Two open invoices with the same pay-to address and amount can't be told apart. Always use fresh addresses.
- TRON invoices never progress locally.

If one of these blocks a task, report it, naming the task and the requirement it blocks. Don't patch the service.

## 9. Per-task loop (the fast path)

1. `infra/local/bin/status`. If it's not up, `bin/up --frontend`. If it warns about the clock, `bin/fix-clock`.
2. Implement the task in `frontend/`. Hot reload shows it at http://localhost:5173/app/.
3. Create the state the task needs (section 5): `bin/dev-account`, invoices, `bin/chain ...`, Mailpit.
4. Run the task's named tests inside the `frontend` container (Vitest), and the Playwright ones per section 6.
5. In the phase artifact, record what you exercised against the live stack: commands, observed states and anything that
   didn't match the spec.
6. Before handing back, `infra/local/bin/smoke` should still pass. A frontend change can't break it, but it confirms the
   environment you tested against was healthy.
