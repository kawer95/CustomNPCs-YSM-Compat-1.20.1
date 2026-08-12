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
    // CustomNPCs' 400x240 model page leaves x=124..244, y=23..43 unused.
    // The bottom-right area is occupied by its x=202..322, y=210..230 rotation slider.
    private static final int YSM_BUTTON_X = 124;
    private static final int YSM_BUTTON_Y = 23;
    private static final int YSM_BUTTON_WIDTH = 120;

    protected GuiCreationEntitiesMixin(EntityNPCInterface npc) {
        super(npc);
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void customnpcsYsmCompat$addModelButton(CallbackInfo ci) {
        GuiCreationEntities self = (GuiCreationEntities) (Object) this;
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.open"),
                        button -> openGui(new YsmModelSelectionScreen(self, npc)))
                .bounds(this.guiLeft + YSM_BUTTON_X, this.guiTop + YSM_BUTTON_Y,
                        YSM_BUTTON_WIDTH, 20)
                .build());
    }
}
