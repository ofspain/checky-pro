package com.themistra.payment.archtestfixtures;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** FU-2 control: a class-level {@code @PreAuthorize} covers every handler, so this must pass. */
@RestController
@PreAuthorize("hasRole('MERCHANT')")
@Profile(RogueUnguardedController.NEVER_ACTIVE)
public class RogueClassGuardedController {

    @GetMapping("/rogue/class-guarded")
    public String handler() {
        return "closed";
    }
}
