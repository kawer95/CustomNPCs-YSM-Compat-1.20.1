package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.GunCompat;
import com.arxyt.customnpcsysmcompat.NpcCrawlState;
import com.arxyt.customnpcsysmcompat.RenderStabilityConfig;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationController;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationFrame;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationInput;
import com.arxyt.customnpcsysmcompat.animation.NpcAnimationState;
import com.arxyt.customnpcsysmcompat.animation.NpcMovementTracker;
import com.arxyt.customnpcsysmcompat.animation.MeleeAttackSync;
import com.arxyt.customnpcsysmcompat.animation.NpcOrientationTracker;
import com.arxyt.customnpcsysmcompat.animation.NpcHurtState;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import com.arxyt.customnpcsysmcompat.mixin.EntitySharedFlagAccessor;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.CustomItems;
import noppes.npcs.entity.EntityNPCInterface;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class AnimatedNpcRenderBridge {
    private static final Map<EntityNPCInterface, AnimatedProxy> PROXIES =
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

        YsmDisplayData selected = YsmDisplayAccess.get(npc.display);
        if (!selected.enabled() || !Ysm265Adapter.hasModel(selected.modelId())) {
            return false;
        }

        Player viewer = Minecraft.getInstance().player;
        Visibility resolvedVisibility = visibility(npc, viewer);
        if (resolvedVisibility == Visibility.HIDDEN) {
            return true;
        }

        // A hidden corpse has no renderer call in stock CustomNPCs. Explicitly
        // suppress it here as well because the replacement hook is deeper.
        if (shouldHide(npc)) {
            return true;
        }

        try {
            AnimatedProxy holder = readyProxy(npc, selected);
            if (holder == null) return false;
            ensureSynced(holder, npc);

            // CustomNPCs 20260711 promotes display size from integer to float. Keeping the
            // multiplier as float preserves fractional size while remaining source-compatible
            // with the older integer-returning API.
            float size = npc.display.getSize();
            boolean partialVisibility = resolvedVisibility == Visibility.PARTIAL;
            poseStack.pushPose();
            ProxyVisibilityContext.begin(partialVisibility, npc.getId());
            boolean rendered;
            try {
                poseStack.scale(npc.scaleX / 5.0F * size, npc.scaleY / 5.0F * size,
                        npc.scaleZ / 5.0F * size);
                float renderYaw = holder.orientation.interpolatedBodyYaw(partialTick);
                GunCompat.beginClientRender();
                try {
                    rendered = Ysm265Adapter.renderPlayer(holder.player, renderYaw, partialTick,
                            poseStack, buffers, packedLight);
                } finally {
                    GunCompat.endClientRender();
                }
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

    private static AnimatedProxy readyProxy(EntityNPCInterface original, YsmDisplayData selected) {
        AnimatedProxy existing = PROXIES.get(original);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException("Cannot create YSM NPC proxy without a client level");
        }
        YsmTweakProfile desired = selected.tweaksFor(selected.modelId());
        if (existing != null && existing.player.level() == level
                && selected.modelId().equals(existing.modelId)
                && desired.equals(existing.appliedTweaks)
                && Ysm265Adapter.isPlayerModelReady(existing.player)) {
            return existing;
        }

        UUID id = UUID.nameUUIDFromBytes(("customnpcs-ysm:" + original.getUUID())
                .getBytes(StandardCharsets.UTF_8));
        String name = "YsmNpc" + Math.abs(original.getId() % 1_000_000);
        AnimatedProxy created = new AnimatedProxy(new YsmNpcProxyPlayer(level, new GameProfile(id, name)));
        if (!Ysm265Adapter.setPlayerModel(created.player, selected.modelId())) return null;
        created.modelId = selected.modelId();
        created.appliedTweaks = desired;
        Ysm265Adapter.applyPlayerTweaks(created.player, created.modelId, desired);
        sync(created, original);
        PROXIES.put(original, created);
        if (existing != null) PROXY_PLAYERS.remove(existing.player);
        PROXY_PLAYERS.put(created.player, Boolean.TRUE);
        return created;
    }

    private static void ensureSynced(AnimatedProxy holder, EntityNPCInterface npc) {
        if (holder.lastObservedNpcTick != npc.tickCount) sync(holder, npc);
    }

    private static void sync(AnimatedProxy holder, EntityNPCInterface npc) {
        YsmNpcProxyPlayer player = holder.player;
        int animationTick = npc.tickCount;
        boolean dead = npc.isDeadOrDying() || npc.isKilled();
        if (dead) {
            if (holder.deathStartedAt == Integer.MIN_VALUE || animationTick < holder.deathStartedAt) {
                holder.deathStartedAt = animationTick;
            }
            animationTick = Math.min(animationTick, holder.deathStartedAt + 20);
        } else {
            holder.deathStartedAt = Integer.MIN_VALUE;
        }
        NpcMovementTracker.Sample movement = holder.movementTracker.sample(npc.tickCount, npc.getX(), npc.getZ());
        MeleeAttackSync.Sample attack = MeleeAttackSync.sample(npc, 0.0F);
        if (!attack.active()) {
            attack = vanillaSwingSample(npc, 0.0F);
        }
        // A retreating gun NPC moves opposite to its aim. Do not turn the YSM proxy
        // toward that displacement or it appears to run around and shoot backwards.
        boolean backpedalling = holder.movementTracker.backpedalling(movement, npc.yHeadRot);
        float targetBodyYaw = movement.walking()
                ? (backpedalling ? npc.yHeadRot : movement.movementYaw())
                : npc.yBodyRot;
        traceRetreatRender(holder, npc, movement, backpedalling, targetBodyYaw);
        NpcOrientationTracker.Frame orientation = holder.orientationTracker.sample(
                npc.tickCount, targetBodyYaw, npc.yHeadRot);
        holder.orientation = orientation;
        NpcAnimationInput input = new NpcAnimationInput(animationTick,
                dead, Math.min(20, npc.deathTime),
                npc.hurtTime, attack.interpolatedProgress(), movement.walking(), movement.speed(),
                orientation.bodyYaw(), orientation.headYaw());
        NpcAnimationFrame frame = holder.controller.update(input);

        player.tickCount = input.tick();
        player.setPos(npc.getX(), npc.getY(), npc.getZ());
        // RemotePlayer is a render-only proxy and therefore never receives Entity#tick(),
        // which normally advances xo/yo/zo. YSM explicitly interpolates those fields and
        // derives an animation-space movement vector from them. Leaving the proxy's old
        // coordinates stale makes a normal hurt transition look like a large vertical
        // teleport to model scripts, which can leave the model root below the NPC.
        player.xo = npc.xo;
        player.yo = npc.yo;
        player.zo = npc.zo;
        player.setDeltaMovement(npc.getDeltaMovement());
        player.fallDistance = npc.fallDistance;
        player.setOnGround(npc.onGround());
        player.setXRot(npc.getXRot());
        player.xRotO = npc.xRotO;
        player.setYRot(frame.bodyYaw());
        player.yRotO = orientation.previousBodyYaw();
        player.yBodyRot = frame.bodyYaw();
        player.yBodyRotO = orientation.previousBodyYaw();
        player.yHeadRot = frame.headYaw();
        player.yHeadRotO = orientation.previousHeadYaw();

        boolean advancedTick = holder.lastSyncedTick != input.tick();
        if (advancedTick) {
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
        // Preserve model-pack-defined hurt actions. The paired clocks are normalized because
        // LivingEntity normally establishes both together, while the render-only proxy does
        // not pass through LivingEntity#hurt and must mirror that invariant explicitly.
        NpcHurtState hurt = NpcHurtState.normalize(frame.hurtTime(), npc.hurtDuration, dead);
        player.hurtDuration = hurt.duration();
        player.hurtTime = hurt.time();
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
        traceAttack(holder, npc, 0.0F, attack, frame, attacking, player);
        // Dominion's server-side command is the single source of truth for sprint.  The
        // movement sample gates the flag so a delayed entity-data packet cannot select a run
        // animation while the proxy is stationary; YSM then sees the same isSprinting state as
        // a real player and can select both standard run and TACZ tac:run controllers.
        player.setSprinting(!dead && !NpcCrawlState.isCrawling(npc)
                && movement.walking() && npc.isSprinting());
        // The proxy has no entity lifecycle, so synchronize both the shift flag and cached
        // pose during this one-per-tick update. Crawl remains the higher-priority physical pose.
        boolean crouching = npc.isShiftKeyDown();
        player.setShiftKeyDown(crouching);
        player.setPose(NpcCrawlState.isCrawling(npc) ? Pose.SWIMMING
                : crouching ? Pose.CROUCHING : Pose.STANDING);
        player.setSwimming(false);
        player.stopUsingItem();

        Minecraft minecraft = Minecraft.getInstance();
        Player viewer = minecraft.player;
        Visibility visibility = visibility(npc, viewer);
        // Hidden NPCs are suppressed before rendering. A partial proxy must report visible so
        // YSM reaches its pipeline; the render-local mixin supplies translucency and alpha.
        player.setCompatVisibility(false, false);
        player.setGlowingTag(npc.isCurrentlyGlowing());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, npc.getItemBySlot(slot));
        }
        GunCompat.syncClientState(npc, player);
        NpcActionClientState.State actionState = NpcActionClientState.state(npc);
        String desiredAction = holder.modelId.equals(actionState.actionSetId()) ? actionState.actionId() : "";
        if (actionState.revision() != holder.appliedActionRevision || !desiredAction.equals(holder.appliedAction)) {
            if (desiredAction.isBlank()) Ysm265Adapter.stopPlayerAction(player);
            else if (!Ysm265Adapter.playPlayerAction(player, desiredAction)) desiredAction = "";
            holder.appliedAction = desiredAction;
            holder.appliedActionRevision = actionState.revision();
        }
        traceProxyState(holder, npc, player, visibility);
        // The proxy is deliberately not inserted into ClientLevel, so Forge/YSM never sends
        // it the normal per-entity capability tick. Advance that state exactly once for each
        // mirrored NPC tick after every input field has been synchronized. Without this,
        // one-shot animations such as hurt can leave a stale root-bone translation forever.
        if (advancedTick) Ysm265Adapter.advancePlayerAnimation(player);
        holder.lastObservedNpcTick = npc.tickCount;
    }

    private static void traceProxyState(AnimatedProxy holder, EntityNPCInterface npc,
                                        YsmNpcProxyPlayer player, Visibility visibility) {
        if (!RenderStabilityConfig.ENABLED.get()) return;
        Player viewer = Minecraft.getInstance().player;
        String state = "model=" + holder.modelId
                + ",displayVisible=" + npc.display.getVisible()
                + ",npcInvisible=" + npc.isInvisible()
                + ",invisibilityEffect=" + npc.hasEffect(MobEffects.INVISIBILITY)
                + ",vanillaInvisibleFlag=" + vanillaInvisible(npc)
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
                + ",hurtDuration=" + player.hurtDuration
                + ",npcHurtTime=" + npc.hurtTime
                + ",npcHurtDuration=" + npc.hurtDuration
                + ",proxyOldPos=" + player.xo + "/" + player.yo + "/" + player.zo
                + ",npcOldPos=" + npc.xo + "/" + npc.yo + "/" + npc.zo
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
        if (!claimDiagnosticTick(holder, npc)) return;
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-PROXY-TRACE] npcId={} tick={} {}", npc.getId(), npc.tickCount, state);
    }

    private static boolean shouldHide(EntityNPCInterface npc) {
        return npc.isKilled() && npc.stats.hideKilledBody && npc.deathTime > 20;
    }

    private static void traceRetreatRender(AnimatedProxy holder, EntityNPCInterface npc,
                                           NpcMovementTracker.Sample movement,
                                           boolean backpedalling, float targetBodyYaw) {
        if (!RenderStabilityConfig.ENABLED.get()) return;
        boolean armedMovement = movement.walking() && GunCompat.active(npc);
        if (!armedMovement && !backpedalling && !holder.wasBackpedalling) return;
        if (npc.tickCount % 5 != 0 && backpedalling == holder.wasBackpedalling) return;
        if (!claimDiagnosticTick(holder, npc)) return;
        float difference = net.minecraft.util.Mth.wrapDegrees(movement.movementYaw() - npc.yHeadRot);
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-RETREAT-TRACE][CLIENT-PROXY] npcId={} tick={} armedMovement={} backpedalling={} walking={} speed={} movementYaw={} npcRotation={} npcBodyYaw={} npcHeadYaw={} directionDifference={} selectedProxyBodyYaw={} previousProxyBodyYaw={}",
                npc.getId(), npc.tickCount, armedMovement, backpedalling, movement.walking(), movement.speed(),
                movement.movementYaw(), npc.getYRot(), npc.yBodyRot, npc.yHeadRot, difference,
                targetBodyYaw, holder.orientation.bodyYaw());
        holder.wasBackpedalling = backpedalling;
    }

    private static Visibility visibility(EntityNPCInterface npc, Player viewer) {
        int displayMode = npc.display.getVisible();
        boolean hasWand = viewer != null && viewer.getMainHandItem().getItem() == CustomItems.wand;
        // EntityNPCInterface overrides isInvisible() for its display setting, so it does not
        // expose vanilla's invisibility-effect flag. Read the potion effect explicitly.
        if (vanillaInvisible(npc) || npc.hasEffect(MobEffects.INVISIBILITY)) {
            return hasWand ? Visibility.PARTIAL : Visibility.HIDDEN;
        }
        if (displayMode == 2) {
            return Visibility.PARTIAL;
        }
        if (displayMode == 1) {
            return hasWand ? Visibility.PARTIAL : Visibility.HIDDEN;
        }
        return npc.isInvisible() ? Visibility.HIDDEN : Visibility.VISIBLE;
    }

    private static boolean vanillaInvisible(Entity entity) {
        // Vanilla Entity#isInvisible reads shared flag 5, but CustomNPC overrides that method
        // with display-mode semantics. The shared flag remains the authoritative potion state.
        return ((EntitySharedFlagAccessor) entity).customnpcsYsmCompat$getSharedFlag(5);
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
        if (!RenderStabilityConfig.ENABLED.get()) return;
        if (attack.active() && holder.lastAttackDebugTick != npc.tickCount) {
            if (!claimDiagnosticTick(holder, npc)) return;
            holder.lastAttackDebugTick = npc.tickCount;
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-ATTACK-TRACE][CLIENT-PROXY] npcId={} tick={} partial={} rawSwinging={} rawSwingTime={} rawAttackAnim={} sampleSwingTime={} samplePrev={} sampleCurrent={} sampleInterpolated={} controller={} proxySwinging={} proxySwingTime={} proxyPrev={} proxyCurrent={}",
                    npc.getId(), npc.tickCount, partialTick, npc.swinging, npc.swingTime,
                    npc.getAttackAnim(partialTick), attack.swingTime(), attack.previousProgress(),
                    attack.currentProgress(), attack.interpolatedProgress(), frame.state(), attacking,
                    player.swingTime, player.oAttackAnim, player.attackAnim);
        }
        if (holder.attackDebugActive && !attack.active()) {
            if (!claimDiagnosticTick(holder, npc)) return;
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-ATTACK-TRACE][CLIENT-END] npcId={} tick={} controller={} proxySwinging={}",
                    npc.getId(), npc.tickCount, frame.state(), player.swinging);
        }
        holder.attackDebugActive = attack.active();
    }

    private static boolean claimDiagnosticTick(AnimatedProxy holder, EntityNPCInterface npc) {
        if (holder.lastDiagnosticTick == npc.tickCount) return false;
        holder.lastDiagnosticTick = npc.tickCount;
        return true;
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
            // RenderNPCInterface also emits a nearby chat-bubble pass with DepthTest.ALWAYS.
            // Keep its normal label/title logic, but scope the call so our narrow mixin drops
            // that x-ray pass without changing native CustomNPC rendering elsewhere.
            NpcNameTagRenderContext.begin();
            try {
                npcRenderer.renderNameTag(npc, npc.getDisplayName(), poseStack, buffers, packedLight);
            } finally {
                NpcNameTagRenderContext.end();
            }
        }
    }

    public static void clearCaches() {
        PROXIES.clear();
        PROXY_PLAYERS.clear();
        MeleeAttackSync.clear();
        ProxyVisibilityContext.clearDebugState();
        Ysm265Adapter.clearTweakDiagnostics();
    }

    /** Advances every existing world proxy independently of visibility and render passes. */
    public static void clientTick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        EntityNPCInterface[] tracked;
        synchronized (PROXIES) {
            tracked = PROXIES.keySet().toArray(EntityNPCInterface[]::new);
        }
        for (EntityNPCInterface npc : tracked) {
            if (npc == null || npc.isRemoved() || npc.level() != level) {
                removeProxy(npc);
                continue;
            }
            YsmDisplayData selected = YsmDisplayAccess.get(npc.display);
            if (!selected.enabled() || !Ysm265Adapter.hasModel(selected.modelId())) {
                removeProxy(npc);
                continue;
            }
            try {
                AnimatedProxy holder = readyProxy(npc, selected);
                if (holder != null) ensureSynced(holder, npc);
            } catch (Throwable error) {
                CustomNpcsYsmCompat.LOGGER.error(
                        "Failed to tick animated YSM model for CustomNPC {}", npc.getId(), error);
            }
        }
    }

    private static void removeProxy(EntityNPCInterface npc) {
        if (npc == null) return;
        AnimatedProxy removed = PROXIES.remove(npc);
        if (removed != null) PROXY_PLAYERS.remove(removed.player);
    }

    public static boolean isProxyPlayer(Entity entity) {
        return PROXY_PLAYERS.containsKey(entity);
    }

    public static void registerProxyPlayer(Entity entity) {
        PROXY_PLAYERS.put(entity, Boolean.TRUE);
    }

    public static void unregisterProxyPlayer(Entity entity) {
        PROXY_PLAYERS.remove(entity);
    }

    private static final class AnimatedProxy {
        private final YsmNpcProxyPlayer player;
        private final NpcAnimationController controller = new NpcAnimationController();
        private final NpcMovementTracker movementTracker = new NpcMovementTracker();
        private final NpcOrientationTracker orientationTracker = new NpcOrientationTracker();
        private NpcOrientationTracker.Frame orientation = NpcOrientationTracker.fixed(0.0F, 0.0F);
        private int lastObservedNpcTick = Integer.MIN_VALUE;
        private int lastSyncedTick = Integer.MIN_VALUE;
        private int lastAttackDebugTick = Integer.MIN_VALUE;
        private int lastDiagnosticTick = Integer.MIN_VALUE;
        private boolean attackDebugActive;
        private boolean wasBackpedalling;
        private int deathStartedAt = Integer.MIN_VALUE;
        private String lastProxyDebugState = "";
        private int ysmFoodLevel = -1;
        private String modelId = "";
        private YsmTweakProfile appliedTweaks = YsmTweakProfile.EMPTY;
        private String appliedAction = "";
        private long appliedActionRevision = Long.MIN_VALUE;

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
