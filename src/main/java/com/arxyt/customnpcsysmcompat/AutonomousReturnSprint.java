package com.arxyt.customnpcsysmcompat;

import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Transient server-authoritative run-animation state for autonomous combat return.
 *
 * <p>CustomNPCs recalculates the vanilla sprint bit after goals tick. The entity-tail mixin
 * reapplies this marker only after CNPC's own update, allowing YSM's player proxy to receive the
 * real synchronized sprint flag. Weak runtime state avoids saving a stale sprint across unloads.</p>
 */
public final class AutonomousReturnSprint {
    private static final Set<EntityNPCInterface> ACTIVE =
            Collections.newSetFromMap(new WeakHashMap<>());

    private AutonomousReturnSprint() { }

    public static void activate(EntityNPCInterface npc) {
        if (npc == null || npc.level().isClientSide) return;
        ACTIVE.add(npc);
        npc.setSprinting(true);
    }

    public static void deactivate(EntityNPCInterface npc) {
        if (npc == null || npc.level().isClientSide) return;
        if (ACTIVE.remove(npc)) npc.setSprinting(false);
    }

    public static void maintain(EntityNPCInterface npc) {
        if (npc == null || npc.level().isClientSide || !ACTIVE.contains(npc)) return;
        if (!npc.isAlive() || npc.isPassenger()) {
            deactivate(npc);
            return;
        }
        npc.setSprinting(true);
    }
}
