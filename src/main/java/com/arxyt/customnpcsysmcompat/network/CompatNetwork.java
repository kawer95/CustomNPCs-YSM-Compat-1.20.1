package com.arxyt.customnpcsysmcompat.network;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

public final class CompatNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CustomNpcsYsmCompat.MOD_ID, "main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), PROTOCOL::equals);

    private CompatNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, MaidTweakMessage.class, MaidTweakMessage::encode,
                MaidTweakMessage::decode, MaidTweakMessage::handle);
        CHANNEL.registerMessage(1, NpcActionStateMessage.class, NpcActionStateMessage::encode,
                NpcActionStateMessage::decode, NpcActionStateMessage::handle);
    }

    public static void sendMaidTweak(MaidTweakMessage message) {
        CHANNEL.sendToServer(message);
    }

    public static boolean isRemotePresent(Connection connection) {
        return connection != null && CHANNEL.isRemotePresent(connection);
    }

    public static void sendActionState(Entity entity, NpcActionStateMessage message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }

    public static void sendActionState(ServerPlayer player, NpcActionStateMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /**
     * The maid editor packet is an optional extension.  In particular, old
     * ReplayMod recordings have no entry for this channel in their recorded
     * Forge handshake.  Rejecting Forge's ABSENT marker makes ReplayMod repeat
     * login forever before it can create the replay world.
     */
}
