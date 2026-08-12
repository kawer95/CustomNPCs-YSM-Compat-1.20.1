package com.arxyt.customnpcsysmcompat.tacz;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.GunCompatFacade;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class Tacz115Compat implements GunCompatFacade {
    private final Map<EntityNPCInterface, String> equippedGuns =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<EntityNPCInterface, ShootResult> lastResults =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public Tacz115Compat() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isGun(ItemStack stack) {
        return IGun.getIGunOrNull(stack) != null;
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
        ItemStack gunStack = shooter.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        Optional<CommonGunIndex> indexOptional = gunIndex(gunStack);
        if (gun == null || indexOptional.isEmpty()) return Action.waitFor(100);

        CommonGunIndex index = indexOptional.get();
        GunData data = index.getGunData();
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        String gunKey = String.valueOf(gun.getGunId(gunStack));
        String previous = equippedGuns.put(shooter, gunKey);
        if (!gunKey.equals(previous)) {
            operator.draw(shooter::getMainHandItem);
            return Action.waitFor(seconds(data.getDrawTime()) + 2);
        }
        boolean sniper = index.getType().equalsIgnoreCase(GunTabType.SNIPER.name());
        float distance = shooter.distanceTo(target);
        float aimBoundary = Math.max(1.0F, shooter.stats.ranged.getRange());

        if (sniper && !operator.getSynIsAiming()) {
            operator.aim(true);
            return Action.waitFor(seconds(data.getAimTime()) + 2);
        }
        if (!sniper) {
            boolean shouldAim = distance > aimBoundary;
            if (operator.getSynIsAiming() != shouldAim) {
                operator.aim(shouldAim);
                return Action.waitFor(seconds(data.getAimTime()) + 2);
            }
        }

        double x = target.getX() - shooter.getX();
        double y = target.getEyeY() - shooter.getEyeY();
        double z = target.getZ() - shooter.getZ();
        float yaw = (float) -Math.toDegrees(Math.atan2(x, z));
        float pitch = (float) -Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
        ShootResult result = operator.shoot(() -> pitch, () -> yaw);
        traceResult(shooter, gunStack, gun, operator, result);

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
    public void stop(EntityNPCInterface shooter) {
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        if (operator.getSynIsAiming()) operator.aim(false);
        // Goal arbitration and small range changes are transient for CustomNPCs. Cancelling
        // here repeatedly aborted TaCZ's reload state machine after the first magazine.
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

    @SubscribeEvent
    public void protectFactionRelations(EntityHurtByGunEvent.Pre event) {
        if (shouldCancel(event.getAttacker(), event.getHurtEntity())) {
            event.setCanceled(true);
        }
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
            if (hurt == shooter.getTarget()) return false;
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
}
