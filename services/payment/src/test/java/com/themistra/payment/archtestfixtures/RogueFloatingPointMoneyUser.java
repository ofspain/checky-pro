package com.themistra.payment.archtestfixtures;

/**
 * T05 — deliberately violates L6 three different ways (a field, a method return type, and a
 * method parameter), so {@code ArchitectureTest}'s own
 * {@code shouldRejectFloatingPointMoneyTypesAtArchUnit} rule can be proven to actually fail
 * against a genuine violation, not just pass on already-clean code. Never swept into the real
 * canary's own scan ({@code ImportOption.DoNotIncludeTests} excludes every test-source class
 * regardless of package); referenced directly via a class literal by
 * {@code ArchitectureTest} (a different package), so this class and its members are {@code
 * public} — mirrors {@code services/notification}'s own identical rogue-fixture precedent.
 */
public class RogueFloatingPointMoneyUser {

    public double amount = 0.0;

    public float getFloatAmount() {
        return 0.0f;
    }

    public void acceptDoubleAmount(Double amount) {
        // intentionally empty - exists only to carry a forbidden parameter type
    }
}
