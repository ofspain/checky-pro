# Local stack (everything in Docker)

The whole platform on one machine, for trying each frontend task by hand. Every dependency and every
service runs in Docker; nothing is installed on the host except Docker itself.

```
infra/local/bin/up               build and start the backend (dependencies, four services, edge)
infra/local/bin/up --frontend    the same, plus the SPA dev server (once frontend task 1 exists)
infra/local/bin/status           which services are ready
infra/local/bin/down             stop everything (data kept)
infra/local/bin/down --reset     stop and delete the database (start from empty)
```

| URL | What |
|---|---|
| http://localhost:5173/app/ | The app. One origin for the SPA and every API, as in production (frontend L9). |
| http://localhost:8025 | Mailpit: every email the platform sends (verification, reset, invoice, receipt). |
| http://localhost:8545 | The local Ethereum chain (anvil), if you want to use `cast` yourself. |

Debug ports, for calling a service directly: auth 18080, crypto 18081, notification 18082,
payment 18083, Postgres 15432 (user `checky`, password `checky-local-only`), Kafka 19094,
LocalStack 4566. They are chosen not to clash with `services/auth/compose.local.yaml`.

## What runs

| Container | Role |
|---|---|
| `edge` | Caddy on port 5173. Routes `/oauth2`, `/login`, `/logout`, `/connect`, `/.well-known`, `/userinfo`, `/error`, `/accounts`, `/admin`, `/api-keys` to auth; `/api/v1` to payment; `/notifications` to notification (SSE unbuffered); `/app` to the SPA. |
| `auth`, `crypto`, `notification`, `payment` | The services, built from their own Dockerfiles. `AUTH_ISSUER_URI` is `http://localhost:5173`, the origin the browser sees. |
| `postgres` | One server, one database per service (`auth`, `crypto`, `notification`, `payment`). |
| `migrate-*` | One-shot Flyway jobs for crypto, notification and payment (auth migrates itself at startup). |
| `kafka` | Single broker, topics created on first use. |
| `localstack` | S3 (receipt bucket with object lock, observation bucket) and KMS (the receipt signing key). |
| `mailpit` | Catches all email; notification-service sends to it over SMTP (`EMAIL_TRANSPORT=smtp`, local profile only). |
| `anvil`, `chain-init` | A local Ethereum chain, one block every 2 s, kept across restarts, and a mock USDC (6 decimals) at `0x5FbDB2315678afecb367f032d93F642f64180aa3`. crypto-service's three Ethereum providers all point at it (`config/crypto.properties`). TRON keeps placeholder providers that nothing serves, so a TRON invoice never moves locally. |
| `frontend` | `pnpm -C frontend dev` with hot reload, under the `frontend` profile. |

## Signing in for the first time

Nothing creates an admin, so the first one is granted by hand:

1. Register at `/app/register` (or `POST /accounts`), then open the verification email in Mailpit.
2. Sign in, and enroll MFA at `/app/account/mfa`. MERCHANT and ADMIN accounts must have it.
3. `infra/local/bin/grant-role you@example.com MERCHANT` (or `ADMIN`), then sign in again.

## Making a payment

Create an invoice with chain `ETHEREUM`, token `0x5FbDB2315678afecb367f032d93F642f64180aa3`,
decimals `6`, and a pay-to address from `bin/chain new-address`. crypto-service accepts only EIP-55
checksummed addresses (mixed case); use a new address per invoice, because a transfer is matched by
address. Then:

```
infra/local/bin/chain pay <payToAddress> <amount>   # amount in base units: 1 USDC = 1000000
infra/local/bin/chain finalize                      # or wait about 2 minutes
infra/local/bin/chain reorg 3                        # instead, before finalizing: drop the payment
```

The payment moves SEEN → CONFIRMING → FINALIZED → ATTESTED, and the receipt appears.
Pay less or more than the invoice to see UNDERPAID or OVERPAID. Pay with `bin/chain pay-blocked` to see
a HELD payment with no receipt: local screening (`LocalDevScreeningClient`, `mode=local-dev`) clears
every sender except that one. `bin/chain help` lists every command.

`infra/local/bin/dev-account [USER|MERCHANT|ADMIN]` creates a ready account in one step (verified, TOTP enrolled, role
granted, signed in) and prints its email, password, TOTP secret and an access token; `bin/totp <secret>` prints the current
code. To see EXPIRED quickly: `LOCAL_INVOICE_TTL_MINUTES=1 LOCAL_EXPIRY_SWEEP_CRON="*/20 * * * * *" bin/up`.
Agents testing frontend work: see `.ai/prompts/frontend/KIMI-LOCAL-STACK.md` (includes Playwright in Docker).

`infra/local/bin/smoke` runs the whole path through the edge (register, verify from Mailpit, PKCE
sign-in, MFA, MERCHANT, invoice, payment, receipt, sign-out) and prints SMOKE PASSED.

## Notes

- Everything here is local-only. The passwords, the anvil key and the AWS keys (`test`) protect nothing.
- Local-only behaviour is switched on by this stack's settings and refuses to start outside the
  `local` profile: SMTP email (`EMAIL_TRANSPORT=smtp`) and local screening (`themistra.crypto.screening.mode=local-dev`).
  Without them every environment keeps the fake email transport and fail-closed screening.
- LocalStack keeps nothing between restarts, so `localstack/init.sh` recreates the buckets and the key on
  every start. Receipts signed before a restart can no longer be verified.
- The first `bin/up` builds four images and takes several minutes. Later runs reuse the layers.
