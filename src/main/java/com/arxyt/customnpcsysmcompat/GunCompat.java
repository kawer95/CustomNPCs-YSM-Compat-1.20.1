package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;

public final class GunCompat {
    private static volatile GunCompatFacade facade;
    private static final java.util.concurrent.atomic.AtomicBoolean RUNTIME_ERROR_REPORTED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private GunCompat() {
    }

    public static void load() {
        try {
            facade = (GunCompatFacade) Class.forName(
                    "com.arxyt.customnpcsysmcompat.tacz.Tacz115Compat")
                    .getConstructor().newInstance();
            CustomNpcsYsmCompat.LOGGER.info("TaCZ 1.1.5 gun control enabled");
        } catch (Throwable error) {
            CustomNpcsYsmCompat.LOGGER.error("TaCZ was found but its 1.1.5 adapter failed to load", error);
        }
    }

    public static boolean active(EntityNPCInterface npc) {
        GunCompatFacade current = facade;
        if (current == null || npc == null
                || !com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess.get(npc.display).enabled()) {
            return false;
        }
        try {
            return current.isGun(npc.getMainHandItem());
        } catch (Throwable error) {
            reportRuntimeError(error);
            return false;
        }
    }

    public static GunCompatFacade facade() {
        return facade;
    }

    public static boolean isGun(ItemStack stack) {
        try {
            return facade != null && facade.isGun(stack);
        } catch (Throwable error) {
            reportRuntimeError(error);
            return false;
        }
    }

    public static void syncClientState(net.minecraft.world.entity.LivingEntity source,
                                       net.minecraft.world.entity.LivingEntity renderProxy) {
        GunCompatFacade current = facade;
        if (current != null) {
            try {
                current.syncClientState(source, renderProxy);
            } catch (Throwable error) {
                reportRuntimeError(error);
            }
        }
    }

    public static void reportRuntimeError(Throwable error) {
        if (RUNTIME_ERROR_REPORTED.compareAndSet(false, true)) {
            CustomNpcsYsmCompat.LOGGER.error(
                    "TaCZ gun control encountered an error; affected actions will safely pause", error);
        }
    }
}
