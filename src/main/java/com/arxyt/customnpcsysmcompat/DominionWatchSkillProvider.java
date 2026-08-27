package com.arxyt.customnpcsysmcompat;

import com.arxyt.dominionsword.api.DominionControlApi;
import com.arxyt.dominionsword.api.DominionSkillProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

/** Optional Dominion-owned skill provider for YSM CustomNPCs holding an active TACZ weapon. */
public final class DominionWatchSkillProvider implements DominionSkillProvider {
    private static final String ID = "dominionsword:watch";
    private static final String ICON = "dominionsword:textures/gui/skill/watch.png";

    @Override
    public boolean supports(Entity actor) {
        return actor instanceof EntityNPCInterface npc && GunCompat.active(npc);
    }

    @Override
    public List<SkillView> skills(ServerPlayer commander, Entity actor) {
        return List.of(new SkillView(ID, "skill.dominionsword.watch", ICON, SkillType.DIRECTION,
                true, 0, 0));
    }

    @Override
    public DirectionalAreaSpec directionalArea(ServerPlayer commander, Entity actor, String skillId) {
        return ID.equals(skillId) ? DominionControlApi.watchArea() : null;
    }

    @Override
    public boolean activate(SkillContext context, String skillId) {
        return ID.equals(skillId) && context.actor() instanceof Mob mob
                && context.target() != null && context.target().position() != null
                && DominionControlApi.deployWatch(context.commander(), mob, context.target().position());
    }
}
