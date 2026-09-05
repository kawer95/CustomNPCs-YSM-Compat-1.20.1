package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.render.PoseStackDepthAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Deque;

@Mixin(PoseStack.class)
public abstract class PoseStackDepthMixin implements PoseStackDepthAccess {
    @Shadow @Final private Deque<PoseStack.Pose> poseStack;

    @Override
    public int customnpcsYsmCompat$depth() {
        return poseStack.size();
    }
}
