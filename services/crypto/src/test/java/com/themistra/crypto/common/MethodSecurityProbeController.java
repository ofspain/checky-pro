package com.themistra.crypto.common;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint that every caller passes the URL rules for and every caller fails the
 * method rule for, so {@link MethodSecurityTest} can see what a {@code @PreAuthorize} denial
 * turns into over HTTP (FU-2 port, 2026-10-20). Profile-gated so full-application contexts never
 * load it. */
@RestController
@Profile(MethodSecurityProbeController.PROFILE)
class MethodSecurityProbeController {

    static final String PROFILE = "method-security-probe";

    @GetMapping("/method-security-probe")
    @PreAuthorize("denyAll()")
    String probe() {
        return "unreachable";
    }
}
