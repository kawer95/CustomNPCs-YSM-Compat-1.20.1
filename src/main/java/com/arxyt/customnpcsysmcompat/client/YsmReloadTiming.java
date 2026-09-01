package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Shared TaCZ-to-YSM timing calculation for CNPC proxies and real YSM maids. */
final class YsmReloadTiming {
    private YsmReloadTiming() {
    }

    static void sync(YsmReloadClock clock, LivingEntity entity) {
        boolean active = reloadActive(entity);
        float speed = reloadPlaybackSpeed(entity);
        boolean wasActive = clock.active();
        clock.target(active, speed);
        if (active != wasActive) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-RELOAD-TIMING] entityId={} entityType={} active={} playbackSpeed={}",
                    entity.getId(), entity.getType(), active, clock.speed());
        }
    }

    private static boolean reloadActive(LivingEntity entity) {
        try {
            ReloadState state = IGunOperator.fromLivingEntity(entity).getSynReloadState();
            return state != null && state.getCountDown() != ReloadState.NOT_RELOADING_COUNTDOWN;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Matches YSM's fixed TAC clip to TaCZ's empty/tactical reload duration. */
    private static float reloadPlaybackSpeed(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return 1.0F;
        try {
            return TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> {
                var reload = index.getGunData().getReloadData();
                if (reload == null || reload.getCooldown() == null) return 1.0F;
                boolean empty = gun.getCurrentAmmoCount(stack) <= 0;
                float seconds = empty ? reload.getCooldown().getEmptyTime()
                        : reload.getCooldown().getTacticalTime();
                if (!(seconds > 0.0F)) return 1.0F;
                String type = index.getType();
                float clipSeconds = GunTabType.PISTOL.name().equalsIgnoreCase(type) ? 1.7083F
                        : GunTabType.RPG.name().equalsIgnoreCase(type) ? 3.0417F : 1.75F;
                return Math.max(0.05F, Math.min(4.0F, clipSeconds / seconds));
            }).orElse(1.0F);
        } catch (RuntimeException | LinkageError ignored) {
            return 1.0F;
        }
    }
}
