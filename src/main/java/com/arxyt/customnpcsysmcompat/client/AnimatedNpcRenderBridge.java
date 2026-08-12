package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.GunCompat;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationController;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationFrame;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationInput;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationState;
import com.arxyt.customnpcsysmcompat.animation.NpcMovementTracker;
import com.arxyt.customnpcsysmcompat.animation.MeleeAttackSync;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.Util;
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
    private static final Map<EntityNPCInterface, AnimatedProxy> PREVIEW_PROXIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Boolean> PROXY_PLAYERS =
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
        boolean preview = selected != null;
        if (!preview) {
            selected = YsmDisplayAccess.get(npc.display);
        }
        if (!selected.enabled() || !Ysm265Adapter.hasModel(selected.modelId())) {
            return false;
        }

        try {
            AnimatedProxy holder = proxyFor(npc, preview);
            long previewMillis = preview ? Util.getMillis() : 0L;
            float renderPartialTick = preview ? (previewMillis % 50L) / 50.0F : partialTick;
            int animationTick = preview ? (int) (previewMillis / 50L) : npc.tickCount;
            sync(holder, npc, renderPartialTick, preview, animationTick);
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
                float renderYaw = preview ? holder.player.yBodyRot : yaw;
                rendered = Ysm265Adapter.renderPlayer(holder.player, renderYaw, renderPartialTick,
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

    private static AnimatedProxy proxyFor(EntityNPCInterface original, boolean preview) {
        Map<EntityNPCInterface, AnimatedProxy> cache = preview ? PREVIEW_PROXIES : PROXIES;
        AnimatedProxy existing = cache.get(original);
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
        cache.put(original, created);
        PROXY_PLAYERS.put(created.player, Boolean.TRUE);
        return created;
    }

    private static void sync(AnimatedProxy holder, EntityNPCInterface npc, float partialTick,
                             boolean preview, int animationTick) {
        RemotePlayer player = holder.player;
        NpcMovementTracker.Sample movement = preview ? NpcMovementTracker.Sample.STOPPED
                : holder.movementTracker.sample(npc.tickCount, npc.getX(), npc.getZ());
        float attackProgress = preview ? 0.0F : MeleeAttackSync.progress(npc, partialTick);
        float bodyYaw = movement.walking() ? movement.movementYaw() : npc.yBodyRot;
        NpcAnimationInput input = new NpcAnimationInput(animationTick,
                !preview && (npc.isDeadOrDying() || npc.isKilled()), preview ? 0 : npc.deathTime,
                preview ? 0 : npc.hurtTime, attackProgress, movement.walking(), movement.speed(),
                bodyYaw, npc.yHeadRot);
        NpcAnimationFrame frame = holder.controller.update(input);

        player.tickCount = input.tick();
        player.setPos(npc.getX(), npc.getY(), npc.getZ());
        player.setDeltaMovement(npc.getDeltaMovement());
        player.setPose(Pose.STANDING);
        player.setOnGround(npc.onGround());
        player.setXRot(npc.getXRot());
        player.xRotO = preview ? npc.getXRot() : npc.xRotO;
        player.setYRot(frame.bodyYaw());
        player.yRotO = preview ? frame.bodyYaw() : npc.yRotO;
        player.yBodyRot = frame.bodyYaw();
        player.yBodyRotO = preview ? frame.bodyYaw() : npc.yBodyRotO;
        player.yHeadRot = frame.headYaw();
        player.yHeadRotO = preview ? frame.headYaw() : npc.yHeadRotO;

        if (holder.lastSyncedTick != input.tick()) {
            player.walkAnimation.update(frame.walkSpeed(), 1.0F);
            holder.lastSyncedTick = input.tick();
        }
        player.hurtTime = frame.hurtTime();
        player.deathTime = frame.deathTime();
        player.swinging = frame.state() == NpcAnimationState.ATTACK;
        player.swingingArm = InteractionHand.MAIN_HAND;
        player.swingTime = preview ? 0 : npc.swingTime;
        player.attackAnim = frame.attackProgress();
        player.oAttackAnim = frame.attackProgress();
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setSwimming(false);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, npc.getItemBySlot(slot));
        }
        if (!preview) {
            GunCompat.syncClientState(npc, player);
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
        PREVIEW_PROXIES.clear();
        PROXY_PLAYERS.clear();
        PreviewOverrides.clearAll();
        MeleeAttackSync.clear();
    }

    public static boolean isProxyPlayer(Entity entity) {
        return PROXY_PLAYERS.containsKey(entity);
    }

    public static void discardPreview(EntityNPCInterface npc) {
        AnimatedProxy removed = PREVIEW_PROXIES.remove(npc);
        if (removed != null) {
            PROXY_PLAYERS.remove(removed.player);
        }
    }

    private static final class AnimatedProxy {
        private final RemotePlayer player;
        private final NpcAnimationController controller = new NpcAnimationController();
        private final NpcMovementTracker movementTracker = new NpcMovementTracker();
        private int lastSyncedTick = Integer.MIN_VALUE;
        private String modelId = "";

        private AnimatedProxy(RemotePlayer player) {
            this.player = player;
        }

    }
}
