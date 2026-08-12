package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.YsmTaczGunGoal;
import com.arxyt.customnpcsysmcompat.animation.DelayedMeleeAttack;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /**
     * CustomNPCs overrides the obfuscated Mob#doHurtTarget method directly. Injecting into
     * Mob#doHurtTarget therefore never sees NPC melee hits; this must target the CNPC override.
     */
    @Inject(method = "m_7327_", at = @At("HEAD"), cancellable = true, remap = false)
    private void customnpcsYsmCompat$queueMeleeHit(Entity target,
                                                   CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!level().isClientSide
                && YsmDisplayAccess.get(npc.display).enabled()
                && DelayedMeleeAttack.intercept(npc, target)) {
            cir.setReturnValue(false);
        }
    }
}
