package com.arxyt.customnpcsysmcompat;

import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.dominionsword.api.DominionUnitActionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

public final class DominionYsmNpcActionProvider implements DominionUnitActionProvider {
    @Override public boolean supports(Entity entity) {
        return entity instanceof EntityNPCInterface npc && YsmDisplayAccess.get(npc.display).enabled();
    }
    @Override public String actionSetId(Entity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) return "";
        return YsmDisplayData.normalizeModelId(YsmDisplayAccess.get(npc.display).modelId());
    }
    @Override public String currentAction(Entity entity) { return NpcYsmActionState.action(entity); }
    @Override public boolean play(ServerPlayer player, Entity entity, String actionId) {
        if (!(entity instanceof EntityNPCInterface npc)) return false;
        NpcYsmActionState.play(npc, actionSetId(npc), actionId); return true;
    }
    @Override public void stop(ServerPlayer player, Entity entity) {
        if (entity instanceof EntityNPCInterface npc) NpcYsmActionState.stop(npc);
    }
}
