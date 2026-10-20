package com.themistra.payment.archtestfixtures;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FU-2 (open-issues sweep, 2026-10-19). A controller with one handler that has no
 * {@code @PreAuthorize}, so {@code ArchitectureTest}'s handler rule can be shown to fail on a
 * real violation. {@link #guarded()} is the control: it must not be reported.
 *
 * <p>{@code @SpringBootApplication} scans test classes too, so the never-active profile keeps
 * this out of every real application context.</p>
 */
@RestController
@Profile(RogueUnguardedController.NEVER_ACTIVE)
public class RogueUnguardedController {

    public static final String NEVER_ACTIVE = "archunit-fixture-never-active";

    @GetMapping("/rogue/unguarded")
    public String unguarded() {
        return "open";
    }

    @PostMapping("/rogue/guarded")
    @PreAuthorize("hasRole('MERCHANT')")
    public String guarded() {
        return "closed";
    }
}
