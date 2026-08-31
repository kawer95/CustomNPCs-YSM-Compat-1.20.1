package com.arxyt.customnpcsysmcompat.network;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CompatNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CustomNpcsYsmCompat.MOD_ID, "main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), PROTOCOL::equals);

    private CompatNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, MaidTweakMessage.class, MaidTweakMessage::encode,
                MaidTweakMessage::decode, MaidTweakMessage::handle);
    }

    public static void sendMaidTweak(MaidTweakMessage message) {
        CHANNEL.sendToServer(message);
    }

    public static boolean isRemotePresent(Connection connection) {
        return connection != null && CHANNEL.isRemotePresent(connection);
    }

    /**
     * The maid editor packet is an optional extension.  In particular, old
     * ReplayMod recordings have no entry for this channel in their recorded
     * Forge handshake.  Rejecting Forge's ABSENT marker makes ReplayMod repeat
     * login forever before it can create the replay world.
     */
}
