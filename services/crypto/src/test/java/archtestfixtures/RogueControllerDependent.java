package archtestfixtures;

import com.themistra.crypto.watch.WatchController;

/** FU-1/FU-2 port (open-issues sweep, 2026-10-20). Test-only fixture for
 * {@code com.themistra.crypto.ControllerRulesArchitectureTest}. It sits outside
 * {@code com.themistra.crypto}, so component scanning never picks it up. */
public class RogueControllerDependent {

    private WatchController controller;

    WatchController controller() {
        return controller;
    }
}
