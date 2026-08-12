package com.arxyt.customnpcsysmcompat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class YsmNbtCodec {
    public static final String ROOT_TAG = "CustomNPCsYsmCompat";

    private YsmNbtCodec() {
    }

    public static void write(CompoundTag root, YsmDisplayData value) {
        CompoundTag data = new CompoundTag();
        data.putBoolean("Enabled", value.enabled());
        data.putString("ModelId", value.enabled() ? value.modelId() : "");
        root.put(ROOT_TAG, data);
    }

    public static YsmDisplayData read(CompoundTag root) {
        if (!root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return YsmDisplayData.DISABLED;
        }
        CompoundTag data = root.getCompound(ROOT_TAG);
        return new YsmDisplayData(data.getBoolean("Enabled"), data.getString("ModelId"));
    }
}
