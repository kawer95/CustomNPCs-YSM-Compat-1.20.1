package com.arxyt.customnpcsysmcompat.network;

import com.arxyt.customnpcsysmcompat.client.ClientNpcAimSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One server-tick sample of the real intermediate CNPC gun orientation. */
public record NpcAimStateMessage(int entityId, float yaw, float bodyYaw, float headYaw, float pitch) {
    static void encode(NpcAimStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeFloat(message.yaw);
        buffer.writeFloat(message.bodyYaw);
        buffer.writeFloat(message.headYaw);
        buffer.writeFloat(message.pitch);
    }

    static NpcAimStateMessage decode(FriendlyByteBuf buffer) {
        return new NpcAimStateMessage(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat());
    }

    static void handle(NpcAimStateMessage message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientNpcAimSync.apply(message.entityId, message.yaw, message.bodyYaw,
                        message.headYaw, message.pitch)));
        context.get().setPacketHandled(true);
    }
}
