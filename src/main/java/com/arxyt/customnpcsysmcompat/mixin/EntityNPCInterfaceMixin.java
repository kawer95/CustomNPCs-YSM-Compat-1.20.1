package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.YsmTaczGunGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class EntityNPCInterfaceMixin extends PathfinderMob {
    protected EntityNPCInterfaceMixin(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "updateTasks", at = @At("TAIL"), remap = false)
    private void customnpcsYsmCompat$installGunGoal(CallbackInfo ci) {
        if (!level().isClientSide) {
            goalSelector.addGoal(0, new YsmTaczGunGoal((EntityNPCInterface) (Object) this));
        }
    }
}
