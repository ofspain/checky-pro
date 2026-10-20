package archtestfixtures;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FU-1/FU-2 port (open-issues sweep, 2026-10-20). Test-only fixture for
 * {@code com.themistra.crypto.ControllerRulesArchitectureTest}. It sits outside
 * {@code com.themistra.crypto}, so component scanning never picks it up. */
@RestController
@RequestMapping("/rogue")
public class RogueOpenController {

    @GetMapping("/open")
    @PreAuthorize("permitAll()")
    public String open() {
        return "open";
    }
}
