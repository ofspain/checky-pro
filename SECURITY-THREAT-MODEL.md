# Themistra — Security Threat Model

> Status: **stub — must be completed before the first line of crypto-service code.**
> See ARCHITECTURE.md §6.7. Every feature PR either updates this document or states why no update is needed.

## Threats to enumerate (minimum set)

| # | Threat | Mitigation (ARCHITECTURE.md ref) | Status | Implementing task |
|---|---|---|---|---|
| 1 | Attacker controls one RPC provider | Multi-provider 2-of-3 quorum (§6.1) | tracked | crypto T09 (quorum evaluator) |
| 2 | Attacker deploys fake USDT contract | Contract-address allowlist (§6.3) | tracked | crypto T11 (token allowlist) |
| 3 | Chain reorg after user sees "confirmed" | Per-chain finality policy, reorg-aware state machine (§6.2) | tracked | crypto T14 (finality policies) / T18 (reorg detector) |
| 4 | Stolen application server credentials | Keys unexfiltratable — KMS-only signing (§6.4) | tracked | crypto T20 (KMS signer) |
| 5 | Insider alters a historical verification | Hash-chain ledger + S3 Object Lock + on-chain anchor (§6.5) | tracked | crypto T08 (observation log) / T09 (quorum decision persistence) |
| 6 | Address-poisoning of a repeat customer | Prefix/suffix similarity flagging (§6.3) | tracked | crypto T13 (address-poisoning detector) |
| 7 | Merchant webhook spoofing | HMAC-signed webhook payloads (§3.5) | designed | — |
| 8 | Account takeover of a merchant | MFA from day one (§3.2) | designed | — |

Threats #1–#6 are owned by `crypto-service` (`spec/crypto-service/tasks.md`); `tracked` means each is
mapped to the task that closes it, not yet implemented (`services/crypto` has no application code as
of crypto-service T01). Threats #7–#8 are auth-service/payments concerns, out of this table's T01
update scope.
