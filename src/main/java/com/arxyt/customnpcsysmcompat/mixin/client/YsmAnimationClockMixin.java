package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.YsmReloadRenderScope;
import com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * YSM 2.6.5 animation controller. Its public update receives YSM's monotonic animation clock.
 * Feed it a continuous virtual clock whose per-frame delta is scaled during TaCZ reloads. Scaling
 * the absolute value itself causes discontinuities, while intercepting the local elapsed helper is
 * too late for YSM's asynchronous animation pipeline.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OOOO0o0oo0o000oOOooOoOo0", remap = false)
public abstract class YsmAnimationClockMixin {
    @Shadow @Final
    private o0000OoOooO0oo0o0oooo0Oo<?> oOOOo0OOO0ooooo0O00OO0o0;

    @ModifyVariable(
            method = "Oo0Oo0o00O00Oo0OOoOOoooo(FLcom/elfmcys/yesstevemodel/O0Oooo00oOo00O00OoOOOooO;Z)V",
            at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 1)
    private float customnpcsYsmCompat$scaleReloadClock(float rawTime) {
        return YsmReloadRenderScope.adjustAnimationTime(
                oOOOo0OOO0ooooo0O00OO0o0.OO00OOOOo0Ooo0oo0o0Oo0OO(), rawTime);
    }
}
