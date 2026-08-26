package com.arxyt.customnpcsysmcompat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YsmNbtCodec {
    public static final String ROOT_TAG = "CustomNPCsYsmCompat";
    private static final String TWEAK_PROFILES_TAG = "TweakProfiles";
    private static final String TWEAK_ENTRIES_TAG = "Entries";

    private YsmNbtCodec() {
    }

    public static void write(CompoundTag root, YsmDisplayData value) {
        CompoundTag data = new CompoundTag();
        data.putBoolean("Enabled", value.enabled());
        data.putString("ModelId", value.enabled() ? value.modelId() : "");
        ListTag profiles = new ListTag();
        for (Map.Entry<String, YsmTweakProfile> profile : value.tweakProfiles().entrySet()) {
            CompoundTag profileTag = new CompoundTag();
            profileTag.putString("ModelId", profile.getKey());
            ListTag entries = new ListTag();
            for (YsmTweakEntry entry : profile.getValue().entries()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("ButtonId", entry.buttonId());
                entryTag.putInt("FormIndex", entry.formIndex());
                entryTag.putString("Kind", entry.kind().name());
                entryTag.putString("Variable", entry.variable());
                entryTag.putBoolean("BooleanValue", entry.booleanValue());
                entryTag.putDouble("NumberValue", entry.numberValue());
                entryTag.putString("Choice", entry.choice());
                entryTag.putLong("Order", entry.order());
                entries.add(entryTag);
            }
            profileTag.put(TWEAK_ENTRIES_TAG, entries);
            profiles.add(profileTag);
        }
        data.put(TWEAK_PROFILES_TAG, profiles);
        root.put(ROOT_TAG, data);
    }

    public static YsmDisplayData read(CompoundTag root) {
        if (!root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return YsmDisplayData.DISABLED;
        }
        CompoundTag data = root.getCompound(ROOT_TAG);
        Map<String, YsmTweakProfile> profiles = new LinkedHashMap<>();
        if (data.contains(TWEAK_PROFILES_TAG, Tag.TAG_LIST)) {
            ListTag profileTags = data.getList(TWEAK_PROFILES_TAG, Tag.TAG_COMPOUND);
            for (int profileIndex = 0; profileIndex < profileTags.size()
                    && profiles.size() < YsmDisplayData.MAX_TWEAK_PROFILES; profileIndex++) {
                CompoundTag profileTag = profileTags.getCompound(profileIndex);
                String modelId = YsmDisplayData.normalizeModelId(profileTag.getString("ModelId"));
                if (modelId.isEmpty()) continue;
                List<YsmTweakEntry> entries = new ArrayList<>();
                if (profileTag.contains(TWEAK_ENTRIES_TAG, Tag.TAG_LIST)) {
                    ListTag entryTags = profileTag.getList(TWEAK_ENTRIES_TAG, Tag.TAG_COMPOUND);
                    for (int entryIndex = 0; entryIndex < entryTags.size()
                            && entries.size() < YsmTweakProfile.MAX_ENTRIES; entryIndex++) {
                        CompoundTag entryTag = entryTags.getCompound(entryIndex);
                        YsmTweakKind kind = YsmTweakKind.fromNbt(entryTag.getString("Kind"));
                        if (kind == null) continue;
                        YsmTweakEntry entry = new YsmTweakEntry(entryTag.getString("ButtonId"),
                                entryTag.getInt("FormIndex"), kind, entryTag.getString("Variable"),
                                entryTag.getBoolean("BooleanValue"), entryTag.getDouble("NumberValue"),
                                entryTag.getString("Choice"), entryTag.getLong("Order"));
                        if (entry.valid()) entries.add(entry);
                    }
                }
                YsmTweakProfile profile = new YsmTweakProfile(entries);
                if (!profile.isEmpty()) profiles.put(modelId, profile);
            }
        }
        return new YsmDisplayData(data.getBoolean("Enabled"), data.getString("ModelId"), profiles);
    }
}
