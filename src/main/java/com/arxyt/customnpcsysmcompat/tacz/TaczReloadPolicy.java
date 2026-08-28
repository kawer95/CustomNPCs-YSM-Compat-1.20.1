package com.arxyt.customnpcsysmcompat.tacz;

/**
 * Pure timing policy for the optional CNPC TaCZ adapter's idle reloads.
 *
 * <p>It intentionally contains no CustomNPCs, TaCZ, or Dominion references. The adapter owns
 * enemy detection and the real reload request; this class only guarantees the three thresholds
 * stay ordered and is therefore directly unit-testable.</p>
 */
public final class TaczReloadPolicy {
    public static final int TICKS_PER_SECOND = 20;

    private TaczReloadPolicy() {
    }

    public static Schedule schedule(int belowHalfSeconds, int belowTwoThirdsSeconds, int nonFullSeconds) {
        int half = Math.max(1, belowHalfSeconds);
        int twoThirds = Math.max(half + 1, belowTwoThirdsSeconds);
        int nonFull = Math.max(twoThirds + 1, nonFullSeconds);
        return new Schedule(half, twoThirds, nonFull);
    }

    public static boolean shouldAutoReload(int currentAmmo, int capacity, long quietTicks, Schedule schedule) {
        if (capacity <= 0 || currentAmmo < 0 || quietTicks < 0 || schedule == null) return false;
        if (quietTicks >= (long) schedule.nonFullSeconds() * TICKS_PER_SECOND) return currentAmmo < capacity;
        if (quietTicks >= (long) schedule.belowTwoThirdsSeconds() * TICKS_PER_SECOND) {
            return (long) currentAmmo * 3L < (long) capacity * 2L;
        }
        return quietTicks >= (long) schedule.belowHalfSeconds() * TICKS_PER_SECOND
                && (long) currentAmmo * 2L < capacity;
    }

    public record Schedule(int belowHalfSeconds, int belowTwoThirdsSeconds, int nonFullSeconds) {
        public Schedule {
            if (belowHalfSeconds < 1 || belowTwoThirdsSeconds <= belowHalfSeconds
                    || nonFullSeconds <= belowTwoThirdsSeconds) {
                throw new IllegalArgumentException("TaCZ auto-reload schedule must be strictly ascending");
            }
        }
    }
}
