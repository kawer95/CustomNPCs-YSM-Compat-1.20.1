package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.YsmTaczGunGoal;
import com.arxyt.customnpcsysmcompat.YsmTaczProneGunGoal;
import com.arxyt.customnpcsysmcompat.YsmTaczSentryGunGoal;
import com.arxyt.customnpcsysmcompat.YsmTaczWatchGunGoal;
import com.arxyt.customnpcsysmcompat.NpcGunAimLock;
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
            // Prone commands disable MOVE to prevent the native crawl animation from being
            // overwritten. Give the LOOK-only prone shooter precedence over the normal
            // MOVE+LOOK goal so a prone ordered target can still operate its TaCZ weapon.
            goalSelector.addGoal(-1, new YsmTaczProneGunGoal((EntityNPCInterface) (Object) this));
            // A stationary standing watch has the same arbitration requirement: CustomNPCs'
            // native movement/look tasks otherwise prevent the general gun goal from ticking.
            goalSelector.addGoal(-1, new YsmTaczWatchGunGoal((EntityNPCInterface) (Object) this));
            goalSelector.addGoal(0, new YsmTaczGunGoal((EntityNPCInterface) (Object) this));
            // The normal gun goal owns MOVE and is intentionally disabled by Dominion's
            // HOLD policy. This LOOK-only goal can keep firing without reopening movement.
            goalSelector.addGoal(0, new YsmTaczSentryGunGoal((EntityNPCInterface) (Object) this));
        }
    }

    /**
     * EntityNPCInterface's runtime tick name in the shipped CustomNPCs 1.20.1 jar.
     * This runs after PathfinderMob has allowed body/head controllers to process, so the
     * commanded aim direction is the one replicated to observing clients.
     */
    @Inject(method = "m_8119_", at = @At("TAIL"), remap = false, require = 0)
    private void customnpcsYsmCompat$maintainCommandGunAim(CallbackInfo ci) {
        if (!level().isClientSide) {
            NpcGunAimLock.maintain((EntityNPCInterface) (Object) this);
        }
    }

    /**
     * CustomNPCs overrides the obfuscated Mob#doHurtTarget method directly. Injecting into
     * Mob#doHurtTarget therefore never sees NPC melee hits; this must target the CNPC override.
     */
    @Inject(method = {"m_7327_", "doHurtTarget"}, at = @At("HEAD"), cancellable = true, remap = false)
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
