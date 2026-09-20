package archtestfixtures;

import com.themistra.crypto.attest.KmsSigner;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * Deliberately violates both of {@code com.themistra.crypto.attest.KmsSignerArchitectureTest}'s rules -
 * a real class depending on both {@link KmsSigner} and the KMS SDK directly, from outside {@code
 * com.themistra.crypto.attest} entirely. Exists solely so that test's negative-proof case can prove the
 * rules actually fail on a genuine violation, not just pass on already-clean code (Kimi Phase 11 Gap 1).
 *
 * <p>Deliberately placed in this standalone top-level {@code archtestfixtures} package - not under
 * {@code com.themistra.crypto} at all - so the real production canary's own
 * {@code importPackages("com.themistra.crypto")} scan can never sweep this class up and fail the real
 * build. Referenced only by that one negative-proof test, via {@code ClassFileImporter().importClasses(...)}
 * naming this class explicitly, never by any package-wide scan.</p>
 */
public class RogueAttestReferencer {
    private final KmsSigner kmsSigner = null;
    private final KmsClient kmsClient = null;
}
