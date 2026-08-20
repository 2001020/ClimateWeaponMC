package com.stormweapon.client.gui;

import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.network.LauncherControlPacket;
import com.stormweapon.network.StormNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Compact industrial launcher console: three X/Z presets plus arm/launch control. */
public final class LauncherControlScreen extends Screen {
    private final BlockPos launcherPos;
    private int slot;
    private EditBox xField;
    private EditBox zField;
    private EditBox countdownField;

    public LauncherControlScreen(BlockPos launcherPos) {
        super(Component.translatable("gui.stormweapon.launcher.title"));
        this.launcherPos = launcherPos;
    }

    @Override
    protected void init() {
        int left = width / 2 - 112;
        int top = height / 2 - 100;
        xField = addRenderableWidget(new EditBox(font, left + 50, top + 46, 150, 20, Component.literal("X")));
        zField = addRenderableWidget(new EditBox(font, left + 50, top + 72, 150, 20, Component.literal("Z")));
        countdownField = addRenderableWidget(new EditBox(font, left + 110, top + 98, 90, 20, Component.literal("T")));
        xField.setMaxLength(11); zField.setMaxLength(11);
        countdownField.setMaxLength(2);
        addRenderableWidget(Button.builder(Component.literal("PRESET 1"), button -> selectSlot(0)).bounds(left, top + 15, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("PRESET 2"), button -> selectSlot(1)).bounds(left + 73, top + 15, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("PRESET 3"), button -> selectSlot(2)).bounds(left + 146, top + 15, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stormweapon.launcher.save"), button -> save()).bounds(left, top + 128, 104, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.stormweapon.launcher.arm"), button -> arm()).bounds(left + 110, top + 128, 104, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(left + 56, top + 155, 104, 20).build());
        selectSlot(0);
    }

    private void selectSlot(int value) {
        slot = Mth.clamp(value, 0, 2);
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(launcherPos) instanceof WeatherLauncherBlockEntity launcher) {
            xField.setValue(Integer.toString(launcher.presetX(slot)));
            zField.setValue(Integer.toString(launcher.presetZ(slot)));
            countdownField.setValue(Integer.toString(launcher.countdownSeconds()));
        }
    }
    private void save() {
        try {
            StormNetwork.sendLauncherControl(new LauncherControlPacket(launcherPos, LauncherControlPacket.SAVE_TARGET, slot,
                Integer.parseInt(xField.getValue()), Integer.parseInt(zField.getValue()),
                Mth.clamp(Integer.parseInt(countdownField.getValue()), 3, 30)));
        } catch (NumberFormatException ignored) { }
    }
    private void arm() {
        save();
        StormNetwork.sendLauncherControl(new LauncherControlPacket(launcherPos, LauncherControlPacket.ARM_OR_LAUNCH, slot, 0, 0, 0));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
        int left = width / 2 - 120;
        int top = height / 2 - 110;
        graphics.fill(left, top, left + 240, top + 202, 0xE817202B);
        graphics.outline(left, top, 240, 202, 0xFF647890);
        graphics.centeredText(font, title, width / 2, top + 5, 0xFFE6EDF5);
        graphics.text(font, Component.translatable("gui.stormweapon.launcher.target", slot + 1), left + 9, top + 39, 0xFFB8C9D9, false);
        graphics.text(font, "X", left + 34, top + 52, 0xFFFDCC58, false);
        graphics.text(font, "Z", left + 34, top + 78, 0xFFFDCC58, false);
        graphics.text(font, Component.translatable("gui.stormweapon.launcher.countdown"), left + 10, top + 104, 0xFFFDCC58, false);
        graphics.text(font, Component.translatable("gui.stormweapon.launcher.hint"), left + 12, top + 184, 0xFF95A8B9, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
