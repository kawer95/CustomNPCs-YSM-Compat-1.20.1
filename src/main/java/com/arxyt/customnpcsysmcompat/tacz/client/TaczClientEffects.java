package com.arxyt.customnpcsysmcompat.tacz.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.GunCompat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

/** Client-only bridge for effects normally activated by TaCZ's player hand renderer. */
@OnlyIn(Dist.CLIENT)
public final class TaczClientEffects {
    private static boolean registered;

    private TaczClientEffects() {
    }

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(TaczClientEffects.class);
            CustomNpcsYsmCompat.LOGGER.info("TaCZ CustomNPC client effects bridge enabled");
        }
    }

    public static void beginNpcGunRender() {
        MuzzleFlashRender.isSelf = true;
        ShellRender.isSelf = true;
    }

    public static void endNpcGunRender() {
        MuzzleFlashRender.isSelf = false;
        ShellRender.isSelf = false;
    }

    @SubscribeEvent
    public static void onNpcShoot(GunShootEvent event) {
        if (event.getLogicalSide().isServer()
                || !(event.getShooter() instanceof EntityNPCInterface npc)
                || !GunCompat.active(npc)) {
            return;
        }

        MuzzleFlashRender.onShoot();
        TimelessAPI.getGunDisplay(event.getGunItemStack()).ifPresent(display -> {
            if (display.getShellEjection() == null || display.getGunModel() == null) return;
            var velocity = display.getShellEjection().getRandomVelocity();
            ShellRender shell = display.getGunModel().getShellRender(0);
            if (shell != null) shell.addShell(velocity);
            var lod = display.getLodModel();
            if (lod != null) {
                ShellRender lodShell = lod.getLeft().getShellRender(0);
                if (lodShell != null) lodShell.addShell(velocity);
            }
        });
    }
}
