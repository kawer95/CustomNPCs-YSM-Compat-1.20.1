package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.api.IYsmNpcDisplay;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmNbtCodec;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataDisplay.class, remap = false)
public abstract class DataDisplayMixin implements IYsmNpcDisplay {
    @Unique
    private boolean customnpcsYsmCompat$enabled;
    @Unique
    private String customnpcsYsmCompat$modelId = "";

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void customnpcsYsmCompat$save(CompoundTag root, CallbackInfoReturnable<CompoundTag> cir) {
        YsmNbtCodec.write(cir.getReturnValue(),
                new YsmDisplayData(customnpcsYsmCompat$enabled, customnpcsYsmCompat$modelId));
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void customnpcsYsmCompat$read(CompoundTag root, CallbackInfo ci) {
        YsmDisplayData data = YsmNbtCodec.read(root);
        customnpcsYsmCompat$modelId = data.modelId();
        customnpcsYsmCompat$enabled = data.enabled();
    }

    @Override
    public boolean customnpcsYsmCompat$isEnabled() {
        return customnpcsYsmCompat$enabled;
    }

    @Override
    public void customnpcsYsmCompat$setEnabled(boolean enabled) {
        customnpcsYsmCompat$enabled = enabled && !customnpcsYsmCompat$modelId.isEmpty();
        if (!customnpcsYsmCompat$enabled) {
            customnpcsYsmCompat$modelId = "";
        }
    }

    @Override
    public String customnpcsYsmCompat$getModelId() {
        return customnpcsYsmCompat$modelId;
    }

    @Override
    public void customnpcsYsmCompat$setModelId(String modelId) {
        customnpcsYsmCompat$modelId = YsmDisplayData.normalizeModelId(modelId);
        if (customnpcsYsmCompat$modelId.isEmpty()) {
            customnpcsYsmCompat$enabled = false;
        }
    }
}
