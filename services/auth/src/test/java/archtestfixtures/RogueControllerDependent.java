package archtestfixtures;

import com.themistra.auth.account.AccountController;

/** FU-1/FU-2 port (open-issues sweep, 2026-10-20). Test-only fixture for
 * {@code com.themistra.auth.ControllerRulesArchitectureTest}. It sits outside
 * {@code com.themistra.auth}, so component scanning never picks it up. */
public class RogueControllerDependent {

    private AccountController controller;

    AccountController controller() {
        return controller;
    }
}
