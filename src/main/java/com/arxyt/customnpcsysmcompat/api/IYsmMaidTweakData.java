package com.arxyt.customnpcsysmcompat.api;

import com.arxyt.customnpcsysmcompat.data.YsmTweakEntry;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;

import java.util.Map;

/** Persistent, synchronized formal YSM config-form choices owned by one maid. */
public interface IYsmMaidTweakData {
    Map<String, YsmTweakProfile> customnpcsYsmCompat$getMaidTweakProfiles();

    void customnpcsYsmCompat$setMaidTweakProfiles(Map<String, YsmTweakProfile> profiles);

    default YsmTweakProfile customnpcsYsmCompat$getMaidTweaks(String modelId) {
        return customnpcsYsmCompat$getMaidTweakProfiles().getOrDefault(modelId, YsmTweakProfile.EMPTY);
    }

    default void customnpcsYsmCompat$putMaidTweak(String modelId, YsmTweakEntry entry) {
        Map<String, YsmTweakProfile> current = customnpcsYsmCompat$getMaidTweakProfiles();
        java.util.Map<String, YsmTweakProfile> updated = new java.util.LinkedHashMap<>(current);
        updated.put(modelId, current.getOrDefault(modelId, YsmTweakProfile.EMPTY).with(entry));
        customnpcsYsmCompat$setMaidTweakProfiles(updated);
    }
}
