package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.network.NpcActionStateMessage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = CustomNpcsYsmCompat.MOD_ID, value = Dist.CLIENT)
public final class NpcActionClientState {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private NpcActionClientState() {}
    public static void accept(NpcActionStateMessage message) {
        STATES.compute(message.entityUuid(), (id, previous) -> previous == null || message.revision() >= previous.revision()
                ? new State(message.entityId(), message.revision(), message.actionSetId(), message.actionId()) : previous);
    }
    public static State state(EntityNPCInterface npc) {
        State state = npc == null ? null : STATES.get(npc.getUUID());
        return state == null || state.entityId != npc.getId() ? State.EMPTY : state;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { STATES.clear(); }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) { if (event.getLevel().isClientSide()) STATES.remove(event.getEntity().getUUID()); }
    public record State(int entityId, long revision, String actionSetId, String actionId) {
        public static final State EMPTY = new State(-1, 0L, "", "");
    }
}
