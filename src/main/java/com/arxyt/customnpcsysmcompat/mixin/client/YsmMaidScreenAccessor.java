package com.arxyt.customnpcsysmcompat.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.elfmcys.yesstevemodel.O00O00O0oOooOoooOOoO0oo0", remap = false)
public interface YsmMaidScreenAccessor {
    @Accessor("O00OOOooOoooOoo0o0o0oO0O")
    EntityMaid customnpcsYsmCompat$getMaid();
}
