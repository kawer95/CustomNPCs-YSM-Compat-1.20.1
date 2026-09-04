package com.arxyt.customnpcsysmcompat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

/** Applies a stepped server aim sample without erasing the previous frame used for interpolation. */
public final class ClientNpcAimSync {
    private ClientNpcAimSync() {
    }

    public static void apply(int entityId, float yaw, float bodyYaw, float headYaw, float pitch) {
        if (Minecraft.getInstance().level == null) return;
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (!(entity instanceof EntityNPCInterface npc)) return;
        npc.setYRot(yaw);
        npc.yBodyRot = bodyYaw;
        npc.setYHeadRot(headYaw);
        npc.setXRot(pitch);
    }
}
