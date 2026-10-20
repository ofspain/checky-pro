package archtestfixtures;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** FU-1/FU-2 port (open-issues sweep, 2026-10-20). Test-only fixture for
 * {@code com.themistra.notification.ControllerRulesArchitectureTest}. It sits outside
 * {@code com.themistra.notification}, so component scanning never picks it up. */
@RestController
@PreAuthorize("isAuthenticated()")
public class RogueClassGuardedController {

    @GetMapping("/rogue/class-guarded")
    public String handler() {
        return "closed";
    }
}
