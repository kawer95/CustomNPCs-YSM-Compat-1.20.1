package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationController;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationFrame;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationInput;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationState;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class AnimatedNpcRenderBridge {
    private static final Map<EntityNPCInterface, AnimatedProxy> PROXIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AnimatedNpcRenderBridge() {
    }

    /** Returns true only when YSM successfully rendered the replacement model. */
    public static boolean tryRender(Entity entity, float yaw, float partialTick, PoseStack poseStack,
                                    MultiBufferSource buffers, int packedLight) {
        if (!(entity instanceof EntityNPCInterface npc)) {
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
            AnimatedProxy holder = proxyFor(npc);
            sync(holder, npc);
            if (!selected.modelId().equals(holder.modelId)) {
                if (!Ysm265Adapter.setPlayerModel(holder.player, selected.modelId())) {
                    return false;
                }
                holder.modelId = selected.modelId();
            } else if (!Ysm265Adapter.isPlayerModelReady(holder.player)) {
                return false;
            }

            int size = npc.display.getSize();
            poseStack.pushPose();
            boolean rendered;
            try {
                poseStack.scale(npc.scaleX / 5.0F * size, npc.scaleY / 5.0F * size,
                        npc.scaleZ / 5.0F * size);
                rendered = Ysm265Adapter.renderPlayer(holder.player, yaw, partialTick,
                        poseStack, buffers, packedLight);
            } finally {
                poseStack.popPose();
            }
            if (!rendered) {
                return false;
            }

            renderName(npc, poseStack, buffers, packedLight);
            return true;
        } catch (Throwable error) {
            CustomNpcsYsmCompat.LOGGER.error("Failed to render animated YSM model for CustomNPC {}", npc.getId(), error);
            return false;
        }
    }

    private static AnimatedProxy proxyFor(EntityNPCInterface original) {
        AnimatedProxy existing = PROXIES.get(original);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException("Cannot create YSM NPC proxy without a client level");
        }
        if (existing != null && existing.player.level() == level) {
            return existing;
        }

        UUID id = UUID.nameUUIDFromBytes(("customnpcs-ysm:" + original.getUUID())
                .getBytes(StandardCharsets.UTF_8));
        String name = "YsmNpc" + Math.abs(original.getId() % 1_000_000);
        AnimatedProxy created = new AnimatedProxy(new RemotePlayer(level, new GameProfile(id, name)));
        PROXIES.put(original, created);
        return created;
    }

    private static void sync(AnimatedProxy holder, EntityNPCInterface npc) {
        RemotePlayer player = holder.player;
        NpcAnimationInput input = new NpcAnimationInput(npc.tickCount,
                npc.isDeadOrDying() || npc.isKilled(), npc.deathTime, npc.hurtTime,
                npc.isAttacking(), npc.isWalking(), npc.walkAnimation.speed(1.0F),
                npc.yBodyRot, npc.yHeadRot);
        NpcAnimationFrame frame = holder.controller.update(input);

        player.tickCount = input.tick();
        player.setPos(npc.getX(), npc.getY(), npc.getZ());
        player.setDeltaMovement(npc.getDeltaMovement());
        player.setPose(Pose.STANDING);
        player.setOnGround(npc.onGround());
        player.setXRot(npc.getXRot());
        player.xRotO = npc.xRotO;
        player.setYRot(frame.bodyYaw());
        player.yRotO = npc.yRotO;
        player.yBodyRot = frame.bodyYaw();
        player.yBodyRotO = npc.yBodyRotO;
        player.yHeadRot = frame.headYaw();
        player.yHeadRotO = npc.yHeadRotO;

        if (holder.lastSyncedTick != input.tick()) {
            player.walkAnimation.update(frame.walkSpeed(), 1.0F);
            holder.lastSyncedTick = input.tick();
        }
        player.hurtTime = frame.hurtTime();
        player.deathTime = frame.deathTime();
        player.swinging = frame.state() == NpcAnimationState.ATTACK;
        player.swingingArm = InteractionHand.MAIN_HAND;
        player.swingTime = Math.round(frame.attackProgress() * 6.0F);
        player.attackAnim = frame.attackProgress();
        player.oAttackAnim = frame.attackProgress();
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setSwimming(false);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, npc.getItemBySlot(slot));
        }
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
        PreviewOverrides.clearAll();
    }

    private static final class AnimatedProxy {
        private final RemotePlayer player;
        private final NpcAnimationController controller = new NpcAnimationController();
        private int lastSyncedTick = Integer.MIN_VALUE;
        private String modelId = "";

        private AnimatedProxy(RemotePlayer player) {
            this.player = player;
        }
    }
}
