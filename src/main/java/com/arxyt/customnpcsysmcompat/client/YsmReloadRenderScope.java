package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Owns one virtual YSM animation clock per eligible entity. The animation-controller mixin calls
 * this directly from YSM's actual update path; no render-thread scope is required, which also
 * covers YSM's worker-thread maid evaluation.
 */
public final class YsmReloadRenderScope {
    private static final Map<LivingEntity, YsmReloadClock> CLOCKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private YsmReloadRenderScope() {
    }

    public static float adjustAnimationTime(Entity entity, float rawTime) {
        if (!(entity instanceof LivingEntity living) || !eligible(living)) return rawTime;
        YsmReloadClock clock = CLOCKS.computeIfAbsent(living, ignored -> new YsmReloadClock());
        YsmReloadTiming.sync(clock, living);
        boolean firstHit = clock.active() && !clock.mixinHit();
        float adjusted = clock.scaleAbsoluteTime(rawTime);
        if (firstHit) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-RELOAD-ANIM] entityId={} entityType={} updateClockMixin=HIT speed={} rawTime={} virtualTime={}",
                    living.getId(), living.getType(), clock.speed(), rawTime, adjusted);
        }
        return adjusted;
    }

    public static void clear() {
        CLOCKS.clear();
    }

    private static boolean eligible(LivingEntity entity) {
        return AnimatedNpcRenderBridge.isProxyPlayer(entity)
                || entity instanceof EntityMaid maid && maid.isYsmModel();
    }
}
