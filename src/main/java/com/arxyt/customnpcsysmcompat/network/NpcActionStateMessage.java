package com.arxyt.customnpcsysmcompat.network;

import com.arxyt.customnpcsysmcompat.client.NpcActionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record NpcActionStateMessage(int entityId, UUID entityUuid, long revision, String actionSetId, String actionId) {
    public static void encode(NpcActionStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId); buffer.writeUUID(message.entityUuid); buffer.writeVarLong(Math.max(0L, message.revision));
        buffer.writeUtf(message.actionSetId == null ? "" : message.actionSetId, 1024);
        buffer.writeUtf(message.actionId == null ? "" : message.actionId, 256);
    }
    public static NpcActionStateMessage decode(FriendlyByteBuf buffer) {
        return new NpcActionStateMessage(buffer.readVarInt(), buffer.readUUID(), buffer.readVarLong(), buffer.readUtf(1024), buffer.readUtf(256));
    }
    public static void handle(NpcActionStateMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NpcActionClientState.accept(message)));
        context.setPacketHandled(true);
    }
}
