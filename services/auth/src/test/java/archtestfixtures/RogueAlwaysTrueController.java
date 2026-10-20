package archtestfixtures;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kimi sweep review, Finding 2 (2026-10-20). Test-only fixture for
 * {@code com.themistra.auth.ControllerRulesArchitectureTest}: an always-true rule opens the
 * endpoint without saying {@code permitAll()}. Outside {@code com.themistra.auth}, so component
 * scanning never picks it up. */
@RestController
public class RogueAlwaysTrueController {

    @GetMapping("/rogue/always-true")
    @PreAuthorize("true")
    public String open() {
        return "open";
    }
}
