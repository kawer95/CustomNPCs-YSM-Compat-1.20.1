package com.arxyt.customnpcsysmcompat.client.gui;

import com.arxyt.customnpcsysmcompat.client.AnimatedNpcRenderBridge;
import com.arxyt.customnpcsysmcompat.client.Ysm265Adapter;
import com.arxyt.customnpcsysmcompat.client.YsmTweakForm;
import com.arxyt.customnpcsysmcompat.client.YsmTweakGroup;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmTweakEntry;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Per-model YSM config_forms editor. Saving remains owned by the parent model page. */
final class YsmTweakScreen extends Screen {
    private static final int TOP = 42;
    private static final int BOTTOM_MARGIN = 32;
    private static final int GROUP_HEIGHT = 18;
    private static final int FORM_HEIGHT = 24;

    private final YsmModelSelectionScreen parent;
    private final EntityNPCInterface npc;
    private final String modelId;
    private final YsmDisplayData openedData;
    private final List<YsmTweakGroup> groups;
    private final List<Line> lines = new ArrayList<>();
    private YsmDisplayData working;
    private int scrollPixels;
    private int contentHeight;

    YsmTweakScreen(YsmModelSelectionScreen parent, EntityNPCInterface npc, String modelId,
                   YsmDisplayData data) {
        super(Component.translatable("gui.customnpcs_ysm_compat.tweaks.title"));
        this.parent = parent;
        this.npc = npc;
        this.modelId = modelId;
        this.openedData = data;
        this.working = data;
        this.groups = Ysm265Adapter.tweakGroups(modelId);
        buildLines();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void buildLines() {
        int y = 0;
        for (YsmTweakGroup group : groups) {
            lines.add(new HeaderLine(group, y));
            y += GROUP_HEIGHT;
            for (YsmTweakForm form : group.forms()) {
                lines.add(new FormLine(group, form, y));
                y += FORM_HEIGHT;
            }
        }
        contentHeight = y;
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int panelWidth = panelWidth();
        int bottom = height - BOTTOM_MARGIN;
        for (Line line : lines) {
            if (!(line instanceof FormLine formLine)) continue;
            int y = TOP + formLine.y() - scrollPixels;
            if (y + FORM_HEIGHT <= TOP || y >= bottom) continue;
            addFormWidgets(formLine, 12, y + 2, panelWidth - 24);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.tweaks.done"),
                        button -> closeToParent())
                .bounds(width / 2 - 154, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.tweaks.reset_model"),
                        button -> resetModel())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.tweaks.cancel"),
                        button -> cancelPage())
                .bounds(width / 2 + 54, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.tweaks.clear_all"),
                        button -> clearAll())
                .bounds(12, height - 26, 105, 20).build());
    }

    private void addFormWidgets(FormLine line, int x, int y, int availableWidth) {
        YsmTweakForm form = line.form();
        int resetWidth = 42;
        int controlX = x + Math.min(150, availableWidth / 3);
        int controlWidth = Math.max(90, availableWidth - (controlX - x) - resetWidth - 4);
        if (form instanceof YsmTweakForm.Checkbox checkbox) {
            addRenderableWidget(Button.builder(checkboxLabel(line.group().id(), checkbox),
                            button -> cycleCheckbox(line.group().id(), checkbox))
                    .bounds(controlX, y, controlWidth, 20).build());
        } else if (form instanceof YsmTweakForm.Range range) {
            YsmTweakEntry existing = entryFor(line.group().id(), range.index());
            double value = existing == null ? range.min() : Ysm265Adapter.normalizeRange(range, existing.numberValue());
            addRenderableWidget(new RangeSlider(controlX, y, controlWidth, 20, range, existing != null, value,
                    next -> setRange(line.group().id(), range, next)));
        } else if (form instanceof YsmTweakForm.Radio radio) {
            addRenderableWidget(Button.builder(radioLabel(line.group().id(), radio),
                            button -> cycleRadio(line.group().id(), radio))
                    .bounds(controlX, y, controlWidth, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.customnpcs_ysm_compat.tweaks.default"),
                        button -> clearOverride(line.group().id(), form.index()))
                .bounds(controlX + controlWidth + 4, y, resetWidth, 20).build());
    }

    private Component checkboxLabel(String groupId, YsmTweakForm.Checkbox form) {
        YsmTweakEntry entry = entryFor(groupId, form.index());
        String value = entry == null ? "§7" + translated("gui.customnpcs_ysm_compat.tweaks.default")
                : entry.booleanValue() ? "§a" + translated("gui.customnpcs_ysm_compat.tweaks.enabled")
                : "§c" + translated("gui.customnpcs_ysm_compat.tweaks.disabled");
        return Component.literal(value);
    }

    private Component radioLabel(String groupId, YsmTweakForm.Radio form) {
        YsmTweakEntry entry = entryFor(groupId, form.index());
        String choice = entry == null ? "§7" + translated("gui.customnpcs_ysm_compat.tweaks.default")
                : "§b" + entry.choice();
        return Component.literal(choice);
    }

    private String translated(String key) {
        return Component.translatable(key).getString();
    }

    private void cycleCheckbox(String groupId, YsmTweakForm.Checkbox form) {
        YsmTweakEntry existing = entryFor(groupId, form.index());
        if (existing == null) {
            setWorking(working.withTweak(modelId,
                    YsmTweakEntry.checkbox(groupId, form.index(), form.variable(), true, 0)));
        } else if (existing.booleanValue()) {
            setWorking(working.withTweak(modelId,
                    YsmTweakEntry.checkbox(groupId, form.index(), form.variable(), false, 0)));
        } else {
            clearOverride(groupId, form.index());
            return;
        }
        rebuildWidgets();
    }

    private void cycleRadio(String groupId, YsmTweakForm.Radio form) {
        YsmTweakEntry current = entryFor(groupId, form.index());
        int next = current == null ? 0 : form.choices().indexOf(current.choice()) + 1;
        if (next < 0 || next >= form.choices().size()) {
            clearOverride(groupId, form.index());
            return;
        }
        setWorking(working.withTweak(modelId,
                YsmTweakEntry.radio(groupId, form.index(), form.variable(), form.choices().get(next), 0)));
        rebuildWidgets();
    }

    private void setRange(String groupId, YsmTweakForm.Range form, double value) {
        setWorking(working.withTweak(modelId,
                YsmTweakEntry.range(groupId, form.index(), form.variable(),
                        Ysm265Adapter.normalizeRange(form, value), 0)));
    }

    private void clearOverride(String groupId, int formIndex) {
        setWorking(working.withoutTweak(modelId, groupId, formIndex));
        rebuildWidgets();
    }

    private void resetModel() {
        setWorking(working.resetTweaks(modelId));
        rebuildWidgets();
    }

    private void clearAll() {
        setWorking(working.clearAllTweaks());
        rebuildWidgets();
    }

    private void setWorking(YsmDisplayData data) {
        working = data;
        parent.replaceWorkingData(data);
        AnimatedNpcRenderBridge.discardPreview(npc);
    }

    private YsmTweakEntry entryFor(String groupId, int formIndex) {
        YsmTweakProfile profile = working.tweaksFor(modelId);
        return profile.find(groupId, formIndex);
    }

    private void closeToParent() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void cancelPage() {
        parent.replaceWorkingData(openedData);
        AnimatedNpcRenderBridge.discardPreview(npc);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        cancelPage();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int viewport = Math.max(1, height - TOP - BOTTOM_MARGIN);
        int max = Math.max(0, contentHeight - viewport);
        int next = Math.max(0, Math.min(max, scrollPixels - (int) Math.signum(delta) * 16));
        if (next != scrollPixels) {
            scrollPixels = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelWidth = panelWidth();
        graphics.drawCenteredString(font, title, panelWidth / 2, 10, 0xFFFFFF);
        graphics.drawString(font, font.plainSubstrByWidth(modelId, panelWidth - 24), 12, 27, 0xAAAAAA);
        graphics.fill(8, TOP, panelWidth - 8, height - BOTTOM_MARGIN, 0x90000000);
        if (groups.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.customnpcs_ysm_compat.tweaks.empty"),
                    panelWidth / 2, TOP + 10, 0xFF7777);
        }
        for (Line line : lines) {
            int y = TOP + line.y() - scrollPixels;
            if (y + line.height() <= TOP || y >= height - BOTTOM_MARGIN) continue;
            if (line instanceof HeaderLine header) {
                graphics.drawString(font, "§e" + header.group().title(), 12, y + 4, 0xFFFF55, false);
            } else if (line instanceof FormLine formLine) {
                YsmTweakForm form = formLine.form();
                String label = form.title().isBlank() ? form.variable() : form.title();
                graphics.drawString(font, font.plainSubstrByWidth(label, Math.min(145, panelWidth / 3 - 8)),
                        12, y + 4, 0xFFFFFF, false);
                if (!form.description().isBlank() && mouseX >= 12 && mouseX < panelWidth / 3
                        && mouseY >= y && mouseY < y + FORM_HEIGHT) {
                    graphics.renderTooltip(font, Component.literal(form.description()), mouseX, mouseY);
                }
            }
        }
        int previewX = panelWidth + (width - panelWidth) / 2;
        int previewBottom = height - 46;
        graphics.drawCenteredString(font, Component.translatable("gui.customnpcs_ysm_compat.tweaks.preview"),
                previewX, 28, 0xCCCCCC);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, previewX, previewBottom,
                Math.max(24, Math.min(55, (height - 88) / 3)), previewX - mouseX, previewBottom - 70 - mouseY, npc);
    }

    private int panelWidth() {
        return Math.max(280, width * 2 / 3);
    }

    private sealed interface Line permits HeaderLine, FormLine {
        int y();

        int height();
    }

    private record HeaderLine(YsmTweakGroup group, int y) implements Line {
        @Override
        public int height() {
            return GROUP_HEIGHT;
        }
    }

    private record FormLine(YsmTweakGroup group, YsmTweakForm form, int y) implements Line {
        @Override
        public int height() {
            return FORM_HEIGHT;
        }
    }

    private static final class RangeSlider extends AbstractSliderButton {
        private final YsmTweakForm.Range form;
        private final DoubleConsumer consumer;
        private boolean override;

        private RangeSlider(int x, int y, int width, int height, YsmTweakForm.Range form,
                            boolean override, double initialValue, DoubleConsumer consumer) {
            super(x, y, width, height, Component.empty(), valueFor(form, initialValue));
            this.form = form;
            this.override = override;
            this.consumer = consumer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String valueText = override ? String.format(Locale.ROOT, "%.4f", current()).replaceAll("0+$", "").replaceAll("\\.$", "")
                    : Component.translatable("gui.customnpcs_ysm_compat.tweaks.default").getString();
            setMessage(Component.literal(valueText));
        }

        @Override
        protected void applyValue() {
            override = true;
            double normalized = Ysm265Adapter.normalizeRange(form, current());
            value = valueFor(form, normalized);
            consumer.accept(normalized);
            updateMessage();
        }

        private double current() {
            return form.min() + value * (form.max() - form.min());
        }

        private static double valueFor(YsmTweakForm.Range form, double actual) {
            double span = form.max() - form.min();
            return span == 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, (actual - form.min()) / span));
        }
    }
}
