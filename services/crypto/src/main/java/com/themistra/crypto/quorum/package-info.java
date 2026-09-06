/**
 * Quorum read layer (ARCHITECTURE §6.1) — the part of this service that decides what is true.
 *
 * <p>No single-provider answer ever leaves the service as fact. Everything here is pure decision
 * logic over adapter answers: no network, no persistence, no framework. That is deliberate, since
 * provider disagreement and reorgs are exactly the behaviours that cannot be tested against a
 * live chain.
 */
package com.themistra.crypto.quorum;
