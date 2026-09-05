package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.api.EntitySharedFlagAccess;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla Entity shared flags hidden by CustomNPC's isInvisible() override. */
@Mixin(Entity.class)
public interface EntitySharedFlagAccessor extends EntitySharedFlagAccess {
    @Override
    @Invoker("getSharedFlag")
    boolean customnpcsYsmCompat$getSharedFlag(int flag);
}
