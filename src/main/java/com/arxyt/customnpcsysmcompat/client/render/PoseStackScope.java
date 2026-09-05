package com.arxyt.customnpcsysmcompat.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.function.IntSupplier;

/** Restores a caller-owned pose stack to its entry depth after a complete render transaction. */
public final class PoseStackScope implements AutoCloseable {
    private final PoseStack stack;
    private final int initialDepth;
    private boolean closed;

    private PoseStackScope(PoseStack stack) {
        this.stack = stack;
        this.initialDepth = depth(stack);
    }

    public static PoseStackScope capture(PoseStack stack) {
        return new PoseStackScope(stack);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        restoreToDepth(initialDepth, () -> depth(stack), stack::popPose);
    }

    static int leakedFrames(int initialDepth, int currentDepth) {
        return Math.max(0, currentDepth - initialDepth);
    }

    static void restoreToDepth(int initialDepth, IntSupplier currentDepth, Runnable pop) {
        int depth = currentDepth.getAsInt();
        if (depth < initialDepth) {
            throw new IllegalStateException("Renderer popped " + (initialDepth - depth)
                    + " caller-owned PoseStack frame(s)");
        }
        while (depth > initialDepth) {
            pop.run();
            depth = currentDepth.getAsInt();
        }
    }

    private static int depth(PoseStack stack) {
        return ((PoseStackDepthAccess) stack).customnpcsYsmCompat$depth();
    }
}
