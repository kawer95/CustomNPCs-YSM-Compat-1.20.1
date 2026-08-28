package com.arxyt.customnpcsysmcompat;

import com.arxyt.dominionsword.api.DominionControlApi;
import com.arxyt.dominionsword.api.DominionSkills;

/** Loaded reflectively only after Forge confirms that Dominion Sword is present. */
public final class DominionCompatBootstrap {
    private DominionCompatBootstrap() {
    }

    public static void load() {
        DominionCommandBridge.load();
        DominionCombatBalance.load();
        DominionControlApi.registerAdapter(new DominionYsmNpcAdapter());
        DominionSkills.register(new DominionWatchSkillProvider());
        DominionSkills.register(new DominionReloadSkillProvider());
    }
}
