package com.arxyt.customnpcsysmcompat.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opens reload timing around YSM's own animation update, not around Forge's later render event.
 * YSM evaluates its bones before RenderLivingEvent.Pre, so an event-based scope can calculate the
 * right speed while still missing every animation-player call.
 */
public final class YsmReloadRenderScope {
    private static final Map<LivingEntity, YsmReloadClock> CLOCKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private YsmReloadRenderScope() {
    }

    public static boolean begin(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !eligible(living)) return false;
        YsmReloadClock clock = CLOCKS.computeIfAbsent(living, ignored -> new YsmReloadClock());
        YsmReloadTiming.sync(clock, living);
        YsmReloadTimeContext.begin(clock);
        return true;
    }

    public static void end(boolean active) {
        if (active) YsmReloadTimeContext.end();
    }

    public static void clear() {
        CLOCKS.clear();
        YsmReloadTimeContext.end();
    }

    private static boolean eligible(LivingEntity entity) {
        return AnimatedNpcRenderBridge.isProxyPlayer(entity)
                || entity instanceof EntityMaid maid && maid.isYsmModel();
    }
}
