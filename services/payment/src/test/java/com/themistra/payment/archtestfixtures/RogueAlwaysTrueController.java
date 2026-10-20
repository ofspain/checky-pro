package com.themistra.payment.archtestfixtures;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kimi sweep review, Finding 2 (2026-10-20). An always-true rule opens the endpoint while still
 * carrying {@code @PreAuthorize}, so {@code ArchitectureTest}'s known-expression check can be shown
 * to catch it. Never-active profile: see {@link RogueUnguardedController}. */
@RestController
@Profile(RogueUnguardedController.NEVER_ACTIVE)
public class RogueAlwaysTrueController {

    @GetMapping("/rogue/always-true")
    @PreAuthorize("true")
    public String open() {
        return "open";
    }
}
