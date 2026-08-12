package com.arxyt.customnpcsysmcompat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CustomNpcsYsmCompat.MOD_ID)
public final class CustomNpcsYsmCompat {
    public static final String MOD_ID = "customnpcs_ysm_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CustomNpcsYsmCompat() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, YsmTaczConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new CommonEvents());
        if (ModList.get().isLoaded("tacz")) {
            GunCompat.load();
        }
        if (ModList.get().isLoaded("dominionsword")) {
            DominionCommandBridge.load();
            com.arxyt.dominionsword.api.DominionControlApi.registerAdapter(new DominionYsmNpcAdapter());
        }
        LOGGER.info("CustomNPCs YSM Compat loaded");
    }
}
