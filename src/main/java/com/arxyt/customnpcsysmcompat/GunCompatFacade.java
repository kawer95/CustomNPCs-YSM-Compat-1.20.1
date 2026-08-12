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

    void stop(EntityNPCInterface shooter);

    default void syncClientState(LivingEntity source, LivingEntity renderProxy) {
    }
}
