package com.arxyt.customnpcsysmcompat.tacz;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.DominionCommandBridge;
import com.arxyt.customnpcsysmcompat.DominionCombatBalance;
import com.arxyt.customnpcsysmcompat.GunCompatFacade;
import com.arxyt.customnpcsysmcompat.GunCompat;
import com.arxyt.customnpcsysmcompat.NpcCrawlState;
import com.arxyt.customnpcsysmcompat.NpcGunAimLock;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * TaCZ 1.1.5+ adapter that delegates all weapon state to TaCZ while exposing a
 * small, fail-safe gun-control surface to YSM CustomNPC goals.
 *
 * <p>The adapter is instantiated only when TaCZ is present. Dominion Sword
 * settings are read through a local optional bridge, so a missing or failed
 * Dominion installation leaves TaCZ shooting unchanged.</p>
 */
public final class Tacz115Compat implements GunCompatFacade {
    private final Map<EntityNPCInterface, String> equippedGuns =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<EntityNPCInterface, ShootResult> lastResults =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<EntityNPCInterface, Integer> lastProneAimTraceTick =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<EntityNPCInterface, Integer> lastCrawlStateTraceTick =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<EntityNPCInterface, Integer> lastProneHitTraceTick =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<EntityNPCInterface, WatchTriggerState> watchTriggers =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    public Tacz115Compat() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isGun(ItemStack stack) {
        return IGun.getIGunOrNull(stack) != null;
    }

    @Override
    public boolean isMachineGun(ItemStack stack) {
        return gunIndex(stack)
                .map(index -> GunTabType.MG.name().equalsIgnoreCase(index.getType()))
                .orElse(false);
    }

    @Override
    public RangeClass rangeClass(ItemStack stack) {
        return gunIndex(stack).map(index -> {
            String type = index.getType().toLowerCase(Locale.ROOT);
            if (type.equals(GunTabType.SNIPER.name().toLowerCase(Locale.ROOT))) return RangeClass.LONG;
            if (type.equals(GunTabType.PISTOL.name().toLowerCase(Locale.ROOT))
                    || type.equals(GunTabType.SHOTGUN.name().toLowerCase(Locale.ROOT))
                    || type.equals(GunTabType.SMG.name().toLowerCase(Locale.ROOT))) return RangeClass.NEAR;
            return RangeClass.MEDIUM;
        }).orElse(RangeClass.MEDIUM);
    }

    @Override
    public Action operate(EntityNPCInterface shooter, LivingEntity target) {
        watchTriggers.remove(shooter);
        return operate(shooter, target, false);
    }

    @Override
    public Action operateWatch(EntityNPCInterface shooter, LivingEntity target) {
        return operate(shooter, target, true);
    }

    private Action operate(EntityNPCInterface shooter, LivingEntity target, boolean watchFire) {
        ItemStack gunStack = shooter.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        Optional<CommonGunIndex> indexOptional = gunIndex(gunStack);
        if (gun == null || indexOptional.isEmpty()) return Action.waitFor(100);

        CommonGunIndex index = indexOptional.get();
        GunData data = index.getGunData();
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        prepareImmediateFire(shooter, operator);
        String gunKey = String.valueOf(gun.getGunId(gunStack));
        String previous = equippedGuns.put(shooter, gunKey);
        if (!gunKey.equals(previous)) {
            operator.draw(shooter::getMainHandItem);
            return Action.waitFor(seconds(data.getDrawTime()) + 2);
        }
        if (needsAimForTarget(operator.getSynIsAiming())) {
            operator.aim(true);
            return Action.waitFor(seconds(data.getAimTime()) + 2);
        }

        double x = target.getX() - shooter.getX();
        double z = target.getZ() - shooter.getZ();
        NpcGunAimLock.AimSolution lockedAim = NpcGunAimLock.solutionFor(shooter, target);
        if (!lockedAim.valid()) return Action.waitFor(1);
        float yaw = lockedAim.yaw();
        float pitch = lockedAim.pitch();
        int accuracyRoll = shooter.getRandom().nextInt(100);
        double magnitudeRoll = shooter.getRandom().nextDouble();
        boolean positiveError = shooter.getRandom().nextBoolean();
        DominionCombatBalance.Settings balance = DominionCombatBalance.settings();
        boolean machineGun = GunTabType.MG.name().equalsIgnoreCase(index.getType());
        boolean sniperRifle = GunTabType.SNIPER.name().equalsIgnoreCase(index.getType());
        int effectiveAccuracy = effectiveAccuracy(shooter.stats.ranged.getAccuracy(),
                balance.available() && balance.customNpcStandingMachineGunAccuracyPenalty(), machineGun,
                sniperRifle, operator.getDataHolder().isCrawling);
        float aimError = aimErrorDegrees(effectiveAccuracy, target.getBbWidth(),
                Math.sqrt(x * x + z * z), accuracyRoll, magnitudeRoll, positiveError);
        float adjustedYaw = yaw + aimError;
        ShootResult result = watchFire && DominionCommandBridge.watchContinuousFireRequested(shooter) && machineGun
                ? heldTriggerShoot(shooter, operator, gun, gunStack, data, pitch, adjustedYaw)
                : operator.shoot(() -> pitch, () -> adjustedYaw);
        if (result == ShootResult.IS_SPRINTING) {
            // If another hook restored a sprint transition during this tick, retry after
            // clearing only that gate. All mechanical weapon gates remain authoritative.
            prepareImmediateFire(shooter, operator);
            result = watchFire && DominionCommandBridge.watchContinuousFireRequested(shooter) && machineGun
                    ? heldTriggerShoot(shooter, operator, gun, gunStack, data, pitch, adjustedYaw)
                    : operator.shoot(() -> pitch, () -> adjustedYaw);
        }
        traceResult(shooter, gunStack, gun, operator, result);
        traceProneAim(shooter, target, gun, operator, result, yaw, pitch, adjustedYaw,
                aimError, accuracyRoll);

        return switch (result) {
            case SUCCESS -> new Action(successDelay(gun, gunStack, shooter), true);
            case NOT_DRAW -> {
                operator.draw(shooter::getMainHandItem);
                yield Action.waitFor(seconds(data.getDrawTime()) + 2);
            }
            case NEED_BOLT -> {
                operator.bolt();
                yield Action.waitFor(seconds(data.getBoltActionTime()) + 2);
            }
            case NO_AMMO -> {
                operator.reload();
                float reload = data.getReloadData() == null || data.getReloadData().getCooldown() == null
                        ? 1.0F : data.getReloadData().getCooldown().getEmptyTime();
                yield Action.waitFor(seconds(reload) + 2);
            }
            case COOL_DOWN, IS_RELOADING, IS_DRAWING, IS_BOLTING, IS_MELEE, IS_SPRINTING -> Action.waitFor(1);
            default -> Action.waitFor(20);
        };
    }

    @Override
    public Action continueWatchFire(EntityNPCInterface shooter) {
        WatchTriggerState state = watchTriggers.get(shooter);
        ItemStack stack = shooter.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        Optional<CommonGunIndex> index = gunIndex(stack);
        if (state == null || gun == null || index.isEmpty() || !isMachineGun(stack)
                || !DominionCommandBridge.watchContinuousFireRequested(shooter)) {
            watchTriggers.remove(shooter);
            return Action.waitFor(1);
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        prepareImmediateFire(shooter, operator);
        ShootResult result = heldTriggerShoot(shooter, operator, gun, stack, index.get().getGunData(), state.pitch, state.yaw);
        return new Action(1, result == ShootResult.SUCCESS);
    }

    private ShootResult heldTriggerShoot(EntityNPCInterface shooter, IGunOperator operator, IGun gun,
                                         ItemStack stack, GunData data, float pitch, float yaw) {
        String key = String.valueOf(gun.getGunId(stack));
        WatchTriggerState state = watchTriggers.computeIfAbsent(shooter, ignored -> new WatchTriggerState());
        if (!key.equals(state.gunKey)) { state.gunKey = key; state.chargeProgress = 0.0F; }
        state.pitch = pitch;
        state.yaw = yaw;
        ChargeView charge = chargeData(data, gun.getFireMode(stack));
        if (charge == null) return operator.shoot(() -> pitch, () -> yaw);
        state.chargeProgress = Math.min(charge.maxCharge,
                state.chargeProgress + Math.max(0.0F, charge.increasePerTick));
        float threshold = charge.autoOrDelay() ? charge.maxCharge : charge.fireThreshold;
        if (state.chargeProgress + 0.001F < threshold) return ShootResult.COOL_DOWN;
        long timestamp = System.currentTimeMillis() - operator.getDataHolder().baseTimestamp;
        ShootResult result = chargedShoot(operator, () -> pitch, () -> yaw, timestamp, state.chargeProgress);
        if (result == ShootResult.SUCCESS) {
            state.chargeProgress = charge.delay() ? 0.0F
                    : Math.max(0.0F, state.chargeProgress - charge.decreaseOnFire);
        } else if (result == ShootResult.NO_AMMO || result == ShootResult.IS_RELOADING
                || result == ShootResult.IS_DRAWING || result == ShootResult.IS_BOLTING) {
            state.chargeProgress = 0.0F;
        }
        return result;
    }

    /** Preserves sprint for movement animation, but makes a requested shot leave it immediately. */
    private static void prepareImmediateFire(EntityNPCInterface shooter, IGunOperator operator) {
        shooter.setSprinting(false);
        operator.getDataHolder().sprintTimeS = 0.0F;
        operator.getDataHolder().sprintTimestamp = System.currentTimeMillis();
    }

    @Override
    public void stop(EntityNPCInterface shooter) {
        stop(shooter, false);
    }

    @Override
    public void stop(EntityNPCInterface shooter, boolean forceExitAim) {
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        boolean queuedAttack = DominionCommandBridge.hasQueuedAttack(shooter);
        if (shouldExitAim(operator.getSynIsAiming(), queuedAttack, forceExitAim)) operator.aim(false);
        if (forceExitAim || !DominionCommandBridge.watchContinuousFireRequested(shooter)) watchTriggers.remove(shooter);
        // Goal arbitration and small range changes are transient for CustomNPCs. Cancelling
        // here repeatedly aborted TaCZ's reload state machine after the first magazine.
    }

    /**
     * Bridges CNPC's physical {@code CRAWL} action to TaCZ's authoritative
     * crawl request.  TaCZ owns the final decision: its tick hook cancels the
     * request for unsupported guns, swimming, jumping, passengers and entities
     * that are not on the ground.
     */
    @Override
    public void syncCrawlState(EntityNPCInterface shooter) {
        ItemStack gunStack = shooter.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        // Do not re-request a state that TaCZ has already rejected.  In particular,
        // can_crawl=false weapons would otherwise be reset by TaCZ at every tick tail
        // and requested again at every following tick head.
        boolean activeGun = GunCompat.active(shooter);
        boolean gunCanCrawl = gun != null && gun.isCanCrawl(gunStack);
        boolean onGround = shooter.onGround();
        boolean passenger = shooter.isPassenger();
        boolean swimming = shooter.isSwimming();
        boolean spectator = shooter.isSpectator();
        boolean canRequest = activeGun && gunCanCrawl && onGround && !passenger && !swimming && !spectator;
        boolean requested = NpcCrawlState.requestsTaczCrawl(shooter.currentAnimation, canRequest);
        boolean current = operator.getDataHolder().isCrawling;
        if (current == requested) return;

        traceCrawlStateMismatch(shooter, current, requested, activeGun, gunCanCrawl,
                onGround, passenger, swimming, spectator);
        operator.crawl(requested);
    }

    private static final class WatchTriggerState {
        private String gunKey = "";
        private float pitch;
        private float yaw;
        private float chargeProgress;
    }

    /** TaCZ 1.1.5 has the four-argument shoot API but no public ChargeData type. */
    private static ChargeView chargeData(GunData data, FireMode mode) {
        try {
            Object charge = data.getClass().getMethod("getChargeData", FireMode.class).invoke(data, mode);
            if (charge == null) return null;
            Class<?> type = charge.getClass();
            return new ChargeView(number(type, charge, "getMaxCharge"), number(type, charge, "getIncreasePerTick"),
                    number(type, charge, "getFireThreshold"), number(type, charge, "getDecreaseOnFire"),
                    String.valueOf(type.getMethod("getChargeType").invoke(charge)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
    private static float number(Class<?> type, Object value, String method) throws ReflectiveOperationException {
        return ((Number) type.getMethod(method).invoke(value)).floatValue();
    }
    private static ShootResult chargedShoot(IGunOperator operator, java.util.function.Supplier<Float> pitch,
                                             java.util.function.Supplier<Float> yaw, long timestamp, float progress) {
        try {
            Object result = operator.getClass().getMethod("shoot", java.util.function.Supplier.class,
                    java.util.function.Supplier.class, long.class, float.class)
                    .invoke(operator, pitch, yaw, timestamp, progress);
            if (result instanceof ShootResult shootResult) return shootResult;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return operator.shoot(pitch, yaw, timestamp);
    }
    private record ChargeView(float maxCharge, float increasePerTick, float fireThreshold,
                              float decreaseOnFire, String type) {
        boolean autoOrDelay() { return "AUTO".equals(type) || "DELAY".equals(type); }
        boolean delay() { return "DELAY".equals(type); }
    }

    @Override
    public void syncClientState(LivingEntity source, LivingEntity renderProxy) {
        if (!isGun(source.getMainHandItem())) return;
        ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(renderProxy,
                ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.getValue(source));
        ModSyncedEntityData.MELEE_COOL_DOWN_KEY.setValue(renderProxy,
                ModSyncedEntityData.MELEE_COOL_DOWN_KEY.getValue(source));
        ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(renderProxy,
                ModSyncedEntityData.DRAW_COOL_DOWN_KEY.getValue(source));
        ModSyncedEntityData.IS_BOLTING_KEY.setValue(renderProxy,
                ModSyncedEntityData.IS_BOLTING_KEY.getValue(source));
        ModSyncedEntityData.RELOAD_STATE_KEY.setValue(renderProxy,
                ModSyncedEntityData.RELOAD_STATE_KEY.getValue(source));
        ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(renderProxy,
                ModSyncedEntityData.AIMING_PROGRESS_KEY.getValue(source));
        ModSyncedEntityData.IS_AIMING_KEY.setValue(renderProxy,
                ModSyncedEntityData.IS_AIMING_KEY.getValue(source));
        ModSyncedEntityData.SPRINT_TIME_KEY.setValue(renderProxy,
                ModSyncedEntityData.SPRINT_TIME_KEY.getValue(source));
    }

    private static Optional<CommonGunIndex> gunIndex(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return Optional.empty();
        ResourceLocation id = gun.getGunId(stack);
        return id == null ? Optional.empty() : TimelessAPI.getCommonGunIndex(id);
    }

    private static int seconds(float seconds) {
        return Math.max(1, Math.round(seconds * 20.0F));
    }

    private static int successDelay(IGun gun, ItemStack stack, EntityNPCInterface shooter) {
        FireMode mode = gun.getFireMode(stack);
        if (mode == FireMode.SEMI || mode == FireMode.BURST) {
            return 10 + shooter.getRandom().nextInt(5);
        }
        return 2;
    }

    /**
     * Applies CustomNPCs' own ranged accuracy as an exact-aim probability.
     * Accurate shots receive the target-centre yaw, while the remaining shots
     * deliberately aim outside the target. TaCZ weapon spread remains active
     * for both paths and is intentionally not counted as CustomNPC accuracy.
     */
    /** Returns zero for a precise target lock; nonzero values intentionally miss the target silhouette. */
    static float aimErrorDegrees(int accuracy, double targetWidth, double horizontalDistance,
                                 int accuracyRoll, double magnitudeRoll, boolean positive) {
        if (isExactAimShot(accuracy, accuracyRoll)) return 0.0F;
        double safeDistance = Math.max(0.1D, horizontalDistance);
        double safeWidth = Math.max(0.35D, targetWidth * 0.65D);
        double randomMagnitude = Double.isFinite(magnitudeRoll) ? Mth.clamp(magnitudeRoll, 0.0D, 1.0D) : 0.0D;
        float error = (float) (Math.toDegrees(Math.atan2(safeWidth, safeDistance)) + 2.0D
                + randomMagnitude * 3.0D);
        return positive ? error : -error;
    }

    static boolean isExactAimShot(int accuracy, int accuracyRoll) {
        int safeAccuracy = Math.max(0, Math.min(100, accuracy));
        return Math.floorMod(accuracyRoll, 100) < safeAccuracy;
    }

    /** Every active NPC gun engagement uses TaCZ ADS until its combat goal stops. */
    static boolean needsAimForTarget(boolean isAiming) {
        return !isAiming;
    }

    /** Keeps ADS only while Dominion's non-empty attack queue transfers this NPC to another target. */
    static boolean shouldExitAim(boolean isAiming, boolean queuedAttack, boolean forceExitAim) {
        return isAiming && (forceExitAim || !queuedAttack);
    }

    private void traceResult(EntityNPCInterface shooter, ItemStack gunStack, IGun gun,
                             IGunOperator operator, ShootResult result) {
        ShootResult previous = lastResults.put(shooter, result);
        if (previous == result && result == ShootResult.SUCCESS) return;

        StringBuilder inventory = new StringBuilder();
        final int[] compatible = {0};
        Optional<net.minecraftforge.items.IItemHandler> capability =
                shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve();
        capability.ifPresent(handler -> {
            inventory.append("slots=").append(handler.getSlots()).append('[');
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                boolean matches = false;
                IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
                if (ammo != null) matches = ammo.isAmmoOfGun(gunStack, stack);
                IAmmoBox box = stack.getItem() instanceof IAmmoBox value ? value : null;
                if (box != null) matches |= box.isAmmoBoxOfGun(gunStack, stack);
                if (matches) compatible[0]++;
                inventory.append(slot).append(':').append(stack.getItem()).append('x')
                        .append(stack.getCount()).append(matches ? "*" : "").append(',');
            }
            inventory.append(']');
        });
        if (capability.isEmpty()) inventory.append("capability=missing");

        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-GUN-TRACE] npcId={} tick={} result={} previous={} gun={} magazine={} " +
                        "needCheckAmmo={} compatibleSlots={} reloadState={} inventory={}",
                shooter.getId(), shooter.tickCount, result, previous, gun.getGunId(gunStack),
                gun.getCurrentAmmoCount(gunStack), operator.needCheckAmmo(), compatible[0],
                operator.getSynReloadState().getStateType(), inventory);
    }

    /** Weapon/stance multipliers change only exact-lock probability; TaCZ spread remains untouched. */
    static int effectiveAccuracy(int baseAccuracy, boolean penaltyEnabled, boolean machineGun,
                                 boolean sniperRifle, boolean crawling) {
        int safeAccuracy = Math.max(0, Math.min(100, baseAccuracy));
        if (sniperRifle) {
            float multiplier = crawling ? 1.35F : 0.80F;
            return Math.max(0, Math.min(100, Math.round(safeAccuracy * multiplier)));
        }
        if (!penaltyEnabled || !machineGun || crawling) return safeAccuracy;
        return Math.max(0, Math.min(100, Math.round(safeAccuracy * 0.5F)));
    }

    /**
     * Limited diagnostic for reports that prone machine-gun fire misses large entities.
     * It proves whether our pre-TaCZ aim ray intersects the target collision box; it does
     * not claim to simulate TaCZ muzzle offsets, weapon spread, gravity, or block collision.
     */
    private void traceProneAim(EntityNPCInterface shooter, LivingEntity target, IGun gun,
                               IGunOperator operator, ShootResult result, float exactYaw,
                               float eyePitch, float adjustedYaw, float aimError, int accuracyRoll) {
        if (!NpcCrawlState.isCrawling(shooter) || result != ShootResult.SUCCESS) return;
        Integer previousTick = lastProneAimTraceTick.get(shooter);
        if (previousTick != null && shooter.tickCount - previousTick < 10) return;
        lastProneAimTraceTick.put(shooter, shooter.tickCount);

        Vec3 origin = shooter.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 boxCenter = box.getCenter();
        double dx = boxCenter.x - origin.x;
        double dy = boxCenter.y - origin.y;
        double dz = boxCenter.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float centerPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        double rayLength = Math.max(1.0D, origin.distanceTo(boxCenter)
                + Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 2.0D + 1.0D);
        boolean exactRayHits = rayHits(box, origin, eyePitch, exactYaw, rayLength);
        boolean adjustedRayHits = rayHits(box, origin, eyePitch, adjustedYaw, rayLength);

        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-PRONE-AIM-TRACE] npcId={} tick={} gun={} targetId={} targetClass={} result={} " +
                        "nativeCrawl={} taczCrawl={} ads={} accuracy={} roll={} exactYaw={} adjustedYaw={} " +
                        "yawError={} eyePitch={} centerPitch={} eyeY={} origin={} targetEyeY={} box={} " +
                        "boxSize=({},{},{}) distance={} lineOfSight={} exactRayHits={} adjustedRayHits={} " +
                        "note=ray_uses_npc_eye_only",
                shooter.getId(), shooter.tickCount, gun.getGunId(shooter.getMainHandItem()), target.getId(),
                target.getClass().getName(), result, NpcCrawlState.isCrawling(shooter),
                operator.getDataHolder().isCrawling, operator.getSynIsAiming(), shooter.stats.ranged.getAccuracy(),
                accuracyRoll, decimal(exactYaw), decimal(adjustedYaw), decimal(aimError), decimal(eyePitch),
                decimal(centerPitch), decimal(shooter.getEyeY()), vector(origin), decimal(target.getEyeY()),
                box, decimal(box.getXsize()), decimal(box.getYsize()), decimal(box.getZsize()),
                decimal(origin.distanceTo(boxCenter)), shooter.hasLineOfSight(target), exactRayHits, adjustedRayHits);
    }

    private static boolean rayHits(AABB box, Vec3 origin, float pitch, float yaw, double length) {
        Vec3 end = origin.add(Vec3.directionFromRotation(pitch, yaw).scale(Math.max(1.0D, length)));
        return box.clip(origin, end).isPresent();
    }

    private static String vector(Vec3 value) {
        return "(" + decimal(value.x) + "," + decimal(value.y) + "," + decimal(value.z) + ")";
    }

    private static String decimal(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "nan";
    }

    /** Logs a throttled reason whenever the visible CNPC Crawl and TaCZ crawl states disagree. */
    private void traceCrawlStateMismatch(EntityNPCInterface shooter, boolean current, boolean requested,
                                         boolean activeGun, boolean gunCanCrawl, boolean onGround,
                                         boolean passenger, boolean swimming, boolean spectator) {
        Integer previousTick = lastCrawlStateTraceTick.get(shooter);
        if (previousTick != null && shooter.tickCount - previousTick < 10) return;
        lastCrawlStateTraceTick.put(shooter, shooter.tickCount);
        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-CRAWL-STATE-TRACE] npcId={} tick={} nativeCrawl={} taczCrawlBefore={} requested={} " +
                        "activeGun={} gunCanCrawl={} onGround={} passenger={} swimming={} spectator={} " +
                        "pose={} delta=({},{},{})",
                shooter.getId(), shooter.tickCount, NpcCrawlState.isCrawling(shooter), current, requested,
                activeGun, gunCanCrawl, onGround, passenger, swimming, spectator, shooter.getPose(),
                decimal(shooter.getDeltaMovement().x), decimal(shooter.getDeltaMovement().y),
                decimal(shooter.getDeltaMovement().z));
    }

    @SubscribeEvent
    public void protectFactionRelations(EntityHurtByGunEvent.Pre event) {
        boolean canceled = shouldCancel(event.getAttacker(), event.getHurtEntity());
        traceProneGunHit(event, canceled);
        if (canceled) {
            event.setCanceled(true);
        }
    }

    /**
     * A real TaCZ entity-hit trace, complementary to the pre-shot ray diagnostic. It is
     * deliberately throttled so a sustained machine-gun burst remains readable.
     */
    private void traceProneGunHit(EntityHurtByGunEvent.Pre event, boolean canceled) {
        if (!(event.getAttacker() instanceof EntityNPCInterface shooter)
                || !NpcCrawlState.isCrawling(shooter) || event.getLogicalSide().isClient()) return;
        Integer previousTick = lastProneHitTraceTick.get(shooter);
        if (previousTick != null && shooter.tickCount - previousTick < 5) return;
        lastProneHitTraceTick.put(shooter, shooter.tickCount);
        Entity target = event.getHurtEntity();
        Entity bullet = event.getBullet();
        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-PRONE-HIT-TRACE] npcId={} tick={} gun={} targetId={} targetClass={} amount={} " +
                        "baseAmount={} headshot={} targetHealth={} taczCrawl={} nativeCrawl={} canceledByFaction={} " +
                        "bulletId={} bulletPos={} targetPos={}",
                shooter.getId(), shooter.tickCount, event.getGunId(), target == null ? -1 : target.getId(),
                target == null ? "null" : target.getClass().getName(), decimal(event.getAmount()),
                decimal(event.getBaseAmount()), event.isHeadShot(),
                target instanceof LivingEntity living ? decimal(living.getHealth()) : "n/a",
                IGunOperator.fromLivingEntity(shooter).getDataHolder().isCrawling,
                NpcCrawlState.isCrawling(shooter), canceled, bullet == null ? -1 : bullet.getId(),
                bullet == null ? "null" : vector(bullet.position()),
                target == null ? "null" : vector(target.position()));
    }

    @SubscribeEvent
    public void protectFromGunExplosions(ExplosionEvent.Detonate event) {
        if (!(event.getExplosion().getDirectSourceEntity() instanceof EntityKineticBullet bullet)) return;
        Entity owner = bullet.getOwner();
        if (!(owner instanceof LivingEntity attacker)) return;
        event.getAffectedEntities().removeIf(target -> shouldCancel(attacker, target));
    }

    private static boolean shouldCancel(LivingEntity attacker, Entity hurt) {
        if (attacker instanceof EntityNPCInterface shooter &&
                com.arxyt.customnpcsysmcompat.GunCompat.active(shooter)) {
            if (isCurrentGunTarget(shooter, hurt)) return false;
            if (hurt instanceof Player player) return !shooter.faction.isAggressiveToPlayer(player);
            if (hurt instanceof EntityNPCInterface npc) return !shooter.faction.isAggressiveToNpc(npc);
            return true;
        }
        if (hurt instanceof EntityNPCInterface npc
                && com.arxyt.customnpcsysmcompat.GunCompat.active(npc)) {
            return npc.isAlliedTo(attacker);
        }
        return false;
    }

    /**
     * A number of Forge bosses expose damageable hit boxes as {@link PartEntity} instances.
     * TaCZ correctly reports the part as the hurt entity, while the commanded CNPC target is
     * its parent. Treat only direct parts of that exact current target as authorized; this
     * preserves the faction-safety rule for every unrelated multipart entity.
     */
    private static boolean isCurrentGunTarget(EntityNPCInterface shooter, Entity hurt) {
        if (shooter == null || hurt == null) return false;
        Entity target = shooter.getTarget();
        return hurt == target || hurt instanceof PartEntity<?> part && part.getParent() == target;
    }
}
