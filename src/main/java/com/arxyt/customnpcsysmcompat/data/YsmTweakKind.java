package com.arxyt.customnpcsysmcompat.data;

/** The three configuration form kinds provided by YSM 2.6.5. */
public enum YsmTweakKind {
    CHECKBOX,
    RANGE,
    RADIO;

    public static YsmTweakKind fromNbt(String value) {
        if (value == null) return null;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
