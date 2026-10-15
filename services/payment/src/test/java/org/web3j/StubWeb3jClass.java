package org.web3j;

/**
 * T15 (L3). A deliberately trivial, empty stand-in for the real {@code web3j} SDK, which is
 * never a dependency of {@code payment-service} (confirmed against {@code pom.xml} - unlike
 * {@code crypto-service}, this module never adds it, by design). Exists solely so {@code
 * ArchitectureTest}'s negative-proof rogue fixture can write a real, compiling {@code import
 * org.web3j...} statement without pulling in the actual third-party JAR just to prove the new
 * L3 ArchUnit rule can genuinely fail (Phase 3, Kimi Finding 2).
 */
public class StubWeb3jClass {
}
