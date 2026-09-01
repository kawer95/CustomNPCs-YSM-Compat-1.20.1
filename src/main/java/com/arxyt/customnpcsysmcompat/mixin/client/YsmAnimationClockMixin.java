package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.YsmReloadTimeContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * YSM 2.6.5 animation-player clock.  The target is optional and the injection deliberately
 * fails open on later YSM binaries; only our render-proxy scope ever changes the input time.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OOOO0o0oo0o000oOOooOoOo0", remap = false)
public abstract class YsmAnimationClockMixin {
    @ModifyVariable(
            method = "Oo0Oo0o00O00Oo0OOoOOoooo(FLcom/elfmcys/yesstevemodel/O0Oooo00oOo00O00OoOOOooO;Z)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float customnpcsYsmCompat$scaleReloadClock(float rawTime) {
        return YsmReloadTimeContext.transform(rawTime);
    }
}
