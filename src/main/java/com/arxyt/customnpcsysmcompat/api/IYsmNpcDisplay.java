package com.arxyt.customnpcsysmcompat.api;

import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;

import java.util.Map;

public interface IYsmNpcDisplay {
    boolean customnpcsYsmCompat$isEnabled();

    void customnpcsYsmCompat$setEnabled(boolean enabled);

    String customnpcsYsmCompat$getModelId();

    void customnpcsYsmCompat$setModelId(String modelId);

    Map<String, YsmTweakProfile> customnpcsYsmCompat$getTweakProfiles();

    void customnpcsYsmCompat$setTweakProfiles(Map<String, YsmTweakProfile> profiles);
}
