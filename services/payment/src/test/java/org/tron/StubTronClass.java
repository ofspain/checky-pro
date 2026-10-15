package org.tron;

/**
 * T15 (L3). A deliberately trivial, empty stand-in for the real Tron SDK ({@code
 * io.github.tronprotocol:trident}, package {@code org.tron.trident}), which is never a
 * dependency of {@code payment-service} (confirmed against {@code pom.xml} - unlike {@code
 * crypto-service}, this module never adds it, by design). Exists solely so {@code
 * ArchitectureTest}'s negative-proof rogue fixture can write a real, compiling {@code import
 * org.tron...} statement without pulling in the actual third-party JAR just to prove the new L3
 * ArchUnit rule can genuinely fail (Phase 3, Kimi Finding 2).
 */
public class StubTronClass {
}
