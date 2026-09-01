package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.YsmReloadRenderScope;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wraps the complete YSM bone-evaluation call for YSM CNPC proxies and YSM maids. */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo", remap = false)
public abstract class YsmAnimatableReloadScopeMixin {
    @Unique
    private boolean customnpcsYsmCompat$reloadScopeActive;

    @Shadow
    public abstract Entity OO00OOOOo0Ooo0oo0o0Oo0OO();

    @Inject(
            method = "o0OOooo0o0OO00OoOOOo0o0O(FZ)Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;",
            at = @At("HEAD"), remap = false, require = 1)
    private void customnpcsYsmCompat$beginReloadScope(float partialTick, boolean firstPerson,
                                                       CallbackInfoReturnable<Object> cir) {
        customnpcsYsmCompat$reloadScopeActive =
                YsmReloadRenderScope.begin(OO00OOOOo0Ooo0oo0o0Oo0OO());
    }

    @Inject(
            method = "o0OOooo0o0OO00OoOOOo0o0O(FZ)Lcom/elfmcys/yesstevemodel/OO00O0o0OooOOOo00OO00o00;",
            at = @At("RETURN"), remap = false, require = 1)
    private void customnpcsYsmCompat$endReloadScope(float partialTick, boolean firstPerson,
                                                     CallbackInfoReturnable<Object> cir) {
        YsmReloadRenderScope.end(customnpcsYsmCompat$reloadScopeActive);
        customnpcsYsmCompat$reloadScopeActive = false;
    }
}
