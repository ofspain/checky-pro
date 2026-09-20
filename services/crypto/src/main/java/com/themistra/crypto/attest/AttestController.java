package com.themistra.crypto.attest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /internal/v1/attest} (L10) - VERBATIM shape, `design.md` §4c. Scope enforcement
 * ({@code internal.crypto:write}) is already fully handled by {@code ResourceServerConfig} (T03); this
 * controller adds no security logic of its own, mirroring {@code watch.WatchController}'s exact
 * precedent. */
@RestController
@RequestMapping("/internal/v1/attest")
public class AttestController {

    private final AttestationService attestationService;

    public AttestController(AttestationService attestationService) {
        this.attestationService = attestationService;
    }

    @PostMapping
    public ResponseEntity<AttestResponse> attest(@Valid @RequestBody AttestRequest request) {
        return ResponseEntity.ok(attestationService.attest(request));
    }
}
