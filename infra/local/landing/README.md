# The on-chain verification landing page

One field, one button, one recorded answer. Branch `demo/onchain-verification`.

## What happens when someone clicks Verify

1. The page `POST`s the hash to `/api/v1/verifications` — same origin, so there is no CORS to
   configure and nothing changes when the host does.
2. **payment-service** calls crypto-service's `GET /internal/v1/transactions/{chain}/{txHash}`
   with the `client_credentials` token it already holds (`internal.crypto:write`).
3. **crypto-service** asks **three providers** and runs **2-of-3 quorum on every fact
   independently** — existence, confirmations, token, from, to, amount — then checks the token
   allowlist. If the providers disagree the answer is `HELD` and **carries no facts at all**, so
   nothing downstream can act on a disputed value.
4. payment-service writes the answer to `payments.chain_verifications` (append-only: the role has
   `INSERT` and `SELECT`, nothing else) and returns the stored row.

The page renders the **stored row**, not the upstream answer, so what a visitor reads and what the
database holds cannot drift apart.

## Running the demo

```bash
infra/local/bin/up          # the stack; --frontend also starts the SPA, which this page does not need
infra/local/bin/chain addresses   # a pay-to address
infra/local/bin/chain pay <address> 12500000   # 12.50 USDC — prints transactionHash
open http://localhost:5173
```

Paste the `transactionHash` into the page.

### The outcomes you can actually show, all measured on this stack

| Outcome | How | Measured |
|---|---|---|
| **AGREED** | `bin/chain pay <addr> 12500000` | `AGREED`, 1 confirmation, `"12.5 USDC"` |
| **NOT FOUND** | Any plausible hash that was never mined | `NOT_FOUND`, every fact null |
| **UNKNOWN TOKEN** | Move a token that is not on the allowlist | `UNKNOWN_TOKEN`, amount `5000000`, **no** `amountDisplay` |
| **The reorg** | Verify a hash, then `bin/chain reorg 20`, then verify it again | `AGREED` with 20 confirmations → `NOT_FOUND` |

**The reorg is the one to close on.** A payment verifies, the chain reorganises, and the same hash
now reports that it never happened — which is the whole argument for confirmations, for finality,
and for not trusting a single provider's "yes".

**`HELD` cannot be shown on this stack, and it is the one outcome worth wanting.** It means the
three providers disagreed, and locally all three *are the same anvil node* — they cannot disagree.
To demo it you would have to point one provider at a different node in
`infra/local/config/crypto.properties`. Nothing is faked to produce it: the quorum code runs on
every request, it simply always reaches agreement here.

**`bin/chain pay-blocked` does not produce `HELD` on this page** — measured, it returns `AGREED`.
Sanctions screening belongs to the attestation and watch paths, not to this read. An earlier draft
of this file claimed otherwise, which is exactly the kind of thing that ruins a live demo.

## What is captured

One append-only row per check: a random id, the chain, the transaction hash, the outcome, the
agreed facts, and the time. All of it is public chain data plus a timestamp. **No account, no
email, no IP address, no cookie, no wallet connection, nothing signed.** The visitor's browser
keeps its own history of checks in `localStorage` and nothing else; the edge has no access-log
directive; crypto-service writes nothing at all on this path.

**There is deliberately no server-side "recent checks" list.** One existed and was removed before
this went anywhere public: `GET /api/v1/verifications` handed the last ten checks — hashes,
addresses, amounts — to any caller at all, which tells a stranger what payments other people are
investigating. That path is now `405`, and the page's history is per-browser.

## Before this is served on a public URL

These are the gaps, written down rather than discovered later.

- **Rate limiting is done, in the service rather than at the edge.** An earlier draft of this file
  argued it belonged in Caddy. That was wrong for this deployment: the Caddyfile here is a
  local-development artifact, a public deploy may sit behind ngrok, a cloud load balancer or
  nothing at all, and a limit written into one edge disappears the moment the edge changes.
  `VerificationRateLimitFilter` gives each client 10 POSTs and 60 reads a minute, with
  `RateLimit-*` headers and a 429 carrying `Retry-After`. The counters are in memory, so each
  instance has its own budget: **several replicas permit several times this**, and a real
  deployment behind more than one wants a shared store.
- **The receipt is not signed.** This path writes no `quorum_decisions` and no `observations` row,
  so it cannot feed attestation (L10). What prints is a record of what three providers said at one
  moment, which is what the page claims and no more.
- **The three "providers" are one anvil node** (`infra/local/config/crypto.properties` says so in
  its own comment). The quorum logic is real and runs for every request; what is not real locally
  is the independence of the three answers. A public deployment needs three genuinely separate
  providers, or the 2-of-3 claim is a claim about one node agreeing with itself.
- **Append-only is enforced by the database, not by convention.** Measured: `payment_app` holds
  `INSERT` and `SELECT` on `chain_verifications`, and an `UPDATE` as that role fails with
  `permission denied`.
- **No caching.** The same hash asked twice is two provider fan-outs and two rows. That is correct
  — each answer is a fact about a moment, and a reorg can change it — but a public page wants a
  short cache in front of it anyway.
- **The token display list is config, not the allowlist.** `THEMISTRA_PAYMENT_CHAINVERIFY_TOKENS_*`
  decides only how an amount is *printed*. crypto-service owns the real, signed allowlist and
  decides `UNKNOWN_TOKEN`. If the two ever disagree, crypto's is right and this is a display bug.
- **The landing page is served by the edge from a mounted directory.** Deploying it anywhere else
  (S3 + CDN, or a static host) only needs `/api/v1/*` to resolve to payment-service from the same
  origin.
