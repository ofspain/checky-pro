package com.themistra.notification.channel;

import com.themistra.notification.delivery.DeliveryLog;

/**
 * T16 - deliberately violates L11: a declared, never-read field of another feature module's own
 * {@code @Entity} type. A real cross-module coupling even though nothing ever calls a method on
 * it (mirroring {@code services/auth}'s/{@code services/crypto}'s own identical
 * dependency-based-not-access-based rationale). Used only by
 * {@code ArchitectureTest.shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation}
 * to prove the rule can genuinely fail against a real, in-module violation - never swept into the
 * real canary's own scan ({@code ImportOption.DoNotIncludeTests} excludes every test-source class
 * regardless of which package it lives in). {@code public} so {@code ArchitectureTest} (a
 * different package) can reference it via a class literal, mirroring both
 * {@code RogueWatchEntityReferencer} and {@code RogueAttestReferencer} in {@code services/crypto}.
 */
public class RogueChannelEntityReferencer {

    private final DeliveryLog deliveryLog = null;
}
