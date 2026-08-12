package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CustomNpcsYsmCompat.MOD_ID, value = Dist.CLIENT)
public final class ProxyNameTagSuppressor {
    private ProxyNameTagSuppressor() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (AnimatedNpcRenderBridge.isProxyPlayer(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }
}
