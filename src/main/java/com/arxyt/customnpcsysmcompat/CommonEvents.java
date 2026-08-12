package com.arxyt.customnpcsysmcompat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.IItemHandler;
import noppes.npcs.entity.EntityNPCInterface;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CommonEvents {
    private static final ResourceLocation AMMO_INVENTORY =
            new ResourceLocation(CustomNpcsYsmCompat.MOD_ID, "ammo_inventory");

    @SubscribeEvent
    public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityNPCInterface npc) {
            NpcAmmoItemHandler handler = new NpcAmmoItemHandler(npc);
            LazyOptional<IItemHandler> optional = LazyOptional.of(() -> handler);
            event.addCapability(AMMO_INVENTORY, new net.minecraftforge.common.capabilities.ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability,
                                                         @Nullable net.minecraft.core.Direction side) {
                    return ForgeCapabilities.ITEM_HANDLER.orEmpty(capability, optional);
                }
            });
            event.addListener(optional::invalidate);
        }
    }

}
