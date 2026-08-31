package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.YsmPlayerTweakPersistence;
import com.arxyt.customnpcsysmcompat.client.YsmMaidTweakClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Observes only YSM's common config-form expression entry point; it does not alter the UI. */
@Mixin(targets = "com.elfmcys.yesstevemodel.oooOO0o0ooo0O000Oo0ooOoo", remap = false)
public abstract class YsmAnimationRouletteMixin {
    @Inject(method = "Oo0Oo0o00O00Oo0OOoOOoooo(Ljava/lang/String;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), remap = false)
    private void customnpcsYsmCompat$captureConfigForm(String expression, Consumer<String> feedback,
                                                        CallbackInfo ci) {
        if (!YsmMaidTweakClient.captureScreenExpression(expression)) {
            YsmPlayerTweakPersistence.captureRouletteExpression(expression);
        }
    }
}
