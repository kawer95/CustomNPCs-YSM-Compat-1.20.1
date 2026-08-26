package com.arxyt.customnpcsysmcompat.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record YsmDisplayData(boolean enabled, String modelId, Map<String, YsmTweakProfile> tweakProfiles) {
    public static final int MAX_MODEL_ID_LENGTH = 1024;
    public static final int MAX_TWEAK_PROFILES = 64;
    public static final YsmDisplayData DISABLED = new YsmDisplayData(false, "", Map.of());

    public YsmDisplayData(boolean enabled, String modelId) {
        this(enabled, modelId, Map.of());
    }

    public YsmDisplayData {
        modelId = normalizeModelId(modelId);
        enabled = enabled && !modelId.isEmpty();
        Map<String, YsmTweakProfile> normalized = new LinkedHashMap<>();
        if (tweakProfiles != null) {
            for (Map.Entry<String, YsmTweakProfile> entry : tweakProfiles.entrySet()) {
                String profileModelId = normalizeModelId(entry.getKey());
                YsmTweakProfile profile = entry.getValue();
                if (profileModelId.isEmpty() || profile == null || profile.isEmpty()) continue;
                normalized.put(profileModelId, profile);
                if (normalized.size() >= MAX_TWEAK_PROFILES) break;
            }
        }
        tweakProfiles = Map.copyOf(normalized);
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

    public YsmTweakProfile tweaksFor(String id) {
        return tweakProfiles.getOrDefault(normalizeModelId(id), YsmTweakProfile.EMPTY);
    }

    public YsmDisplayData withTweak(String id, YsmTweakEntry entry) {
        String profileId = normalizeModelId(id);
        if (profileId.isEmpty() || entry == null || !entry.valid()) return this;
        YsmTweakProfile updated = tweaksFor(profileId).with(entry);
        if (updated.equals(tweaksFor(profileId))) return this;
        Map<String, YsmTweakProfile> profiles = new LinkedHashMap<>(tweakProfiles);
        profiles.put(profileId, updated);
        return new YsmDisplayData(enabled, modelId, profiles);
    }

    public YsmDisplayData withoutTweak(String id, String buttonId, int formIndex) {
        String profileId = normalizeModelId(id);
        YsmTweakProfile current = tweaksFor(profileId);
        YsmTweakProfile updated = current.without(buttonId, formIndex);
        if (current == updated) return this;
        Map<String, YsmTweakProfile> profiles = new LinkedHashMap<>(tweakProfiles);
        if (updated.isEmpty()) profiles.remove(profileId);
        else profiles.put(profileId, updated);
        return new YsmDisplayData(enabled, modelId, profiles);
    }

    public YsmDisplayData resetTweaks(String id) {
        String profileId = normalizeModelId(id);
        if (!tweakProfiles.containsKey(profileId)) return this;
        Map<String, YsmTweakProfile> profiles = new LinkedHashMap<>(tweakProfiles);
        profiles.remove(profileId);
        return new YsmDisplayData(enabled, modelId, profiles);
    }

    public YsmDisplayData clearAllTweaks() {
        return tweakProfiles.isEmpty() ? this : new YsmDisplayData(enabled, modelId, Map.of());
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
