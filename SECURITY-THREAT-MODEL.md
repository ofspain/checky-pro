# Themistra — Security Threat Model

> Status: **stub — must be completed before the first line of crypto-service code.**
> See ARCHITECTURE.md §6.7. Every feature PR either updates this document or states why no update is needed.

## Threats to enumerate (minimum set)

| # | Threat | Mitigation (ARCHITECTURE.md ref) | Status |
|---|---|---|---|
| 1 | Attacker controls one RPC provider | Multi-provider 2-of-3 quorum (§6.1) | designed |
| 2 | Attacker deploys fake USDT contract | Contract-address allowlist (§6.3) | designed |
| 3 | Chain reorg after user sees "confirmed" | Per-chain finality policy, reorg-aware state machine (§6.2) | designed |
| 4 | Stolen application server credentials | Keys unexfiltratable — KMS-only signing (§6.4) | designed |
| 5 | Insider alters a historical verification | Hash-chain ledger + S3 Object Lock + on-chain anchor (§6.5) | designed |
| 6 | Address-poisoning of a repeat customer | Prefix/suffix similarity flagging (§6.3) | designed |
| 7 | Merchant webhook spoofing | HMAC-signed webhook payloads (§3.5) | designed |
| 8 | Account takeover of a merchant | MFA from day one (§3.2) | designed |
