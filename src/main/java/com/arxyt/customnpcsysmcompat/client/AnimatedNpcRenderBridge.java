package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.GunCompat;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationController;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationFrame;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationInput;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationState;
import com.arxyt.customnpcsysmcompat.animation.NpcMovementTracker;
import com.arxyt.customnpcsysmcompat.animation.MeleeAttackSync;
import com.arxyt.customnpcsysmcompat.animation.NpcOrientationTracker;
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
import net.minecraft.world.entity.player.Player;
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

        // A hidden corpse has no renderer call in stock CustomNPCs. Explicitly
        // suppress it here as well because the replacement hook is deeper.
        if (!preview && shouldHide(npc)) {
            return true;
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
            Player viewer = Minecraft.getInstance().player;
            Visibility visibility = preview ? Visibility.VISIBLE : visibility(npc, viewer);
            boolean partialVisibility = visibility == Visibility.PARTIAL;
            poseStack.pushPose();
            ProxyVisibilityContext.begin(partialVisibility, npc.getId());
            boolean rendered;
            try {
                poseStack.scale(npc.scaleX / 5.0F * size, npc.scaleY / 5.0F * size,
                        npc.scaleZ / 5.0F * size);
                float renderYaw = holder.orientation.interpolatedBodyYaw(renderPartialTick);
                rendered = Ysm265Adapter.renderPlayer(holder.player, renderYaw, renderPartialTick,
                        poseStack, ProxyVisibilityContext.applyAlpha(buffers), packedLight);
            } finally {
                ProxyVisibilityContext.end();
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
        AnimatedProxy created = new AnimatedProxy(new YsmNpcProxyPlayer(level, new GameProfile(id, name)));
        cache.put(original, created);
        PROXY_PLAYERS.put(created.player, Boolean.TRUE);
        return created;
    }

    private static void sync(AnimatedProxy holder, EntityNPCInterface npc, float partialTick,
                             boolean preview, int animationTick) {
        YsmNpcProxyPlayer player = holder.player;
        boolean dead = !preview && (npc.isDeadOrDying() || npc.isKilled());
        if (dead) {
            if (holder.deathStartedAt == Integer.MIN_VALUE || animationTick < holder.deathStartedAt) {
                holder.deathStartedAt = animationTick;
            }
            animationTick = Math.min(animationTick, holder.deathStartedAt + 20);
        } else {
            holder.deathStartedAt = Integer.MIN_VALUE;
        }
        NpcMovementTracker.Sample movement = preview ? NpcMovementTracker.Sample.STOPPED
                : holder.movementTracker.sample(npc.tickCount, npc.getX(), npc.getZ());
        MeleeAttackSync.Sample attack = preview ? MeleeAttackSync.Sample.INACTIVE
                : MeleeAttackSync.sample(npc, partialTick);
        if (!preview && !attack.active()) {
            attack = vanillaSwingSample(npc, partialTick);
        }
        float targetBodyYaw = movement.walking() ? movement.movementYaw() : npc.yBodyRot;
        NpcOrientationTracker.Frame orientation = preview
                ? NpcOrientationTracker.fixed(targetBodyYaw, npc.yHeadRot)
                : holder.orientationTracker.sample(npc.tickCount, targetBodyYaw, npc.yHeadRot);
        holder.orientation = orientation;
        NpcAnimationInput input = new NpcAnimationInput(animationTick,
                dead, preview ? 0 : Math.min(20, npc.deathTime),
                preview ? 0 : npc.hurtTime, attack.interpolatedProgress(), movement.walking(), movement.speed(),
                orientation.bodyYaw(), orientation.headYaw());
        NpcAnimationFrame frame = holder.controller.update(input);

        player.tickCount = input.tick();
        player.setPos(npc.getX(), npc.getY(), npc.getZ());
        player.setDeltaMovement(npc.getDeltaMovement());
        player.setPose(Pose.STANDING);
        player.setOnGround(npc.onGround());
        player.setXRot(npc.getXRot());
        player.xRotO = preview ? npc.getXRot() : npc.xRotO;
        player.setYRot(frame.bodyYaw());
        player.yRotO = orientation.previousBodyYaw();
        player.yBodyRot = frame.bodyYaw();
        player.yBodyRotO = orientation.previousBodyYaw();
        player.yHeadRot = frame.headYaw();
        player.yHeadRotO = orientation.previousHeadYaw();

        if (holder.lastSyncedTick != input.tick()) {
            player.walkAnimation.update(frame.walkSpeed(), 1.0F);
            holder.lastSyncedTick = input.tick();
        }
        // Report death to YSM without health=0/deathTime>0, because either value can select
        // the vanilla red corpse overlay. The proxy override preserves YSM's death animation.
        player.setCompatDead(dead);
        player.setHealth(proxyHealth(player));
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        holder.ysmFoodLevel = Ysm265Adapter.normalizePlayerState(player);
        player.hurtTime = dead ? 0 : frame.hurtTime();
        // YSM selects its death animation from isDeadOrDying() (health == 0).
        // Keeping vanilla deathTime non-zero adds the red corpse presentation;
        // the frozen YSM clock already preserves the animation's final frame.
        player.deathTime = 0;
        boolean attacking = attack.active() && frame.state() == NpcAnimationState.ATTACK;
        player.swinging = attacking;
        player.swingingArm = InteractionHand.MAIN_HAND;
        player.swingTime = attacking ? attack.swingTime() : 0;
        player.attackAnim = attacking ? attack.currentProgress() : 0.0F;
        player.oAttackAnim = attacking ? attack.previousProgress() : 0.0F;
        traceAttack(holder, npc, partialTick, attack, frame, attacking, player);
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setSwimming(false);
        player.stopUsingItem();

        Minecraft minecraft = Minecraft.getInstance();
        Player viewer = minecraft.player;
        Visibility visibility = preview ? Visibility.VISIBLE : visibility(npc, viewer);
        player.setCompatVisibility(visibility != Visibility.VISIBLE,
                visibility == Visibility.HIDDEN);
        player.setGlowingTag(!preview && npc.isCurrentlyGlowing());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, npc.getItemBySlot(slot));
        }
        if (!preview) {
            GunCompat.syncClientState(npc, player);
            traceProxyState(holder, npc, player, visibility);
        }
    }

    private static void traceProxyState(AnimatedProxy holder, EntityNPCInterface npc,
                                        YsmNpcProxyPlayer player, Visibility visibility) {
        Player viewer = Minecraft.getInstance().player;
        String state = "model=" + holder.modelId
                + ",displayVisible=" + npc.display.getVisible()
                + ",npcInvisible=" + npc.isInvisible()
                + ",npcInvisibleTo=" + (viewer != null && npc.isInvisibleTo(viewer))
                + ",displayVisibleTo=" + (viewer != null && npc.display.isVisibleTo(viewer))
                + ",resolved=" + visibility
                + ",proxyInvisible=" + player.isInvisible()
                + ",proxyInvisibleTo=" + (viewer != null && player.isInvisibleTo(viewer))
                + ",food=" + player.getFoodData().getFoodLevel()
                + ",ysmFood=" + holder.ysmFoodLevel
                + ",health=" + player.getHealth() + "/" + player.getMaxHealth()
                + ",deadOrDying=" + player.isDeadOrDying()
                + ",hurtTime=" + player.hurtTime
                + ",deathTime=" + player.deathTime
                + ",pose=" + player.getPose()
                + ",usingItem=" + player.isUsingItem()
                + ",usedHand=" + player.getUsedItemHand()
                + ",mainItem=" + player.getMainHandItem().getItem()
                + ",offItem=" + player.getOffhandItem().getItem()
                + ",sneaking=" + player.isShiftKeyDown()
                + ",swimming=" + player.isSwimming()
                + ",sprinting=" + player.isSprinting();
        if (state.equals(holder.lastProxyDebugState)) {
            return;
        }
        holder.lastProxyDebugState = state;
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-PROXY-TRACE] npcId={} tick={} {}", npc.getId(), npc.tickCount, state);
    }

    private static boolean shouldHide(EntityNPCInterface npc) {
        return npc.isKilled() && npc.stats.hideKilledBody && npc.deathTime > 20;
    }

    private static Visibility visibility(EntityNPCInterface npc, Player viewer) {
        int displayMode = npc.display.getVisible();
        if (displayMode == 2) {
            return Visibility.PARTIAL;
        }
        if (displayMode == 1) {
            return viewer != null && !npc.isInvisibleTo(viewer)
                    ? Visibility.PARTIAL : Visibility.HIDDEN;
        }
        return npc.isInvisible() ? Visibility.HIDDEN : Visibility.VISIBLE;
    }

    private static float proxyHealth(RemotePlayer player) {
        return player.getMaxHealth();
    }

    private static MeleeAttackSync.Sample vanillaSwingSample(EntityNPCInterface npc, float partialTick) {
        float interpolated = npc.getAttackAnim(partialTick);
        if (!npc.swinging && interpolated <= 0.001F && npc.attackAnim <= 0.001F) {
            return MeleeAttackSync.Sample.INACTIVE;
        }
        return new MeleeAttackSync.Sample(true, Math.max(0, npc.swingTime),
                Math.max(0.0F, npc.attackAnim), Math.max(0.0F, npc.oAttackAnim),
                Math.max(0.001F, interpolated));
    }

    private static void traceAttack(AnimatedProxy holder, EntityNPCInterface npc, float partialTick,
                                    MeleeAttackSync.Sample attack, NpcAnimationFrame frame,
                                    boolean attacking, RemotePlayer player) {
        if (attack.active() && holder.lastAttackDebugTick != npc.tickCount) {
            holder.lastAttackDebugTick = npc.tickCount;
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-ATTACK-TRACE][CLIENT-PROXY] npcId={} tick={} partial={} rawSwinging={} rawSwingTime={} rawAttackAnim={} sampleSwingTime={} samplePrev={} sampleCurrent={} sampleInterpolated={} controller={} proxySwinging={} proxySwingTime={} proxyPrev={} proxyCurrent={}",
                    npc.getId(), npc.tickCount, partialTick, npc.swinging, npc.swingTime,
                    npc.getAttackAnim(partialTick), attack.swingTime(), attack.previousProgress(),
                    attack.currentProgress(), attack.interpolatedProgress(), frame.state(), attacking,
                    player.swingTime, player.oAttackAnim, player.attackAnim);
        }
        if (holder.attackDebugActive && !attack.active()) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-ATTACK-TRACE][CLIENT-END] npcId={} tick={} controller={} proxySwinging={}",
                    npc.getId(), npc.tickCount, frame.state(), player.swinging);
        }
        holder.attackDebugActive = attack.active();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderName(EntityNPCInterface npc, PoseStack poseStack,
                                   MultiBufferSource buffers, int packedLight) {
        Player viewer = Minecraft.getInstance().player;
        if (visibility(npc, viewer) == Visibility.HIDDEN) {
            return;
        }
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
        ProxyVisibilityContext.clearDebugState();
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
        private final YsmNpcProxyPlayer player;
        private final NpcAnimationController controller = new NpcAnimationController();
        private final NpcMovementTracker movementTracker = new NpcMovementTracker();
        private final NpcOrientationTracker orientationTracker = new NpcOrientationTracker();
        private NpcOrientationTracker.Frame orientation = NpcOrientationTracker.fixed(0.0F, 0.0F);
        private int lastSyncedTick = Integer.MIN_VALUE;
        private int lastAttackDebugTick = Integer.MIN_VALUE;
        private boolean attackDebugActive;
        private int deathStartedAt = Integer.MIN_VALUE;
        private String lastProxyDebugState = "";
        private int ysmFoodLevel = -1;
        private String modelId = "";

        private AnimatedProxy(YsmNpcProxyPlayer player) {
            this.player = player;
        }

    }

    /** Reproduces CustomNPCs' full/partial/wand visibility decision for YSM. */
    private static final class YsmNpcProxyPlayer extends RemotePlayer {
        private boolean compatInvisibleToViewer;
        private boolean compatDead;

        private YsmNpcProxyPlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
        }

        private void setCompatVisibility(boolean invisible, boolean invisibleToViewer) {
            setInvisible(invisible);
            compatInvisibleToViewer = invisibleToViewer;
        }

        private void setCompatDead(boolean dead) {
            compatDead = dead;
        }

        @Override
        public boolean isDeadOrDying() {
            return compatDead || super.isDeadOrDying();
        }

        @Override
        public boolean isInvisibleTo(Player viewer) {
            return compatInvisibleToViewer;
        }
    }

    private enum Visibility {
        VISIBLE,
        PARTIAL,
        HIDDEN
    }
}
