package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Opens the same per-entity YSM clock scope around real maid rendering. */
@Mod.EventBusSubscriber(modid = CustomNpcsYsmCompat.MOD_ID, value = Dist.CLIENT)
public final class YsmMaidReloadRenderScope {
    private static final Map<EntityMaid, YsmReloadClock> CLOCKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private YsmMaidReloadRenderScope() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !maid.isYsmModel()) return;
        YsmReloadClock clock = CLOCKS.computeIfAbsent(maid, ignored -> new YsmReloadClock());
        YsmReloadTiming.sync(clock, maid);
        YsmReloadTimeContext.begin(clock);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() instanceof EntityMaid maid && maid.isYsmModel()) {
            YsmReloadTimeContext.end();
        }
    }
}
