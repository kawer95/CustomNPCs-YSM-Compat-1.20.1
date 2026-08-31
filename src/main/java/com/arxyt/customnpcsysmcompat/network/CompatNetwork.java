package com.arxyt.customnpcsysmcompat.network;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CompatNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CustomNpcsYsmCompat.MOD_ID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    private CompatNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, MaidTweakMessage.class, MaidTweakMessage::encode,
                MaidTweakMessage::decode, MaidTweakMessage::handle);
    }

    public static void sendMaidTweak(MaidTweakMessage message) {
        CHANNEL.sendToServer(message);
    }
}
