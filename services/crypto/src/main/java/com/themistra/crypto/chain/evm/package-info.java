/**
 * EVM chain access via web3j (ARCHITECTURE §3.4).
 *
 * <p>The only package that depends on web3j. Everything above it works in terms of
 * {@link com.themistra.crypto.chain.ChainAdapter} and plain values, so swapping the client library
 * — or adding a non-EVM chain — does not reach the quorum layer.
 */
package com.themistra.crypto.chain.evm;
