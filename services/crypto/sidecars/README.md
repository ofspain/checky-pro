# Chain Sidecars (TypeScript)

One directory per chain whose Java tooling is inadequate (e.g. `solana/`, later).
Each sidecar speaks the chain's native SDK on one side and the internal adapter
contract on the other. Sidecars observe and translate **only** — the Java core treats
their output as one more provider answer, subject to the same quorum.
