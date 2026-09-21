package com.arxyt.customnpcsysmcompat;

/** Explicit opt-in for high-frequency YSM/CNPC combat traces. */
public final class VerboseDiagnostics {
    private static final String PROPERTY = "customnpcs_ysm_compat.diagnostics";

    private VerboseDiagnostics() {}

    public static boolean enabled() { return Boolean.getBoolean(PROPERTY); }
}
