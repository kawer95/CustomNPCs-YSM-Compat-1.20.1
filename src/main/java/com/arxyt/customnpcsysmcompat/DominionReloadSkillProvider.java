package com.arxyt.customnpcsysmcompat;

import com.arxyt.dominionsword.api.DominionSkillProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

/** Optional Dominion skill that asks TaCZ to reload a selected YSM CustomNPC's real weapon. */
public final class DominionReloadSkillProvider implements DominionSkillProvider {
    private static final String ID = "dominionsword:reload";
    private static final String ICON = "dominionsword:textures/gui/skill/reload.png";

    @Override
    public boolean supports(Entity actor) {
        return actor instanceof EntityNPCInterface npc && GunCompat.active(npc);
    }

    @Override
    public List<SkillView> skills(ServerPlayer commander, Entity actor) {
        return List.of(SkillView.instant(ID, "skill.dominionsword.reload", ICON));
    }

    @Override
    public boolean activate(SkillContext context, String skillId) {
        return ID.equals(skillId) && context.actor() instanceof EntityNPCInterface npc
                && GunCompat.requestReload(npc);
    }
}
