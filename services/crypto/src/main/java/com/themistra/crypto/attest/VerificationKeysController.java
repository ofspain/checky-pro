package com.themistra.crypto.attest;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code GET /.well-known/themistra-verification-keys} (R24) - VERBATIM shape, `design.md` §4c.
 * Public by design - already listed in {@code common.PublicEndpoints}, so no security annotation is
 * needed here, mirroring every other controller's own "no security logic of its own" convention.
 * {@code Cache-Control: no-store} (Phase 3 Finding #4) - a rotated key must never be CDN/browser-cached. */
@RestController
@RequestMapping("/.well-known/themistra-verification-keys")
public class VerificationKeysController {

    private final KmsSigner kmsSigner;

    public VerificationKeysController(KmsSigner kmsSigner) {
        this.kmsSigner = kmsSigner;
    }

    @GetMapping
    public ResponseEntity<VerificationKeysResponse> verificationKeys() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new VerificationKeysResponse(List.of(kmsSigner.publicKeyInfo())));
    }
}
