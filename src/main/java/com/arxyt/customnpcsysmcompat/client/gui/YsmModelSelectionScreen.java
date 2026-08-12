package com.arxyt.customnpcsysmcompat.client.gui;

import com.arxyt.customnpcsysmcompat.client.PreviewOverrides;
import com.arxyt.customnpcsysmcompat.client.Ysm265Adapter;
import com.arxyt.customnpcsysmcompat.client.YsmModelEntry;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

public final class YsmModelSelectionScreen extends Screen {
    private static final int ROW_HEIGHT = 18;

    private final GuiCreationEntities parent;
    private final EntityNPCInterface npc;
    private final YsmDisplayData original;
    private final List<YsmModelEntry> allModels;
    private List<YsmModelEntry> filteredModels;
    private EditBox search;
    private Button enabledButton;
    private String selectedId;
    private boolean enabled;
    private int scrollOffset;

    public YsmModelSelectionScreen(GuiCreationEntities parent, EntityNPCInterface npc) {
        super(Component.translatable("gui.customnpcs_ysm_compat.title"));
        this.parent = parent;
        this.npc = npc;
        this.original = YsmDisplayAccess.get(npc.display);
        this.enabled = original.enabled();
        this.selectedId = original.modelId();
        this.allModels = new ArrayList<>(Ysm265Adapter.models());
        this.filteredModels = new ArrayList<>(allModels);
    }

    @Override
    protected void init() {
        int listWidth = Math.max(220, width / 2);
        search = new EditBox(font, 12, 30, listWidth - 24, 20,
                Component.translatable("gui.customnpcs_ysm_compat.search"));
        search.setHint(Component.translatable("gui.customnpcs_ysm_compat.search"));
        search.setResponder(this::filter);
        addRenderableWidget(search);

        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            if (!selectedId.isBlank()) {
                enabled = !enabled;
                button.setMessage(enabledLabel());
                updatePreview();
            }
        }).bounds(listWidth + 12, 30, Math.max(150, width - listWidth - 24), 20).build());

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.apply"),
                button -> apply()).bounds(width / 2 - 154, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.restore"),
                button -> restore()).bounds(width / 2 - 50, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.cancel"),
                button -> cancel()).bounds(width / 2 + 54, bottom, 100, 20).build());
        updatePreview();
    }

    private Component enabledLabel() {
        return Component.literal((enabled ? "§a[✓] " : "§7[ ] ") )
                .append(Component.translatable("gui.customnpcs_ysm_compat.enabled"));
    }

    private void filter(String query) {
        filteredModels = allModels.stream()
                .filter(model -> YsmDisplayData.matches(query, model.id(), model.displayName()))
                .toList();
        scrollOffset = 0;
    }

    private void updatePreview() {
        PreviewOverrides.set(npc, new YsmDisplayData(enabled, selectedId));
    }

    private void apply() {
        YsmDisplayAccess.set(npc.display, new YsmDisplayData(enabled, selectedId));
        PreviewOverrides.clear(npc);
        parent.save();
        Minecraft.getInstance().setScreen(parent);
    }

    private void restore() {
        YsmDisplayAccess.set(npc.display, YsmDisplayData.DISABLED);
        PreviewOverrides.clear(npc);
        parent.save();
        Minecraft.getInstance().setScreen(parent);
    }

    private void cancel() {
        PreviewOverrides.clear(npc);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        int listWidth = Math.max(220, width / 2);
        int firstY = 56;
        int listBottom = height - 34;
        if (mouseX < 8 || mouseX >= listWidth - 8 || mouseY < firstY || mouseY >= listBottom) {
            return false;
        }
        int row = ((int) mouseY - firstY) / ROW_HEIGHT + scrollOffset;
        if (row >= 0 && row < filteredModels.size()) {
            selectedId = filteredModels.get(row).id();
            enabled = true;
            enabledButton.setMessage(enabledLabel());
            updatePreview();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visible = visibleRows();
        int max = Math.max(0, filteredModels.size() - visible);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        renderModelList(graphics);
        renderPreview(graphics, mouseX, mouseY);
    }

    private void renderModelList(GuiGraphics graphics) {
        int listWidth = Math.max(220, width / 2);
        int firstY = 56;
        graphics.fill(8, firstY, listWidth - 8, height - 34, 0x90000000);
        if (filteredModels.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.customnpcs_ysm_compat.empty"),
                    listWidth / 2, firstY + 8, 0xAAAAAA);
            return;
        }
        int end = Math.min(filteredModels.size(), scrollOffset + visibleRows());
        for (int i = scrollOffset; i < end; i++) {
            YsmModelEntry model = filteredModels.get(i);
            int y = firstY + (i - scrollOffset) * ROW_HEIGHT;
            if (model.id().equals(selectedId)) {
                graphics.fill(10, y, listWidth - 10, y + ROW_HEIGHT - 1, 0xA0447744);
            }
            graphics.drawString(font, font.plainSubstrByWidth(model.displayName(), listWidth - 32), 14, y + 2,
                    0xFFFFFF, false);
            graphics.drawString(font, font.plainSubstrByWidth(model.id(), listWidth - 32), 14, y + 10,
                    0x999999, false);
        }
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        int listWidth = Math.max(220, width / 2);
        int centerX = listWidth + (width - listWidth) / 2;
        int bottom = height - 48;
        if (!selectedId.isBlank() && !Ysm265Adapter.hasModel(selectedId)) {
            graphics.drawCenteredString(font, Component.translatable("gui.customnpcs_ysm_compat.missing"),
                    centerX, 58, 0xFF5555);
        }
        graphics.drawCenteredString(font, selectedId.isBlank() ? "-" : selectedId,
                centerX, 72, 0xCCCCCC);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, centerX, bottom,
                Math.max(24, Math.min(55, (height - 100) / 3)),
                centerX - mouseX, bottom - 70 - mouseY, npc);
    }

    private int visibleRows() {
        return Math.max(1, (height - 90) / ROW_HEIGHT);
    }
}
