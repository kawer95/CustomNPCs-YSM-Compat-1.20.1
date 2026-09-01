package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.YsmReloadTimeContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * YSM 2.6.5 animation-player local elapsed-time calculation. Scaling the returned elapsed time
 * keeps the controller's own start timestamp intact; modifying its absolute input clock does not,
 * and allowed the fixed 1.75-second reload clip to finish before a slow TaCZ reload.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OOOO0o0oo0o000oOOooOoOo0", remap = false)
public abstract class YsmAnimationClockMixin {
    @Inject(
            method = "Oo0Oo0o00O00Oo0OOoOOoooo(F)F",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void customnpcsYsmCompat$scaleReloadElapsed(float rawTime,
                                                         CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(YsmReloadTimeContext.scaleElapsed(cir.getReturnValueF()));
    }
}
