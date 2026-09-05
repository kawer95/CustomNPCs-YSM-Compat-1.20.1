package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.api.IYsmMaidTweakData;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import com.arxyt.customnpcsysmcompat.network.CompatNetwork;
import com.arxyt.customnpcsysmcompat.network.MaidTweakMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class YsmMaidTweakClient {
    private YsmMaidTweakClient() {
    }

    /** Captures only expressions emitted while YSM's dedicated maid screen is open. */
    public static boolean captureScreenExpression(String expression) {
        Screen screen = Minecraft.getInstance().screen;
        EntityMaid maid;
        if (screen instanceof YsmMaidScreenAccess accessor) {
            maid = accessor.customnpcsYsmCompat$getMaid();
        } else {
            return false;
        }
        if (maid == null || !maid.isYsmModel()) return true;
        String modelId = YsmDisplayData.normalizeModelId(maid.getYsmModelId());
        Ysm265Adapter.captureTweak(modelId, expression).ifPresent(captured -> {
            ((IYsmMaidTweakData) maid).customnpcsYsmCompat$putMaidTweak(modelId, captured.entry());
            var listener = Minecraft.getInstance().getConnection();
            if (listener != null && CompatNetwork.isRemotePresent(listener.getConnection())) {
                CompatNetwork.sendMaidTweak(new MaidTweakMessage(maid.getId(), modelId, captured.entry()));
            }
        });
        return true;
    }

    public static YsmTweakProfile profile(EntityMaid maid, String modelId) {
        if (maid == null) return YsmTweakProfile.EMPTY;
        return ((IYsmMaidTweakData) maid).customnpcsYsmCompat$getMaidTweaks(
                YsmDisplayData.normalizeModelId(modelId));
    }
}
