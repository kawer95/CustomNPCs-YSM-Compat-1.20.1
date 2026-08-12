package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class StaticNpcRenderBridge {
    private static final Map<EntityNPCInterface, EntityNPCInterface> PROXIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Boolean> PROXY_MARKERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private StaticNpcRenderBridge() {
    }

    /** Returns true only when the bridge rendered the replacement model. */
    public static boolean tryRender(Entity entity, float yaw, PoseStack poseStack,
                                    MultiBufferSource buffers, int packedLight) {
        if (!(entity instanceof EntityNPCInterface npc) || PROXY_MARKERS.containsKey(entity)) {
            return false;
        }

        YsmDisplayData selected = PreviewOverrides.get(npc);
        if (selected == null) {
            selected = YsmDisplayAccess.get(npc.display);
        }
        if (!selected.enabled() || !Ysm265Adapter.hasModel(selected.modelId())) {
            return false;
        }

        try {
            EntityNPCInterface proxy = proxyFor(npc);
            freeze(proxy, npc);
            if (!Ysm265Adapter.setModel(proxy, selected.modelId())) {
                return false;
            }

            int size = npc.display.getSize();
            poseStack.pushPose();
            poseStack.scale(npc.scaleX / 5.0F * size, npc.scaleY / 5.0F * size, npc.scaleZ / 5.0F * size);
            boolean allowOriginal = Ysm265Adapter.render(proxy, yaw, poseStack, buffers, packedLight);
            poseStack.popPose();
            if (allowOriginal) {
                return false;
            }

            renderName(npc, poseStack, buffers, packedLight);
            return true;
        } catch (Throwable error) {
            CustomNpcsYsmCompat.LOGGER.error("Failed to render static YSM model for CustomNPC {}", npc.getId(), error);
            return false;
        }
    }

    private static EntityNPCInterface proxyFor(EntityNPCInterface original) {
        EntityNPCInterface existing = PROXIES.get(original);
        if (existing != null && existing.getType() == original.getType()) {
            return existing;
        }
        Entity created = ((EntityType<?>) original.getType()).create(original.level());
        if (!(created instanceof EntityNPCInterface proxy)) {
            throw new IllegalStateException("CustomNPC entity type did not create an NPC proxy: " + original.getType());
        }
        PROXIES.put(original, proxy);
        PROXY_MARKERS.put(proxy, Boolean.TRUE);
        return proxy;
    }

    private static void freeze(EntityNPCInterface proxy, EntityNPCInterface original) {
        float bodyYaw = original.yBodyRot;
        proxy.tickCount = 0;
        proxy.setPos(original.getX(), original.getY(), original.getZ());
        proxy.setDeltaMovement(Vec3.ZERO);
        proxy.setPose(Pose.STANDING);
        proxy.setXRot(0.0F);
        proxy.xRotO = 0.0F;
        proxy.setYRot(bodyYaw);
        proxy.yRotO = bodyYaw;
        proxy.yBodyRot = bodyYaw;
        proxy.yBodyRotO = bodyYaw;
        proxy.yHeadRot = bodyYaw;
        proxy.yHeadRotO = bodyYaw;
        proxy.hurtTime = 0;
        proxy.deathTime = 0;
        proxy.swingTime = 0;
        proxy.setSprinting(false);
        proxy.setShiftKeyDown(false);
        proxy.setSwimming(false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderName(EntityNPCInterface npc, PoseStack poseStack,
                                   MultiBufferSource buffers, int packedLight) {
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(npc);
        if (renderer instanceof RenderNPCInterface npcRenderer) {
            npcRenderer.renderNameTag(npc, npc.getDisplayName(), poseStack, buffers, packedLight);
        }
    }

    public static void clearCaches() {
        PROXIES.clear();
        PROXY_MARKERS.clear();
        PreviewOverrides.clearAll();
    }
}
