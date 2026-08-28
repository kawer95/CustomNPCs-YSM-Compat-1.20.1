package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public interface GunCompatFacade {
    enum RangeClass { NEAR, MEDIUM, LONG }

    record Action(int delayTicks, boolean fired) {
        public static Action waitFor(int ticks) {
            return new Action(Math.max(1, ticks), false);
        }
    }

    boolean isGun(ItemStack stack);

    /** True only when the optional gun system classifies this weapon as a machine gun. */
    default boolean isMachineGun(ItemStack stack) {
        return false;
    }

    RangeClass rangeClass(ItemStack stack);

    Action operate(EntityNPCInterface shooter, LivingEntity target);

    /** Releases transient gun-combat state after a goal yields control. */
    void stop(EntityNPCInterface shooter);

    /**
     * Releases gun-combat state after an explicit command cancellation.
     *
     * <p>Adapters may preserve an aim state while a queued command switches targets, but must
     * honor this forced exit even when Dominion has not cleared its persistent queue until later
     * in the same server tick.</p>
     */
    default void stop(EntityNPCInterface shooter, boolean forceExitAim) {
        stop(shooter);
    }

    /**
     * Mirrors the CustomNPCs native crawl action into the optional gun system.
     *
     * <p>This is invoked before the NPC's normal living tick continues, so a
     * weapon adapter can validate and apply stance state before its own tick
     * hook updates pose and shooting data.</p>
     */
    default void syncCrawlState(EntityNPCInterface shooter) {
    }

    /** Queues one genuine optional-gun-system reload; implementations must not edit ammunition NBT. */
    default boolean requestReload(EntityNPCInterface shooter) {
        return false;
    }

    /** Advances queued reloads and applies the adapter's own idle auto-reload policy. */
    default void tickReload(EntityNPCInterface shooter) {
    }

    /** True while a manual reload is intentionally blocking this NPC's firing goal. */
    default boolean reloadRequested(EntityNPCInterface shooter) {
        return false;
    }

    default void syncClientState(LivingEntity source, LivingEntity renderProxy) {
    }
}
