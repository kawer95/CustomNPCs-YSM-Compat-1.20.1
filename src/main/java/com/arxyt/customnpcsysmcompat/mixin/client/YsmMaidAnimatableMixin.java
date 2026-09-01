package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.Ysm265Adapter;
import com.arxyt.customnpcsysmcompat.client.YsmMaidTweakClient;
import com.arxyt.customnpcsysmcompat.client.YsmReloadRenderScope;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.elfmcys.yesstevemodel.Ooo0OOOOOoooo0oo00OoO0oO", remap = false)
public abstract class YsmMaidAnimatableMixin {
    @Unique
    private String customnpcsYsmCompat$appliedModel = "";
    @Unique
    private YsmTweakProfile customnpcsYsmCompat$appliedProfile = YsmTweakProfile.EMPTY;
    @Unique
    private boolean customnpcsYsmCompat$reloadScopeActive;

    @Shadow
    public abstract IMaid getMaid();

    @Inject(method = "setYsmModel(Ljava/lang/String;Ljava/lang/String;)V", at = @At("RETURN"), remap = false)
    private void customnpcsYsmCompat$restoreMaidTweaks(String modelId, String texture, CallbackInfo ci) {
        EntityMaid maid = getMaid().asStrictMaid();
        if (maid == null) return;
        String normalizedModel = YsmDisplayData.normalizeModelId(modelId);
        YsmTweakProfile profile = YsmMaidTweakClient.profile(maid, normalizedModel);
        if (normalizedModel.equals(customnpcsYsmCompat$appliedModel)
                && profile.equals(customnpcsYsmCompat$appliedProfile)) return;
        Ysm265Adapter.TweakApplyResult result = Ysm265Adapter.applyTweaks(this, normalizedModel, profile);
        if (profile.isEmpty() || result.applied() + result.skipped() >= profile.entries().size()) {
            customnpcsYsmCompat$appliedModel = normalizedModel;
            customnpcsYsmCompat$appliedProfile = profile;
        }
    }

    /* YSM evaluates maid bones inside this concrete hook, before Forge fires RenderLivingEvent.Pre. */
    @Inject(method = "Oo0Oo0o00O00Oo0OOoOOoooo(FZ)V", at = @At("HEAD"),
            remap = false, require = 1)
    private void customnpcsYsmCompat$beginReloadScope(float partialTick, boolean firstPerson,
                                                       CallbackInfo ci) {
        EntityMaid maid = getMaid().asStrictMaid();
        customnpcsYsmCompat$reloadScopeActive = YsmReloadRenderScope.begin(maid);
    }

    @Inject(method = "Oo0Oo0o00O00Oo0OOoOOoooo(FZ)V", at = @At("RETURN"),
            remap = false, require = 1)
    private void customnpcsYsmCompat$endReloadScope(float partialTick, boolean firstPerson,
                                                     CallbackInfo ci) {
        YsmReloadRenderScope.end(customnpcsYsmCompat$reloadScopeActive);
        customnpcsYsmCompat$reloadScopeActive = false;
    }
}
