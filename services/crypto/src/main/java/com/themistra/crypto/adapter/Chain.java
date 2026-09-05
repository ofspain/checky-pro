package com.themistra.crypto.adapter;

/**
 * Launch scope per design.md §2 — Ethereum and Tron only; later chains (Base, Arbitrum, Solana) are
 * roadmap, not built here.
 *
 * <p>Bridge from T03's regex-constrained {@code String} config values
 * ({@code ProviderProperties}/{@code FinalityProperties}, both {@code @Pattern(regexp =
 * "ETHEREUM|TRON")}) is the JDK's own {@link #valueOf(String)} — no custom converter exists or is
 * needed, since those config fields are already constrained to exact enum-constant spellings.</p>
 */
public enum Chain {
    ETHEREUM,
    TRON
}
