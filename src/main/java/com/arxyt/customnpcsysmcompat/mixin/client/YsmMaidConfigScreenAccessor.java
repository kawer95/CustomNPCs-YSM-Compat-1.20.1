package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.YsmMaidScreenAccess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses the maid retained by YSM's nested config_forms page. */
@Mixin(targets = "com.elfmcys.yesstevemodel.o0ooOOOO0o0Ooo0oo00O0o0o", remap = false)
public interface YsmMaidConfigScreenAccessor extends YsmMaidScreenAccess {
    @Override
    @Accessor("O0OooOo0oOOoOoOoOooO000o")
    EntityMaid customnpcsYsmCompat$getMaid();
}
