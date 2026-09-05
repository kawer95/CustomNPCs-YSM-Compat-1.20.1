package com.arxyt.customnpcsysmcompat.client.render;

/** Implemented on PoseStack by a client mixin; ordinary render code never references the mixin class. */
public interface PoseStackDepthAccess {
    int customnpcsYsmCompat$depth();
}
