package com.arxyt.customnpcsysmcompat;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only diagnostic switches; normal rendering performs no trace logging. */
public final class RenderStabilityConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("diagnostics");
        ENABLED = builder.comment("Log rate-limited YSM proxy movement, combat and visibility state.")
                .define("renderStability", false);
        builder.pop();
        SPEC = builder.build();
    }

    private RenderStabilityConfig() {
    }
}
