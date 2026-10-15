package software.amazon.awssdk.services.kms;

/**
 * T15 (L3). A deliberately trivial, empty stand-in for the real AWS KMS SDK class at this exact
 * package, which is never a dependency of {@code payment-service} (confirmed against {@code
 * pom.xml} - this module depends on {@code software.amazon.awssdk:s3} for the receipt WORM
 * store, task 18, but never {@code kms}, by design - that is L3 itself). Exists solely so {@code
 * ArchitectureTest}'s negative-proof rogue fixture can write a real, compiling {@code import
 * software.amazon.awssdk.services.kms...} statement without pulling in the actual KMS JAR just
 * to prove the new L3 ArchUnit rule can genuinely fail (Phase 3, Kimi Finding 2).
 */
public class StubKmsClass {
}
