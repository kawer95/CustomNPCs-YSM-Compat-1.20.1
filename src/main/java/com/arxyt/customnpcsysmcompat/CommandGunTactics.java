package com.arxyt.customnpcsysmcompat;

/** Deterministic commander gun movement shared by CNPC and its tests. */
public final class CommandGunTactics {
    public static final double RETREAT_DISTANCE = 10.0D;

    private CommandGunTactics() {
    }

    public static Maneuver decide(boolean canSee, double distance, double configuredRange,
                                  boolean closeQuarters) {
        double range = Math.max(1.0D, configuredRange);
        if (!canSee || distance > range) return Maneuver.PURSUE;
        if (!closeQuarters && distance < RETREAT_DISTANCE) return Maneuver.RETREAT;
        return Maneuver.HOLD;
    }

    public static Maneuver decideControlled(boolean commandedAttack, boolean canSee,
                                              double distance, double configuredRange,
                                              boolean closeQuarters) {
        return commandedAttack ? decide(canSee, distance, configuredRange, closeQuarters)
                : Maneuver.SENTRY;
    }

    /** A prone command attack is stationary even when its target is beyond the NPC's configured range. */
    public static Maneuver decideControlled(boolean commandedAttack, boolean canSee,
                                              double distance, double configuredRange,
                                              boolean closeQuarters, boolean prone) {
        return commandedAttack && prone ? Maneuver.HOLD
                : decideControlled(commandedAttack, canSee, distance, configuredRange, closeQuarters);
    }

    /** Prone bypasses the CustomNPC configured distance, never the line-of-sight requirement. */
    public static boolean canFire(boolean prone, boolean canSee, double distance, double configuredRange) {
        return canSee && (prone || distance <= Math.max(1.0D, configuredRange));
    }

    /**
     * A Dominion watch scan validates several sample rays against the target collision box.
     * While watching, that authoritative result must be used by every CNPC firing goal;
     * vanilla's single eye-to-eye sight cache can reject an otherwise valid gun shot.
     */
    public static boolean effectiveLineOfSight(boolean watching, boolean vanillaCanSee,
                                                boolean watchHasClearShot) {
        return watching ? watchHasClearShot : vanillaCanSee;
    }

    public enum Maneuver {
        PURSUE,
        RETREAT,
        HOLD,
        SENTRY
    }
}
