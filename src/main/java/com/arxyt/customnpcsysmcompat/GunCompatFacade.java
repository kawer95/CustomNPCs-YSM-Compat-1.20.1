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

    default void syncClientState(LivingEntity source, LivingEntity renderProxy) {
    }
}
