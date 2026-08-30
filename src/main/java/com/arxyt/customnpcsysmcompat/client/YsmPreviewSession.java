package com.arxyt.customnpcsysmcompat.client;

import com.mojang.authlib.GameProfile;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmTweakEntry;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A GUI-owned YSM player that never mutates or renders the edited world NPC. */
public final class YsmPreviewSession implements AutoCloseable {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("customnpcs-ysm:gui-preview".getBytes()), "YsmPreview");

    private YsmDisplayData pending;
    private RemotePlayer player;
    private String modelId = "";
    private YsmTweakProfile applied = YsmTweakProfile.EMPTY;
    private int ticks;
    private boolean closed;

    public YsmPreviewSession(YsmDisplayData initial) {
        pending = initial;
        tick();
    }

    public void update(YsmDisplayData data) {
        if (!closed) pending = data;
    }

    /** Called exactly once from the owning screen's GUI tick. */
    public void tick() {
        if (closed) return;
        ticks++;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || pending == null || pending.modelId().isBlank()
                || !Ysm265Adapter.hasModel(pending.modelId())) {
            discardPlayer();
            return;
        }

        YsmTweakProfile desired = pending.tweaksFor(pending.modelId());
        boolean modelChanged = player == null || player.level() != level || !pending.modelId().equals(modelId);
        boolean removedOverride = !modelChanged && removedIdentity(applied, desired);
        if (modelChanged || removedOverride) {
            replacePlayer(level, pending.modelId(), desired);
        } else if (!desired.equals(applied)) {
            Ysm265Adapter.applyPlayerTweaks(player, modelId, desired);
            applied = desired;
        }

        if (player != null) {
            prepareIdle(player);
            player.tickCount = ticks;
            Ysm265Adapter.advancePlayerAnimation(player);
        }
    }

    public void render(GuiGraphics graphics, int x, int bottom, int scale, float mouseX, float mouseY) {
        if (closed || player == null) return;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, bottom, scale, mouseX, mouseY, player);
    }

    private void replacePlayer(ClientLevel level, String nextModel, YsmTweakProfile desired) {
        RemotePlayer candidate = new RemotePlayer(level, PROFILE);
        prepareIdle(candidate);
        candidate.tickCount = ticks;
        if (!Ysm265Adapter.setPlayerModel(candidate, nextModel)) return;
        Ysm265Adapter.applyPlayerTweaks(candidate, nextModel, desired);

        discardPlayer();
        player = candidate;
        modelId = nextModel;
        applied = desired;
        AnimatedNpcRenderBridge.registerProxyPlayer(candidate);
    }

    private static void prepareIdle(RemotePlayer player) {
        player.setPos(0.0D, 0.0D, 0.0D);
        player.xo = player.getX();
        player.yo = player.getY();
        player.zo = player.getZ();
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setPose(Pose.STANDING);
        player.setShiftKeyDown(false);
        player.setSwimming(false);
        player.setSprinting(false);
        player.setOnGround(true);
        player.hurtTime = 0;
        player.hurtDuration = 0;
        player.deathTime = 0;
        player.swinging = false;
        player.stopUsingItem();
        for (EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
    }

    static boolean removedIdentity(YsmTweakProfile previous, YsmTweakProfile next) {
        if (previous.isEmpty()) return false;
        Set<String> identities = new HashSet<>();
        for (YsmTweakEntry entry : next.entries()) identities.add(entry.identity());
        for (YsmTweakEntry entry : previous.entries()) {
            if (!identities.contains(entry.identity())) return true;
        }
        return false;
    }

    private void discardPlayer() {
        if (player != null) AnimatedNpcRenderBridge.unregisterProxyPlayer(player);
        player = null;
        modelId = "";
        applied = YsmTweakProfile.EMPTY;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        discardPlayer();
    }
}
