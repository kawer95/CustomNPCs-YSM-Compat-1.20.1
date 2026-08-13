package com.arxyt.customnpcsysmcompat;

import com.arxyt.dominionsword.api.DominionControlApi;

/** Loaded reflectively only after Forge confirms that Dominion Sword is present. */
public final class DominionCompatBootstrap {
    private DominionCompatBootstrap() {
    }

    public static void load() {
        DominionCommandBridge.load();
        DominionControlApi.registerAdapter(new DominionYsmNpcAdapter());
    }
}
