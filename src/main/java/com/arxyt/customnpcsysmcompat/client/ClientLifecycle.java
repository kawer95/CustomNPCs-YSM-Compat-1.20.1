package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CustomNpcsYsmCompat.MOD_ID, value = Dist.CLIENT)
public final class ClientLifecycle {
    private ClientLifecycle() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        YsmPlayerTweakPersistence.flushNow();
        YsmPlayerTweakPersistence.resetSession();
        AnimatedNpcRenderBridge.clearCaches();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            YsmPlayerTweakPersistence.flushNow();
            YsmPlayerTweakPersistence.resetSession();
            AnimatedNpcRenderBridge.clearCaches();
        }
    }
}
