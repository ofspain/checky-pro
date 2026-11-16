package com.themistra.payment.archtestfixtures;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only fixture for {@code ArchitectureTest.everyPermitAllHandlerIsDeclaredInPublicEndpoints}:
 * a handler that opens itself with {@code permitAll()} without its path ever being written into
 * {@code PublicEndpoints.PATTERNS}. Outside {@code com.themistra.payment}'s scanned packages by
 * package name, so component scanning never picks it up.
 */
@RestController
public class RoguePublicController {

    @GetMapping("/rogue/public")
    @PreAuthorize("permitAll()")
    public String open() {
        return "open";
    }
}
