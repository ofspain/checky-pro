package com.themistra.payment.archtestfixtures;

/**
 * T15 — deliberately depends on all three banned SDK stand-ins ({@code org.web3j},
 * {@code org.tron}, {@code software.amazon.awssdk.services.kms}) via a public field each, so
 * {@code ArchitectureTest}'s new L3 rule can be proven to actually fail against a genuine
 * violation, not just pass on already-clean code - mirrors {@link RogueFloatingPointMoneyUser}'s
 * own identical purpose for L6. Never swept into the real canary's own scan ({@code
 * ImportOption.DoNotIncludeTests} excludes every test-source class regardless of package);
 * referenced directly via a class literal by {@code ArchitectureTest} (a different package), so
 * this class and its members are {@code public}.
 */
public class RogueKmsAndChainClientUser {

    public org.web3j.StubWeb3jClass web3jField;

    public org.tron.StubTronClass tronField;

    public software.amazon.awssdk.services.kms.StubKmsClass kmsField;
}
