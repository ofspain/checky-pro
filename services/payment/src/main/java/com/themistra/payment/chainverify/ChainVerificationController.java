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
 * {@code INSERT} and {@code SELECT} only.
 *
 * <p><b>There is deliberately no "list recent checks" endpoint.</b> One existed and was removed
 * before this went anywhere public: it returned the last ten checks - hashes, addresses, amounts -
 * to any caller at all, which tells a stranger what payments other people are investigating. The
 * landing page keeps its own history in the visitor's browser instead, so a check is visible to
 * the person who ran it and to anyone they hand the receipt link to, and to nobody else.
 *
 * <p>Both remaining paths are rate limited per client by
 * {@link VerificationRateLimitFilter}; the POST fans out to three providers and is the one that
 * costs real money.</p>
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
