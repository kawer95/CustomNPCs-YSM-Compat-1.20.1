package com.arxyt.customnpcsysmcompat.data;

import java.util.Objects;

/**
 * A persistent selection for one model-provided YSM config form.  Expressions are
 * intentionally not stored here: radio expressions are read from the local model
 * metadata when the proxy is initialized.
 */
public record YsmTweakEntry(String buttonId, int formIndex, YsmTweakKind kind,
                            String variable, boolean booleanValue, double numberValue,
                            String choice, long order) {
    public static final int MAX_BUTTON_ID_LENGTH = 256;
    public static final int MAX_VARIABLE_LENGTH = 512;
    public static final int MAX_CHOICE_LENGTH = 256;

    public YsmTweakEntry {
        buttonId = normalize(buttonId, MAX_BUTTON_ID_LENGTH);
        variable = normalize(variable, MAX_VARIABLE_LENGTH);
        choice = normalize(choice, MAX_CHOICE_LENGTH);
        formIndex = Math.max(0, formIndex);
        order = Math.max(0L, order);
    }

    public static YsmTweakEntry checkbox(String buttonId, int formIndex, String variable,
                                         boolean value, long order) {
        return new YsmTweakEntry(buttonId, formIndex, YsmTweakKind.CHECKBOX,
                variable, value, 0.0D, "", order);
    }

    public static YsmTweakEntry range(String buttonId, int formIndex, String variable,
                                      double value, long order) {
        return new YsmTweakEntry(buttonId, formIndex, YsmTweakKind.RANGE,
                variable, false, value, "", order);
    }

    public static YsmTweakEntry radio(String buttonId, int formIndex, String variable,
                                      String choice, long order) {
        return new YsmTweakEntry(buttonId, formIndex, YsmTweakKind.RADIO,
                variable, false, 0.0D, choice, order);
    }

    public String identity() {
        return buttonId + '#' + formIndex;
    }

    public boolean valid() {
        if (buttonId.isEmpty() || kind == null) return false;
        return switch (kind) {
            case CHECKBOX -> !variable.isEmpty();
            case RANGE -> !variable.isEmpty() && Double.isFinite(numberValue);
            case RADIO -> !choice.isEmpty();
        };
    }

    public YsmTweakEntry withOrder(long newOrder) {
        return new YsmTweakEntry(buttonId, formIndex, kind, variable,
                booleanValue, numberValue, choice, newOrder);
    }

    private static String normalize(String value, int limit) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }
}
