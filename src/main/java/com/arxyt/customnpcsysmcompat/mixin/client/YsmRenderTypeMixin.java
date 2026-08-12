package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.ProxyVisibilityContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import com.arxyt.customnpcsysmcompat.client.Ysm265Adapter;

/** Gives partially invisible CustomNPC proxies a blend-capable YSM render layer. */
@Mixin(targets = "com.elfmcys.yesstevemodel.oOoOOO0OOOoo000O0o0oo0OO", remap = false)
public abstract class YsmRenderTypeMixin {
    // Merged into YSM's abstract renderer as an override of its interface default.
    public RenderType Oo0Oo0o00O00Oo0OOoOOoooo(ResourceLocation texture, boolean visible,
                                                boolean glowing, boolean customLayer) {
        return Ysm265Adapter.selectRenderType(texture, visible, glowing, customLayer,
                ProxyVisibilityContext.partial());
    }
}
