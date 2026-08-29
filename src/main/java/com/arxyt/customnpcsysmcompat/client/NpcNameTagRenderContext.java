package com.arxyt.customnpcsysmcompat.client;

/**
 * Render-thread scope for the CustomNPC label pass issued after YSM has replaced an NPC model.
 *
 * <p>CustomNPCs renders an additional chat-bubble layer with {@code DepthTest.ALWAYS} when the
 * viewer is nearby. That is useful for its stock renderer, but it also writes a deliberately
 * through-wall overlay into the YSM replacement pass. The matching mixin consumes this scope and
 * drops only that overlay; the ordinary depth-tested label and bubble pass remains intact.</p>
 */
public final class NpcNameTagRenderContext {
    private static final ThreadLocal<Integer> DEPTH_SAFE_SCOPE = ThreadLocal.withInitial(() -> 0);

    private NpcNameTagRenderContext() {
    }

    public static void begin() {
        DEPTH_SAFE_SCOPE.set(DEPTH_SAFE_SCOPE.get() + 1);
    }

    public static void end() {
        int depth = DEPTH_SAFE_SCOPE.get() - 1;
        if (depth <= 0) {
            DEPTH_SAFE_SCOPE.remove();
        } else {
            DEPTH_SAFE_SCOPE.set(depth);
        }
    }

    public static boolean suppressXrayBubblePass() {
        return DEPTH_SAFE_SCOPE.get() > 0;
    }
}
