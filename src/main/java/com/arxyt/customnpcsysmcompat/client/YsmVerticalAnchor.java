package com.arxyt.customnpcsysmcompat.client;

/**
 * Keeps a YSM model pack's animated root attached to its pre-hit ground plane.
 * This does not alter the selected action or any bone animation; it removes only
 * a whole-model downward translation left by some {@code attacked} actions.
 */
final class YsmVerticalAnchor {
    private static final double MIN_TOLERANCE = 0.025D;
    private static final double BASELINE_SMOOTHING = 0.05D;

    private boolean calibrated;
    private boolean anchoring;
    private double baselineFloor;
    private double baselineHeight;
    private double correction;

    boolean needsSample(boolean hurt) {
        return !calibrated || hurt || anchoring;
    }

    double correction() {
        return correction;
    }

    boolean anchoring() {
        return anchoring;
    }

    void reset() {
        calibrated = false;
        anchoring = false;
        baselineFloor = 0.0D;
        baselineHeight = 0.0D;
        correction = 0.0D;
    }

    Update observe(double rawFloor, double rawCeiling, boolean hurt) {
        if (!Double.isFinite(rawFloor) || !Double.isFinite(rawCeiling) || rawCeiling <= rawFloor) {
            return Update.INVALID;
        }

        double height = rawCeiling - rawFloor;
        if (!calibrated) {
            baselineFloor = rawFloor;
            baselineHeight = height;
            calibrated = true;
            anchoring = hurt;
            correction = 0.0D;
            return Update.CALIBRATED;
        }

        double tolerance = Math.max(MIN_TOLERANCE, baselineHeight * 0.015D);
        if (hurt) anchoring = true;

        if (anchoring) {
            double previous = correction;
            double required = baselineFloor - rawFloor;
            // A raised foot or jumping hurt action is legitimate. Only geometry that has
            // crossed below the established ground plane is corrected.
            correction = required > tolerance
                    ? Math.min(required, Math.max(2.5D, baselineHeight * 1.5D))
                    : 0.0D;

            // Keep sampling after hurtTime reaches zero. The anchor is released only after
            // YSM itself has restored the original root, so rapid consecutive hits cannot
            // strand the model underground between actions.
            if (!hurt && correction == 0.0D) {
                anchoring = false;
                updateBaseline(rawFloor, height);
                return Update.RELEASED;
            }
            return Math.abs(previous - correction) > tolerance ? Update.CORRECTION_CHANGED : Update.STABLE;
        }

        correction = 0.0D;
        updateBaseline(rawFloor, height);
        return Update.STABLE;
    }

    private void updateBaseline(double floor, double height) {
        baselineFloor += (floor - baselineFloor) * BASELINE_SMOOTHING;
        baselineHeight += (height - baselineHeight) * BASELINE_SMOOTHING;
    }

    enum Update {
        INVALID,
        CALIBRATED,
        CORRECTION_CHANGED,
        RELEASED,
        STABLE
    }
}
