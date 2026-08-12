package com.arxyt.customnpcsysmcompat.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla Entity shared flags hidden by CustomNPC's isInvisible() override. */
@Mixin(Entity.class)
public interface EntitySharedFlagAccessor {
    @Invoker("getSharedFlag")
    boolean customnpcsYsmCompat$getSharedFlag(int flag);
}
