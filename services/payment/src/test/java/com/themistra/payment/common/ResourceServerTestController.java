package com.themistra.payment.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only stand-in for a future invoice/payment endpoint, none of which exists yet (this
 * service's own controllers land at task 6 onward). Gives {@link ResourceServerConfigTest} a
 * real, arbitrary secured path to exercise {@link ResourceServerConfig}'s
 * {@code .anyRequest().authenticated()} rule against. Never shipped in {@code src/main} - test
 * scope only. Mirrors {@code notification-service}'s own identically-purposed
 * {@code ResourceServerTestController}.
 */
@RestController
class ResourceServerTestController {

    @GetMapping("/v1/invoices/test-only")
    ResponseEntity<Void> testOnly() {
        return ResponseEntity.ok().build();
    }
}
