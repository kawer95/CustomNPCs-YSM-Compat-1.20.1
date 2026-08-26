package com.arxyt.customnpcsysmcompat;

import noppes.npcs.entity.EntityNPCInterface;

/**
 * Defines the one CustomNPCs animation state that represents a physical crawl.
 *
 * <p>CustomNPCs synchronizes this state as an animation integer and also uses it
 * to reduce the entity's dimensions.  TaCZ and the client-only YSM proxy consume
 * this helper instead of independently guessing from movement, pose, or water
 * state.  The helper deliberately does not create a crawl state: the CustomNPC
 * editor/script remains authoritative.</p>
 */
public final class NpcCrawlState {
    /** CustomNPCs' public {@code AnimationType.CRAWL} value in the 1.20.1 GBPort build. */
    public static final int CRAWL_ANIMATION = 7;

    private NpcCrawlState() {
    }

    /** Returns whether the NPC is using its native crawl action. */
    public static boolean isCrawling(EntityNPCInterface npc) {
        return npc != null && isCrawling(npc.currentAnimation);
    }

    /** Value-only form kept deterministic for tests and adapter decisions. */
    static boolean isCrawling(int currentAnimation) {
        return currentAnimation == CRAWL_ANIMATION;
    }

    /**
     * TaCZ may enter its crawl state only for an active, locally validated
     * TaCZ-gun NPC.  The TaCZ adapter retains final authority over all
     * compatibility checks.
     */
    public static boolean requestsTaczCrawl(int currentAnimation, boolean activeTaczGun) {
        return activeTaczGun && isCrawling(currentAnimation);
    }
}
