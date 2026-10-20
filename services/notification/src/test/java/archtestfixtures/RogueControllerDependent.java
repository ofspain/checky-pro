package archtestfixtures;

import com.themistra.notification.inapp.InappReadController;

/** FU-1/FU-2 port (open-issues sweep, 2026-10-20). Test-only fixture for
 * {@code com.themistra.notification.ControllerRulesArchitectureTest}. It sits outside
 * {@code com.themistra.notification}, so component scanning never picks it up. */
public class RogueControllerDependent {

    private InappReadController controller;

    InappReadController controller() {
        return controller;
    }
}
