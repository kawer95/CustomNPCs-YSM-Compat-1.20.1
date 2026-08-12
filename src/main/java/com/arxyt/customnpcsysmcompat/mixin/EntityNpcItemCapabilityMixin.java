package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.NpcAmmoItemHandler;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Makes TaCZ see CustomNPC's projectile/drop slots instead of Forge's six equipment slots. */
@Mixin(value = CapabilityProvider.class, remap = false)
public abstract class EntityNpcItemCapabilityMixin {
    @Unique
    private LazyOptional<IItemHandler> customnpcsYsmCompat$ammoInventory = LazyOptional.empty();

    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void customnpcsYsmCompat$getNpcInventory(@Nonnull Capability<T> capability,
                                                         @Nullable Direction side,
                                                         CallbackInfoReturnable<LazyOptional<T>> cir) {
        if (capability != ForgeCapabilities.ITEM_HANDLER
                || !((Object) this instanceof EntityNPCInterface npc)) {
            return;
        }
        if (!customnpcsYsmCompat$ammoInventory.isPresent()) {
            customnpcsYsmCompat$ammoInventory = LazyOptional.of(() -> new NpcAmmoItemHandler(npc));
        }
        cir.setReturnValue(customnpcsYsmCompat$ammoInventory.cast());
    }

    @Inject(method = "invalidateCaps", at = @At("TAIL"), remap = false)
    private void customnpcsYsmCompat$invalidateNpcInventory(CallbackInfo ci) {
        customnpcsYsmCompat$ammoInventory.invalidate();
        customnpcsYsmCompat$ammoInventory = LazyOptional.empty();
    }

    @Inject(method = "reviveCaps", at = @At("TAIL"), remap = false)
    private void customnpcsYsmCompat$reviveNpcInventory(CallbackInfo ci) {
        customnpcsYsmCompat$ammoInventory = LazyOptional.empty();
    }
}
