package com.arxyt.customnpcsysmcompat.data;

import com.arxyt.customnpcsysmcompat.api.IYsmNpcDisplay;
import noppes.npcs.entity.data.DataDisplay;

public final class YsmDisplayAccess {
    private YsmDisplayAccess() {
    }

    public static YsmDisplayData get(DataDisplay display) {
        IYsmNpcDisplay extension = (IYsmNpcDisplay) display;
        return new YsmDisplayData(extension.customnpcsYsmCompat$isEnabled(), extension.customnpcsYsmCompat$getModelId());
    }

    public static void set(DataDisplay display, YsmDisplayData data) {
        IYsmNpcDisplay extension = (IYsmNpcDisplay) display;
        extension.customnpcsYsmCompat$setModelId(data.modelId());
        extension.customnpcsYsmCompat$setEnabled(data.enabled());
    }
}
