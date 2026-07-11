# Crypto Service

Java 21 · Spring Boot · virtual threads · web3j (EVM) · Tron gRPC. **CODEOWNERS-protected.**

The only component that talks to blockchains. Adapter layer (per-chain, common interface)
plus watcher layer (long-running subscriptions/polling). Every fact requires 2-of-3 quorum
across independent RPC providers. Per-chain finality policy; reorgs emit `chain.tx.reorged`.
Exposes internal `POST /attest` — the sole path to `kms:Sign` on the attestation key.

Owns: `chain` Postgres schema (watches, provider health, raw observation log).
Publishes: `chain.tx.seen|confirmed|finalized|reorged`, `chain.provider.degraded`.

`sidecars/` — TypeScript chain adapters for thin-Java-tooling chains (translation only:
no quorum authority, no key access, no business state).
