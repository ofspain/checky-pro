package com.themistra.payment.archtestfixtures;

import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** FU-1 (open-issues sweep, 2026-10-19). A controller reaching a repository directly, skipping
 * the service layer, so the layering rule can be shown to fail. Never-active profile: see
 * {@link RogueUnguardedController}. */
@RestController
@Profile(RogueUnguardedController.NEVER_ACTIVE)
public class RogueRepositoryController {

    private final CrudRepository<Object, Long> repository;

    public RogueRepositoryController(CrudRepository<Object, Long> repository) {
        this.repository = repository;
    }

    @GetMapping("/rogue/count")
    @PreAuthorize("hasRole('MERCHANT')")
    public long count() {
        return repository.count();
    }
}
