package com.themistra.payment.chainverify;

import com.themistra.payment.chainverify.dto.VerifyRequest;
import com.themistra.payment.chainverify.dto.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The public landing page's one call: paste a transaction hash, get the chain's agreed answer, and
 * have it recorded.
 *
 * <p><b>This is the only unauthenticated write on this service, and it is a deliberate exception
 * to a rule, not an oversight.</b> {@code PublicEndpoints} previously said in as many words that no
 * method-scoped public writes exist here. Both paths below are therefore listed in
 * {@code PublicEndpoints.PATTERNS} and annotated {@code permitAll()}: the architecture test
 * cross-checks those two lists against each other, so a public path can only exist if it was
 * declared in both places on purpose.</p>
 *
 * <p><b>What a public caller can and cannot do.</b> It can cause one read against crypto-service
 * (which fans out to three chain providers) and one append-only row. It cannot read anybody's
 * invoice, payment or receipt, and it cannot change a stored row - the table grants
 * {@code INSERT} and {@code SELECT} only. The real exposure is cost and noise, not disclosure:
 * before this is served on a public URL it needs a rate limit at the edge, which is recorded in
 * the branch's README rather than left to be noticed.</p>
 */
@RestController
@RequestMapping("/api/v1/verifications")
public class ChainVerificationController {

    private final ChainVerificationService service;

    public ChainVerificationController(ChainVerificationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("permitAll()")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        return service.verify(request.txHash(), request.chain());
    }

    /** The most recent verifications, newest first — the list the landing page shows under the form. */
    @GetMapping
    @PreAuthorize("permitAll()")
    public List<VerifyResponse> recent() {
        return service.recent();
    }

    /**
     * One recorded answer, so a check can be linked, reopened and printed.
     *
     * <p>The id is the only key, and it is a random UUID — guessing one is the whole attack, and it
     * yields a public chain fact that any explorer shows anyway. Nothing merchant-scoped is
     * reachable from here.</p>
     */
    @GetMapping("/{verificationId}")
    @PreAuthorize("permitAll()")
    public VerifyResponse one(@PathVariable UUID verificationId) {
        return service.find(verificationId).orElseThrow(ChainVerificationNotFoundException::new);
    }
}
