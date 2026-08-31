package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.api.IYsmMaidTweakData;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmNbtCodec;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(EntityMaid.class)
public abstract class EntityMaidTweakDataMixin extends TamableAnimal implements IYsmMaidTweakData {
    @Unique
    private static final String CUSTOMNPCS_YSM_COMPAT$SAVE_TAG = "CustomNPCsYsmCompatMaidTweaks";
    @Unique
    private static final EntityDataAccessor<CompoundTag> CUSTOMNPCS_YSM_COMPAT$TWEAKS =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.COMPOUND_TAG);

    protected EntityMaidTweakDataMixin(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void customnpcsYsmCompat$defineMaidTweaks(CallbackInfo ci) {
        entityData.define(CUSTOMNPCS_YSM_COMPAT$TWEAKS, new CompoundTag());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void customnpcsYsmCompat$saveMaidTweaks(CompoundTag root, CallbackInfo ci) {
        CompoundTag stored = entityData.get(CUSTOMNPCS_YSM_COMPAT$TWEAKS);
        if (!stored.isEmpty()) root.put(CUSTOMNPCS_YSM_COMPAT$SAVE_TAG, stored.copy());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void customnpcsYsmCompat$loadMaidTweaks(CompoundTag root, CallbackInfo ci) {
        CompoundTag stored = root.contains(CUSTOMNPCS_YSM_COMPAT$SAVE_TAG, Tag.TAG_COMPOUND)
                ? root.getCompound(CUSTOMNPCS_YSM_COMPAT$SAVE_TAG).copy() : new CompoundTag();
        entityData.set(CUSTOMNPCS_YSM_COMPAT$TWEAKS, stored, true);
    }

    @Override
    public Map<String, YsmTweakProfile> customnpcsYsmCompat$getMaidTweakProfiles() {
        return YsmNbtCodec.read(entityData.get(CUSTOMNPCS_YSM_COMPAT$TWEAKS)).tweakProfiles();
    }

    @Override
    public void customnpcsYsmCompat$setMaidTweakProfiles(Map<String, YsmTweakProfile> profiles) {
        YsmDisplayData normalized = new YsmDisplayData(false, "", profiles);
        CompoundTag stored = new CompoundTag();
        YsmNbtCodec.write(stored, normalized);
        entityData.set(CUSTOMNPCS_YSM_COMPAT$TWEAKS, stored, true);
    }
}
