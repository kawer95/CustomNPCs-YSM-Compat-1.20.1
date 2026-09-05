package com.arxyt.customnpcsysmcompat.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** A side-effect-free vertex sink used to discard CustomNPCs' through-wall bubble pass. */
public enum DiscardingVertexConsumer implements VertexConsumer {
    INSTANCE;

    @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
    @Override public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }
    @Override public VertexConsumer uv(float u, float v) { return this; }
    @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
    @Override public VertexConsumer uv2(int u, int v) { return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    @Override public void endVertex() { }
    @Override public void defaultColor(int red, int green, int blue, int alpha) { }
    @Override public void unsetDefaultColor() { }
}
