package com.arxyt.customnpcsysmcompat;

import com.arxyt.dominionsword.api.DominionControlApi;
import com.arxyt.dominionsword.api.DominionTaczReloadApi;
import com.arxyt.dominionsword.api.DominionUnitActions;

/** Loaded reflectively only after Forge confirms that Dominion Sword is present. */
public final class DominionCompatBootstrap {
    private DominionCompatBootstrap() {
    }

    public static void load() {
        DominionCommandBridge.load();
        DominionCombatBalance.load();
        DominionControlApi.registerAdapter(new DominionYsmNpcAdapter());
        try {
            DominionUnitActions.register(new DominionYsmNpcActionProvider());
            DominionUnitActions.registerCatalog(new YsmActionCatalogProvider());
        } catch (LinkageError error) {
            CustomNpcsYsmCompat.LOGGER.info("Dominion Sword predates the unit-action API; YSM action-list integration is disabled");
        }
        try {
            DominionTaczReloadApi.registerAdapter(new DominionYsmNpcReloadAdapter());
        } catch (LinkageError error) {
            CustomNpcsYsmCompat.LOGGER.warn("Dominion Sword lacks the shared TACZ reload API; CNPC reload integration is disabled", error);
        }
    }
}
