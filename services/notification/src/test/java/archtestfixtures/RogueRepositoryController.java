package archtestfixtures;

import org.springframework.data.repository.CrudRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** FU-1/FU-2 port (open-issues sweep, 2026-10-20). Test-only fixture for
 * {@code com.themistra.notification.ControllerRulesArchitectureTest}. It sits outside
 * {@code com.themistra.notification}, so component scanning never picks it up. */
@RestController
public class RogueRepositoryController {

    private final CrudRepository<Object, Long> repository;

    public RogueRepositoryController(CrudRepository<Object, Long> repository) {
        this.repository = repository;
    }

    @GetMapping("/rogue/count")
    @PreAuthorize("isAuthenticated()")
    public long count() {
        return repository.count();
    }
}
