package com.arxyt.customnpcsysmcompat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CustomNpcsYsmCompat.MOD_ID)
public final class CustomNpcsYsmCompat {
    public static final String MOD_ID = "customnpcs_ysm_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CustomNpcsYsmCompat() {
        LOGGER.info("CustomNPCs YSM Compat loaded");
    }
}
