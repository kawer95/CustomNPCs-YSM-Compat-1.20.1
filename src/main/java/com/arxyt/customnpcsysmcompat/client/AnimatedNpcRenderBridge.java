package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.GunCompat;
import com.arxyt.customnpcsysmcompat.NpcCrawlState;
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
import net.minecraft.Util;
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
import java.util.ArrayDeque;
import java.util.Deque;
import org.joml.Matrix4f;

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

        Player viewer = Minecraft.getInstance().player;
        Visibility resolvedVisibility = preview ? Visibility.VISIBLE : visibility(npc, viewer);
        if (resolvedVisibility == Visibility.HIDDEN) {
            return true;
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
            boolean hurtDiagnostic = !preview && updateHurtDiagnostic(holder, npc, renderPartialTick);
            boolean modelChanged = !selected.modelId().equals(holder.modelId);
            if (modelChanged) {
                if (!Ysm265Adapter.setPlayerModel(holder.player, selected.modelId())) {
                    return false;
                }
                holder.modelId = selected.modelId();
                holder.verticalAnchor.reset();
            } else if (!Ysm265Adapter.isPlayerModelReady(holder.player)) {
                return false;
            }
            applyTweaks(holder, selected, modelChanged);

            int size = npc.display.getSize();
            boolean partialVisibility = resolvedVisibility == Visibility.PARTIAL;
            poseStack.pushPose();
            ProxyVisibilityContext.begin(partialVisibility, npc.getId());
            boolean rendered;
            try {
                if (hurtDiagnostic) traceRenderMatrix(holder, npc, "bridge-before-scale", poseStack, renderPartialTick, null);
                poseStack.scale(npc.scaleX / 5.0F * size, npc.scaleY / 5.0F * size,
                        npc.scaleZ / 5.0F * size);
                if (hurtDiagnostic) traceRenderMatrix(holder, npc, "bridge-before-ysm", poseStack, renderPartialTick, null);
                float renderYaw = holder.orientation.interpolatedBodyYaw(renderPartialTick);
                GunCompat.beginClientRender();
                try {
                    boolean anchorSample = !preview && holder.verticalAnchor.needsSample(holder.proxyHurtActive);
                    YsmVertexCapture capture = (hurtDiagnostic || anchorSample)
                            ? new YsmVertexCapture(buffers, new Matrix4f(poseStack.last().pose()),
                            preview ? 0.0D : holder.verticalAnchor.correction())
                            : null;
                    rendered = Ysm265Adapter.renderPlayer(holder.player, renderYaw, renderPartialTick,
                            poseStack, capture == null ? buffers : capture, packedLight);
                    if (!preview && capture != null && capture.hasVertices()) {
                        updateVerticalAnchor(holder, npc, capture);
                    }
                    if (hurtDiagnostic) traceRenderMatrix(holder, npc, "bridge-after-ysm", poseStack,
                            renderPartialTick, capture == null ? null : capture.bounds());
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

    private static void updateVerticalAnchor(AnimatedProxy holder, EntityNPCInterface npc,
                                             YsmVertexCapture capture) {
        double previous = holder.verticalAnchor.correction();
        YsmVerticalAnchor.Update update = holder.verticalAnchor.observe(
                capture.rawFloor(), capture.rawCeiling(), holder.proxyHurtActive);
        if (update == YsmVerticalAnchor.Update.CALIBRATED
                || update == YsmVerticalAnchor.Update.CORRECTION_CHANGED
                || update == YsmVerticalAnchor.Update.RELEASED) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-ROOT-ANCHOR] npcId={} tick={} event={} update={} hurt={} rawFloor={} rawCeiling={} correction={} previous={}",
                    npc.getId(), npc.tickCount, holder.hurtDiagnosticEvent, update,
                    holder.proxyHurtActive, capture.rawFloor(), capture.rawCeiling(),
                    holder.verticalAnchor.correction(), previous);
        }
    }

    /** Rebuilds only when a stored override was removed or changed, then replays it once. */
    private static void applyTweaks(AnimatedProxy holder, YsmDisplayData selected, boolean modelChanged) {
        YsmTweakProfile desired = selected.tweaksFor(selected.modelId());
        boolean profileChanged = !desired.equals(holder.appliedTweaks);
        if (!modelChanged && profileChanged && !holder.tweaksNeedReapply) {
            // A form may have been reset to its default. Recreating the YSM model is the
            // only reliable way to remove variables from its private runtime environment.
            if (!Ysm265Adapter.setPlayerModel(holder.player, holder.modelId)) return;
            holder.verticalAnchor.reset();
        }
        if (!modelChanged && !profileChanged && !holder.tweaksNeedReapply) return;

        Ysm265Adapter.TweakApplyResult result = Ysm265Adapter.applyPlayerTweaks(
                holder.player, holder.modelId, desired);
        holder.appliedTweaks = desired;
        holder.tweaksNeedReapply = false;
        // YSM queues expressions for its normal capability tick. The proxy has no world
        // lifecycle, so flush that queue once after a model/reload/configuration change.
        if (result.applied() > 0) Ysm265Adapter.advancePlayerAnimation(holder.player);
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
        // A retreating gun NPC moves opposite to its aim. Do not turn the YSM proxy
        // toward that displacement or it appears to run around and shoot backwards.
        boolean backpedalling = movement.backpedalling(npc.yHeadRot);
        float targetBodyYaw = movement.walking()
                ? (backpedalling ? npc.yHeadRot : movement.movementYaw())
                : npc.yBodyRot;
        traceRetreatRender(holder, npc, movement, backpedalling, targetBodyYaw);
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
        // RemotePlayer is a render-only proxy and therefore never receives Entity#tick(),
        // which normally advances xo/yo/zo. YSM explicitly interpolates those fields and
        // derives an animation-space movement vector from them. Leaving the proxy's old
        // coordinates stale makes a normal hurt transition look like a large vertical
        // teleport to model scripts, which can leave the model root below the NPC.
        player.xo = preview ? npc.getX() : npc.xo;
        player.yo = preview ? npc.getY() : npc.yo;
        player.zo = preview ? npc.getZ() : npc.zo;
        player.setDeltaMovement(npc.getDeltaMovement());
        player.fallDistance = npc.fallDistance;
        // CNPC's CRAWL action is a physical prone state (not merely a model animation).
        // A real TaCZ-crawling player uses Pose.SWIMMING while isSwimming remains false;
        // reproducing exactly that combination lets YSM select its prone/tactical state
        // without falsely turning a ground crawl into water-swimming behavior.
        player.setPose(NpcCrawlState.isCrawling(npc) ? Pose.SWIMMING : Pose.STANDING);
        player.setOnGround(npc.onGround());
        player.setXRot(npc.getXRot());
        player.xRotO = preview ? npc.getXRot() : npc.xRotO;
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
        boolean hurtAnimationEnded = holder.proxyHurtActive && hurt.time() == 0;
        player.hurtDuration = hurt.duration();
        player.hurtTime = hurt.time();
        holder.proxyHurtActive = hurt.time() > 0;
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
        // Dominion's server-side command is the single source of truth for sprint.  The
        // movement sample gates the flag so a delayed entity-data packet cannot select a run
        // animation while the proxy is stationary; YSM then sees the same isSprinting state as
        // a real player and can select both standard run and TACZ tac:run controllers.
        player.setSprinting(!preview && !dead && !NpcCrawlState.isCrawling(npc)
                && movement.walking() && npc.isSprinting());
        player.setShiftKeyDown(false);
        player.setSwimming(false);
        player.stopUsingItem();

        Minecraft minecraft = Minecraft.getInstance();
        Player viewer = minecraft.player;
        Visibility visibility = preview ? Visibility.VISIBLE : visibility(npc, viewer);
        // Hidden NPCs are suppressed before rendering. A partial proxy must report visible so
        // YSM reaches its pipeline; the render-local mixin supplies translucency and alpha.
        player.setCompatVisibility(false, false);
        player.setGlowingTag(!preview && npc.isCurrentlyGlowing());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, npc.getItemBySlot(slot));
        }
        if (!preview) {
            GunCompat.syncClientState(npc, player);
            traceProxyState(holder, npc, player, visibility);
        }
        // The proxy is deliberately not inserted into ClientLevel, so Forge/YSM never sends
        // it the normal per-entity capability tick. Advance that state exactly once for each
        // mirrored NPC tick after every input field has been synchronized. Without this,
        // one-shot animations such as hurt can leave a stale root-bone translation forever.
        if (advancedTick) Ysm265Adapter.advancePlayerAnimation(player);
        if (hurtAnimationEnded && !holder.modelId.isBlank()) {
            // YSM model packs are allowed to translate their root during `attacked`. The
            // synthetic player has no normal world lifecycle, and the captured GPU vertices
            // prove that some packs retain the final translated root after the selector has
            // already left `attacked`. Re-applying the same model here rebuilds only this
            // render proxy's animation state after the complete hurt action has played.
            boolean recovered = Ysm265Adapter.setPlayerModel(player, holder.modelId);
            holder.tweaksNeedReapply = recovered;
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-HURT-RECOVERY] npcId={} tick={} model={} recovered={} hurtDuration={}",
                    npc.getId(), npc.tickCount, holder.modelId, recovered, player.hurtDuration);
        }
    }

    private static void traceProxyState(AnimatedProxy holder, EntityNPCInterface npc,
                                        YsmNpcProxyPlayer player, Visibility visibility) {
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
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-PROXY-TRACE] npcId={} tick={} {}", npc.getId(), npc.tickCount, state);
    }

    private static boolean updateHurtDiagnostic(AnimatedProxy holder, EntityNPCInterface npc, float partialTick) {
        boolean hurt = npc.hurtTime > 0;
        if (hurt && !holder.hurtDiagnosticActive) {
            holder.hurtDiagnosticEvent++;
            holder.hurtDiagnosticUntil = npc.tickCount + 40;
            holder.hurtDiagnosticDeadlineMillis = Util.getMillis() + 5_000L;
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-HURT-DIAG][BEGIN] event={} npcId={} uuid={} tick={} model={} thread={}",
                    holder.hurtDiagnosticEvent, npc.getId(), npc.getUUID(), npc.tickCount,
                    holder.modelId, Thread.currentThread().getName());
            for (String previous : holder.hurtDiagnosticHistory) {
                CustomNpcsYsmCompat.LOGGER.info("[YSM-HURT-DIAG][PRE] event={} npcId={} {}",
                        holder.hurtDiagnosticEvent, npc.getId(), previous);
            }
        }
        if (hurt) holder.hurtDiagnosticUntil = Math.max(holder.hurtDiagnosticUntil, npc.tickCount + 40);
        if (!hurt && holder.hurtDiagnosticActive) {
            CustomNpcsYsmCompat.LOGGER.info("[YSM-HURT-DIAG][HURT-END] event={} npcId={} tick={}",
                    holder.hurtDiagnosticEvent, npc.getId(), npc.tickCount);
        }
        holder.hurtDiagnosticActive = hurt;

        String frame = diagnosticEntityFrame(npc, holder.player, partialTick);
        if (npc.tickCount > holder.hurtDiagnosticUntil || Util.getMillis() > holder.hurtDiagnosticDeadlineMillis) {
            holder.hurtDiagnosticHistory.addLast(frame);
            while (holder.hurtDiagnosticHistory.size() > 8) holder.hurtDiagnosticHistory.removeFirst();
            return false;
        }
        if (holder.lastHurtDiagnosticTick != npc.tickCount) {
            holder.lastHurtDiagnosticTick = npc.tickCount;
            CustomNpcsYsmCompat.LOGGER.info("[YSM-HURT-DIAG][ENTITY] event={} npcId={} {}",
                    holder.hurtDiagnosticEvent, npc.getId(), frame);
            CustomNpcsYsmCompat.LOGGER.info("[YSM-HURT-DIAG][YSM-STATE] event={} npcId={} tick={} {}",
                    holder.hurtDiagnosticEvent, npc.getId(), npc.tickCount,
                    Ysm265Adapter.diagnosticSnapshot(holder.player));
        }
        return true;
    }

    private static String diagnosticEntityFrame(EntityNPCInterface npc, YsmNpcProxyPlayer player, float partialTick) {
        return "tick=" + npc.tickCount + ",partial=" + partialTick
                + ",npcPos=" + npc.getX() + "/" + npc.getY() + "/" + npc.getZ()
                + ",npcOld=" + npc.xo + "/" + npc.yo + "/" + npc.zo
                + ",npcDelta=" + npc.getDeltaMovement() + ",npcBox=" + npc.getBoundingBox()
                + ",npcGround=" + npc.onGround() + ",npcFall=" + npc.fallDistance
                + ",npcCollision=" + npc.horizontalCollision + "/" + npc.verticalCollision
                + ",npcHurt=" + npc.hurtTime + "/" + npc.hurtDuration
                + ",npcHealth=" + npc.getHealth() + "/" + npc.getMaxHealth()
                + ",npcRot=" + npc.getXRot() + "/" + npc.getYRot() + "/" + npc.yBodyRot + "/" + npc.yHeadRot
                + ",proxyPos=" + player.getX() + "/" + player.getY() + "/" + player.getZ()
                + ",proxyOld=" + player.xo + "/" + player.yo + "/" + player.zo
                + ",proxyDelta=" + player.getDeltaMovement() + ",proxyBox=" + player.getBoundingBox()
                + ",proxyGround=" + player.onGround() + ",proxyFall=" + player.fallDistance
                + ",proxyCollision=" + player.horizontalCollision + "/" + player.verticalCollision
                + ",proxyHurt=" + player.hurtTime + "/" + player.hurtDuration
                + ",proxyRot=" + player.getXRot() + "/" + player.getYRot() + "/" + player.yBodyRot + "/" + player.yHeadRot;
    }

    private static void traceRenderMatrix(AnimatedProxy holder, EntityNPCInterface npc, String stage,
                                          PoseStack poseStack, float partialTick, String vertices) {
        if (holder.lastHurtDiagnosticRenderTick != npc.tickCount) {
            holder.lastHurtDiagnosticRenderTick = npc.tickCount;
            holder.hurtDiagnosticRenderStagesThisTick = 0;
        }
        // Two complete contexts (before scale, before YSM, after YSM) are enough to detect
        // alternate render passes while avoiding unbounded logs when the client is paused.
        if (holder.hurtDiagnosticRenderStagesThisTick++ >= 6) return;
        Matrix4f matrix = poseStack.last().pose();
        StringBuilder values = new StringBuilder(180);
        for (int row = 0; row < 4; row++) {
            if (row > 0) values.append(';');
            for (int column = 0; column < 4; column++) {
                if (column > 0) values.append(',');
                values.append(matrix.get(row, column));
            }
        }
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-HURT-DIAG][RENDER] event={} npcId={} tick={} partial={} frame={} stage={} matrix=[{}] {}",
                holder.hurtDiagnosticEvent, npc.getId(), npc.tickCount, partialTick,
                holder.hurtDiagnosticRenderFrame++, stage, values,
                vertices == null ? "" : vertices);
    }

    private static boolean shouldHide(EntityNPCInterface npc) {
        return npc.isKilled() && npc.stats.hideKilledBody && npc.deathTime > 20;
    }

    private static void traceRetreatRender(AnimatedProxy holder, EntityNPCInterface npc,
                                           NpcMovementTracker.Sample movement,
                                           boolean backpedalling, float targetBodyYaw) {
        boolean armedMovement = movement.walking() && GunCompat.active(npc);
        if (!armedMovement && !backpedalling && !holder.wasBackpedalling) return;
        if (npc.tickCount % 5 != 0 && backpedalling == holder.wasBackpedalling) return;
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
        Ysm265Adapter.clearTweakDiagnostics();
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
        private boolean wasBackpedalling;
        private int deathStartedAt = Integer.MIN_VALUE;
        private String lastProxyDebugState = "";
        private int ysmFoodLevel = -1;
        private String modelId = "";
        private YsmTweakProfile appliedTweaks = YsmTweakProfile.EMPTY;
        private boolean tweaksNeedReapply;
        private final Deque<String> hurtDiagnosticHistory = new ArrayDeque<>();
        private boolean hurtDiagnosticActive;
        private boolean proxyHurtActive;
        private final YsmVerticalAnchor verticalAnchor = new YsmVerticalAnchor();
        private int hurtDiagnosticEvent;
        private int hurtDiagnosticUntil = Integer.MIN_VALUE;
        private long hurtDiagnosticDeadlineMillis = Long.MIN_VALUE;
        private int lastHurtDiagnosticTick = Integer.MIN_VALUE;
        private int lastHurtDiagnosticRenderTick = Integer.MIN_VALUE;
        private int hurtDiagnosticRenderStagesThisTick;
        private long hurtDiagnosticRenderFrame;

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
