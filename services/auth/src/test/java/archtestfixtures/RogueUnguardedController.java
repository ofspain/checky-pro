package archtestfixtures;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** FU-1/FU-2 port (open-issues sweep, 2026-10-20). Test-only fixture for
 * {@code com.themistra.auth.ControllerRulesArchitectureTest}. It sits outside
 * {@code com.themistra.auth}, so component scanning never picks it up. */
@RestController
public class RogueUnguardedController {

    @GetMapping("/rogue/unguarded")
    public String unguarded() {
        return "open";
    }

    @PostMapping("/rogue/guarded")
    @PreAuthorize("isAuthenticated()")
    public String guarded() {
        return "closed";
    }
}
