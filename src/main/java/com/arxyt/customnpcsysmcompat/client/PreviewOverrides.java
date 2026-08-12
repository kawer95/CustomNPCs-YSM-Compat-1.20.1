package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class PreviewOverrides {
    private static final Map<EntityNPCInterface, YsmDisplayData> OVERRIDES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PreviewOverrides() {
    }

    public static void set(EntityNPCInterface npc, YsmDisplayData data) {
        OVERRIDES.put(npc, data);
    }

    public static YsmDisplayData get(EntityNPCInterface npc) {
        return OVERRIDES.get(npc);
    }

    public static void clear(EntityNPCInterface npc) {
        OVERRIDES.remove(npc);
    }

    public static void clearAll() {
        OVERRIDES.clear();
    }
}
