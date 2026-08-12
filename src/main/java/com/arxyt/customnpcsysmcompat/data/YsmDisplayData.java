package com.arxyt.customnpcsysmcompat.data;

import java.util.Locale;

public record YsmDisplayData(boolean enabled, String modelId) {
    public static final int MAX_MODEL_ID_LENGTH = 1024;
    public static final YsmDisplayData DISABLED = new YsmDisplayData(false, "");

    public YsmDisplayData {
        modelId = normalizeModelId(modelId);
        enabled = enabled && !modelId.isEmpty();
    }

    public static String normalizeModelId(String modelId) {
        if (modelId == null) {
            return "";
        }
        String value = modelId.trim();
        return value.length() <= MAX_MODEL_ID_LENGTH ? value : value.substring(0, MAX_MODEL_ID_LENGTH);
    }

    public static boolean matches(String query, String id, String displayName) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty()
                || safeLower(id).contains(needle)
                || safeLower(displayName).contains(needle);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
