package com.arxyt.customnpcsysmcompat.network;

import com.arxyt.customnpcsysmcompat.api.IYsmMaidTweakData;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmTweakEntry;
import com.arxyt.customnpcsysmcompat.data.YsmTweakKind;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record MaidTweakMessage(int entityId, String modelId, YsmTweakEntry entry) {
    public static void encode(MaidTweakMessage message, FriendlyByteBuf buffer) {
        YsmTweakEntry entry = message.entry;
        buffer.writeVarInt(message.entityId);
        buffer.writeUtf(message.modelId, YsmDisplayData.MAX_MODEL_ID_LENGTH);
        buffer.writeUtf(entry.buttonId(), YsmTweakEntry.MAX_BUTTON_ID_LENGTH);
        buffer.writeVarInt(entry.formIndex());
        buffer.writeEnum(entry.kind());
        buffer.writeUtf(entry.variable(), YsmTweakEntry.MAX_VARIABLE_LENGTH);
        buffer.writeBoolean(entry.booleanValue());
        buffer.writeDouble(entry.numberValue());
        buffer.writeUtf(entry.choice(), YsmTweakEntry.MAX_CHOICE_LENGTH);
        buffer.writeVarLong(entry.order());
    }

    public static MaidTweakMessage decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        String modelId = buffer.readUtf(YsmDisplayData.MAX_MODEL_ID_LENGTH);
        String buttonId = buffer.readUtf(YsmTweakEntry.MAX_BUTTON_ID_LENGTH);
        int formIndex = buffer.readVarInt();
        YsmTweakKind kind = buffer.readEnum(YsmTweakKind.class);
        String variable = buffer.readUtf(YsmTweakEntry.MAX_VARIABLE_LENGTH);
        boolean booleanValue = buffer.readBoolean();
        double numberValue = buffer.readDouble();
        String choice = buffer.readUtf(YsmTweakEntry.MAX_CHOICE_LENGTH);
        long order = buffer.readVarLong();
        return new MaidTweakMessage(entityId, modelId,
                new YsmTweakEntry(buttonId, formIndex, kind, variable,
                        booleanValue, numberValue, choice, order));
    }

    public static void handle(MaidTweakMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> apply(sender, message));
        context.setPacketHandled(true);
    }

    private static void apply(ServerPlayer sender, MaidTweakMessage message) {
        Entity found = sender.level().getEntity(message.entityId);
        if (!(found instanceof EntityMaid maid) || !maid.isAlive() || !maid.isYsmModel()) return;
        UUID owner = maid.getOwnerUUID();
        if (owner == null || !owner.equals(sender.getUUID()) || sender.distanceToSqr(maid) > 64.0D) return;
        String modelId = YsmDisplayData.normalizeModelId(message.modelId);
        if (modelId.isEmpty() || !modelId.equals(YsmDisplayData.normalizeModelId(maid.getYsmModelId()))) return;
        if (message.entry == null || !message.entry.valid()) return;
        ((IYsmMaidTweakData) maid).customnpcsYsmCompat$putMaidTweak(modelId, message.entry);
    }
}
