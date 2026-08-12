package com.arxyt.customnpcsysmcompat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/** Records YSM's model-local bounds and optionally corrects its root translation. */
final class YsmVertexCapture implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final Map<VertexConsumer, VertexConsumer> wrappers = new IdentityHashMap<>();
    private final Matrix4f inverseBase;
    private final Vector3f outputCorrection;
    private long vertices;
    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double minZ = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;
    private double maxZ = Double.NEGATIVE_INFINITY;

    YsmVertexCapture(MultiBufferSource delegate, Matrix4f baseTransform, double localCorrectionY) {
        this.delegate = delegate;
        this.inverseBase = new Matrix4f(baseTransform).invert();
        this.outputCorrection = new Matrix4f(baseTransform).transformDirection(
                0.0F, (float) localCorrectionY, 0.0F, new Vector3f());
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        VertexConsumer target = delegate.getBuffer(renderType);
        return wrappers.computeIfAbsent(target, CapturingVertexConsumer::new);
    }

    String bounds() {
        if (vertices == 0) return "vertices=0";
        return "vertices=" + vertices
                + ",min=" + minX + "/" + minY + "/" + minZ
                + ",max=" + maxX + "/" + maxY + "/" + maxZ
                + ",center=" + ((minX + maxX) * 0.5D) + "/"
                + ((minY + maxY) * 0.5D) + "/" + ((minZ + maxZ) * 0.5D)
                + ",size=" + (maxX - minX) + "/" + (maxY - minY) + "/" + (maxZ - minZ);
    }

    boolean hasVertices() {
        return vertices > 0;
    }

    double rawFloor() {
        return minY;
    }

    double rawCeiling() {
        return maxY;
    }

    private final class CapturingVertexConsumer implements VertexConsumer {
        private final VertexConsumer target;

        private CapturingVertexConsumer(VertexConsumer target) {
            this.target = target;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            Vector3f local = inverseBase.transformPosition((float) x, (float) y, (float) z, new Vector3f());
            vertices++;
            minX = Math.min(minX, local.x);
            minY = Math.min(minY, local.y);
            minZ = Math.min(minZ, local.z);
            maxX = Math.max(maxX, local.x);
            maxY = Math.max(maxY, local.y);
            maxZ = Math.max(maxZ, local.z);
            target.vertex(x + outputCorrection.x, y + outputCorrection.y, z + outputCorrection.z);
            return this;
        }

        @Override public VertexConsumer color(int r, int g, int b, int a) { target.color(r, g, b, a); return this; }
        @Override public VertexConsumer uv(float u, float v) { target.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { target.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { target.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { target.normal(x, y, z); return this; }
        @Override public void endVertex() { target.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { target.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { target.unsetDefaultColor(); }
    }
}
