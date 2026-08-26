package com.arxyt.customnpcsysmcompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

/**
 * Persists the post-kill target-switch delay for a TaCZ YSM-NPC when Dominion's
 * shared balance option is enabled.
 *
 * <p>The timing and angle calculation deliberately match Dominion Sword's
 * commander-maid logic: a static reaction lasts twenty ticks, while dynamic
 * reaction time ranges from ten to forty ticks according to the turn needed to
 * face the next target.</p>
 */
public final class NpcGunTargetReaction {
    private static final String LAST_GUN_TARGET = "DominionYsmNpcLastGunTarget";
    private static final String REACTION_TARGET = "DominionYsmNpcReactionTarget";
    private static final String REACTION_UNTIL = "DominionYsmNpcReactionUntil";
    private static final String REACTION_STARTED = "DominionYsmNpcReactionStarted";
    private static final String REACTION_VIEW_X = "DominionYsmNpcReactionViewX";
    private static final String REACTION_VIEW_Y = "DominionYsmNpcReactionViewY";
    private static final String REACTION_VIEW_Z = "DominionYsmNpcReactionViewZ";
    private static final String REACTION_CANDIDATE = "DominionYsmNpcReactionCandidate";
    private static final int STATIC_REACTION_TICKS = 20;
    private static final int MIN_DYNAMIC_REACTION_TICKS = 10;
    private static final int MAX_DYNAMIC_REACTION_TICKS = 40;

    private NpcGunTargetReaction() {
    }

    /**
     * Starts or continues the reaction window when a dead target is replaced.
     * A live target replacement is considered an intentional new order and is
     * never delayed.
     */
    public static boolean blocks(EntityNPCInterface npc, LivingEntity candidate,
                                 DominionCombatBalance.Settings settings) {
        if (npc == null || candidate == null || !candidate.isAlive()
                || settings == null || !settings.available() || !settings.targetReactionEnabled()) {
            clear(npc);
            return false;
        }
        CompoundTag data = npc.getPersistentData();
        if (!data.hasUUID(LAST_GUN_TARGET)) return false;
        UUID previousId = data.getUUID(LAST_GUN_TARGET);
        if (previousId.equals(candidate.getUUID())) {
            clearReactionWindow(data);
            return false;
        }

        Entity previous = npc.level() instanceof ServerLevel level ? level.getEntity(previousId) : null;
        if (previous instanceof LivingEntity living && living.isAlive()) {
            clearReactionWindow(data);
            return false;
        }

        long now = npc.level().getGameTime();
        if (!data.hasUUID(REACTION_TARGET) || !previousId.equals(data.getUUID(REACTION_TARGET))
                || !data.contains(REACTION_STARTED)) {
            Vec3 view = npc.getViewVector(1.0F).normalize();
            data.putUUID(REACTION_TARGET, previousId);
            data.putLong(REACTION_STARTED, now);
            data.putDouble(REACTION_VIEW_X, view.x);
            data.putDouble(REACTION_VIEW_Y, view.y);
            data.putDouble(REACTION_VIEW_Z, view.z);
            data.remove(REACTION_CANDIDATE);
            data.putLong(REACTION_UNTIL, now + reactionDuration(settings.dynamicTargetReaction(), 0.0D));
        }

        if (settings.dynamicTargetReaction() && (!data.hasUUID(REACTION_CANDIDATE)
                || !candidate.getUUID().equals(data.getUUID(REACTION_CANDIDATE)))) {
            Vec3 view = new Vec3(data.getDouble(REACTION_VIEW_X), data.getDouble(REACTION_VIEW_Y),
                    data.getDouble(REACTION_VIEW_Z)).normalize();
            Vec3 targetDirection = candidate.getEyePosition().subtract(npc.getEyePosition()).normalize();
            double angle = Math.toDegrees(Math.acos(Mth.clamp(view.dot(targetDirection), -1.0D, 1.0D)));
            data.putUUID(REACTION_CANDIDATE, candidate.getUUID());
            data.putLong(REACTION_UNTIL, data.getLong(REACTION_STARTED)
                    + reactionDuration(true, angle));
        }
        return data.getLong(REACTION_UNTIL) > now;
    }

    /** Records the live target after its reaction window is complete. */
    public static void noteTarget(EntityNPCInterface npc, LivingEntity target,
                                  DominionCombatBalance.Settings settings) {
        if (npc == null || target == null || !target.isAlive()
                || settings == null || !settings.available() || !settings.targetReactionEnabled()) {
            clear(npc);
            return;
        }
        npc.getPersistentData().putUUID(LAST_GUN_TARGET, target.getUUID());
    }

    /** Clears all compatibility-only target reaction state. */
    public static void clear(EntityNPCInterface npc) {
        if (npc == null) return;
        CompoundTag data = npc.getPersistentData();
        data.remove(LAST_GUN_TARGET);
        clearReactionWindow(data);
    }

    static int reactionDuration(boolean dynamic, double angleDegrees) {
        if (!dynamic) return STATIC_REACTION_TICKS;
        double safeAngle = Double.isFinite(angleDegrees) ? Mth.clamp(angleDegrees, 0.0D, 180.0D) : 0.0D;
        return MIN_DYNAMIC_REACTION_TICKS + (int) Math.round(safeAngle / 180.0D
                * (MAX_DYNAMIC_REACTION_TICKS - MIN_DYNAMIC_REACTION_TICKS));
    }

    private static void clearReactionWindow(CompoundTag data) {
        data.remove(REACTION_TARGET);
        data.remove(REACTION_UNTIL);
        data.remove(REACTION_STARTED);
        data.remove(REACTION_VIEW_X);
        data.remove(REACTION_VIEW_Y);
        data.remove(REACTION_VIEW_Z);
        data.remove(REACTION_CANDIDATE);
    }
}
