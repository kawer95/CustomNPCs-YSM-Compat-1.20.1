package com.arxyt.customnpcsysmcompat;

import net.minecraftforge.common.ForgeConfigSpec;

public final class YsmTaczConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue LONG_DISTANCE;
    public static final ForgeConfigSpec.IntValue MEDIUM_DISTANCE;
    public static final ForgeConfigSpec.IntValue NEAR_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FORWARD_SPEED;
    public static final ForgeConfigSpec.DoubleValue SIDEWAYS_SPEED;
    public static final ForgeConfigSpec.IntValue AUTO_RELOAD_BELOW_HALF_SECONDS;
    public static final ForgeConfigSpec.IntValue AUTO_RELOAD_BELOW_TWO_THIRDS_SECONDS;
    public static final ForgeConfigSpec.IntValue AUTO_RELOAD_NON_FULL_SECONDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("taczGunControl");
        LONG_DISTANCE = builder.defineInRange("longDistance", 64, 1, 512);
        MEDIUM_DISTANCE = builder.defineInRange("mediumDistance", 48, 1, 512);
        NEAR_DISTANCE = builder.defineInRange("nearDistance", 32, 1, 512);
        FORWARD_SPEED = builder.defineInRange("strafeForward", 0.4D, 0.0D, 1.0D);
        SIDEWAYS_SPEED = builder.defineInRange("strafeSideways", 0.2D, 0.0D, 1.0D);
        builder.push("autoReload");
        AUTO_RELOAD_BELOW_HALF_SECONDS = builder
                .comment("Idle seconds before a YSM CustomNPC reloads below 50 percent magazine capacity")
                .defineInRange("belowHalfSeconds", 10, 1, 3600);
        AUTO_RELOAD_BELOW_TWO_THIRDS_SECONDS = builder
                .comment("Idle seconds before a YSM CustomNPC reloads below 66 percent capacity. Must be greater than belowHalfSeconds.")
                .defineInRange("belowTwoThirdsSeconds", 20, 1, 3600);
        AUTO_RELOAD_NON_FULL_SECONDS = builder
                .comment("Idle seconds before a YSM CustomNPC reloads any non-full magazine. Must be greater than belowTwoThirdsSeconds.")
                .defineInRange("nonFullSeconds", 30, 1, 3600);
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private YsmTaczConfig() {
    }
}
