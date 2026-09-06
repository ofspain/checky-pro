/**
 * Chain adapter layer — one adapter per provider per chain, behind a common interface
 * (ARCHITECTURE §3.4).
 *
 * <p>This package holds no notion of truth. It reports what a single provider said; deciding
 * what is true is the quorum layer's job.
 */
package com.themistra.crypto.chain;
