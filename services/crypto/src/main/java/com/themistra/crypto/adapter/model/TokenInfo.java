package com.themistra.crypto.adapter.model;

/**
 * A token's identity and display metadata for one chain (design.md L7 / requirements.md R13):
 * identity is {@code contractAddress} only, paired with the owning {@code ChainAdapter}'s own
 * {@code chain()} — never a symbol.
 *
 * <p><b>Do not rely on {@code equals()}/{@code hashCode()} for identity comparison or as a
 * {@code Set}/{@code Map} key.</b> As a record, this type's generated equality covers every
 * component, including {@code symbol} and {@code decimals} — two instances describing the same
 * token with a differently-cased or differently-sourced {@code symbol} would compare unequal.
 * Business code MUST compare/key by {@code tokenInfo.contractAddress()} explicitly. (Real
 * token-allowlist lookups, task 11, are DB-keyed queries against {@code chain}+{@code
 * contract_address} columns, never Java object comparisons — this rule has no functional impact
 * there, only on any future in-memory use of this type.)</p>
 */
public record TokenInfo(
        String contractAddress,
        String symbol,
        int decimals
) {
}
