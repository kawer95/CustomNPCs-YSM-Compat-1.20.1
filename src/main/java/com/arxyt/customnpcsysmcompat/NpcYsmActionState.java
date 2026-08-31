package com.arxyt.customnpcsysmcompat;

import com.arxyt.customnpcsysmcompat.network.CompatNetwork;
import com.arxyt.customnpcsysmcompat.network.NpcActionStateMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

public final class NpcYsmActionState {
    private static final String SET = "CustomNpcsYsmCompatActionSet";
    private static final String ACTION = "CustomNpcsYsmCompatAction";
    private static final String REVISION = "CustomNpcsYsmCompatActionRevision";
    private NpcYsmActionState() {}

    public static String action(Entity entity) { return entity == null ? "" : entity.getPersistentData().getString(ACTION); }
    public static String set(Entity entity) { return entity == null ? "" : entity.getPersistentData().getString(SET); }
    public static long revision(Entity entity) { return entity == null ? 0L : entity.getPersistentData().getLong(REVISION); }
    public static void play(EntityNPCInterface npc, String set, String action) { update(npc, set, action); }
    public static void stop(EntityNPCInterface npc) { update(npc, "", ""); }
    public static void validate(EntityNPCInterface npc, String currentSet) {
        if (!action(npc).isBlank() && !set(npc).equals(currentSet)) stop(npc);
    }
    public static void sendTo(ServerPlayer player, EntityNPCInterface npc) { CompatNetwork.sendActionState(player, message(npc)); }
    private static void update(EntityNPCInterface npc, String set, String action) {
        if (npc == null || npc.level().isClientSide) return;
        String safeSet = safe(set, 1024), safeAction = safe(action, 256);
        if (safeAction.equals(action(npc)) && safeSet.equals(set(npc))) return;
        if (safeAction.isBlank()) { npc.getPersistentData().remove(ACTION); npc.getPersistentData().remove(SET); }
        else { npc.getPersistentData().putString(ACTION, safeAction); npc.getPersistentData().putString(SET, safeSet); }
        npc.getPersistentData().putLong(REVISION, revision(npc) + 1L);
        CompatNetwork.sendActionState(npc, message(npc));
    }
    private static NpcActionStateMessage message(EntityNPCInterface npc) {
        return new NpcActionStateMessage(npc.getId(), npc.getUUID(), revision(npc), set(npc), action(npc));
    }
    private static String safe(String value, int max) { String text = value == null ? "" : value.trim(); return text.length() <= max ? text : text.substring(0, max); }
}
