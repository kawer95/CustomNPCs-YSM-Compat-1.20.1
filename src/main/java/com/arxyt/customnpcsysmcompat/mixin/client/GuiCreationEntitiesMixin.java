package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.gui.YsmModelSelectionScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiCreationEntities.class, remap = false)
public abstract class GuiCreationEntitiesMixin extends GuiCreationScreenInterface {
    protected GuiCreationEntitiesMixin(EntityNPCInterface npc) {
        super(npc);
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void customnpcsYsmCompat$addModelButton(CallbackInfo ci) {
        GuiCreationEntities self = (GuiCreationEntities) (Object) this;
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.open"),
                        button -> openGui(new YsmModelSelectionScreen(self, npc)))
                .bounds(this.width - 126, 8, 118, 20)
                .build());
    }
}
