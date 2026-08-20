package com.stormweapon.client.gui;

import com.stormweapon.config.StormQuality;
import com.stormweapon.config.StormSettings;
import com.stormweapon.config.StormSettingsState;
import com.stormweapon.network.StormNetwork;
import com.stormweapon.network.StormSettingsUpdatePacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Editor for the shared, server-owned Storm Weapon settings.
 *
 * <p>Every change is applied to a local working copy and immediately sent to the server, which
 * validates the sender's permission, persists it and broadcasts it back to everyone. The working
 * copy is re-seeded from {@link StormSettingsState} on each redraw, so a change another operator
 * makes while this screen is open shows up here rather than being silently overwritten by whatever
 * this client happened to be displaying.</p>
 */
public final class StormSettingsScreen extends Screen {
    private Button quality;
    private Button rain;
    private Button clouds;
    private Button shake;
    private Button flash;
    private Button fog;
    private Button meteors;

    public StormSettingsScreen() { super(Component.translatable("gui.stormweapon.settings.title")); }

    @Override protected void init() {
        int left = width / 2 - 112, top = height / 2 - 116;
        quality = addRenderableWidget(Button.builder(Component.empty(), b -> {
            StormQuality[] values = StormQuality.values();
            StormSettings s = StormSettingsState.current();
            apply(new StormSettings(values[(s.quality().ordinal() + 1) % values.length], s.cloudQuality(),
                s.rainDensity(), s.cameraShake(), s.lightningFlash(), s.stormFog(), s.meteorDensity()));
        }).bounds(left, top + 28, 224, 20).build());
        rain = addRenderableWidget(Button.builder(Component.empty(), b -> {
            StormSettings s = StormSettingsState.current();
            apply(new StormSettings(s.quality(), s.cloudQuality(), next(s.rainDensity(), 0.25D, 0.25D, 1.50D),
                s.cameraShake(), s.lightningFlash(), s.stormFog(), s.meteorDensity()));
        }).bounds(left, top + 53, 224, 20).build());
        clouds = addRenderableWidget(Button.builder(Component.empty(), b -> {
            StormSettings s = StormSettingsState.current();
            apply(new StormSettings(s.quality(), s.cloudQuality() % 5 + 1, s.rainDensity(),
                s.cameraShake(), s.lightningFlash(), s.stormFog(), s.meteorDensity()));
        }).bounds(left, top + 78, 224, 20).build());
        shake = addRenderableWidget(Button.builder(Component.empty(), b -> {
            StormSettings s = StormSettingsState.current();
            apply(new StormSettings(s.quality(), s.cloudQuality(), s.rainDensity(),
                next(s.cameraShake(), 0.2D, 0.0D, 1.0D), s.lightningFlash(), s.stormFog(), s.meteorDensity()));
        }).bounds(left, top + 103, 224, 20).build());
        flash = addRenderableWidget(Button.builder(Component.empty(), b -> {
            StormSettings s = StormSettingsState.current();
            apply(new StormSettings(s.quality(), s.cloudQuality(), s.rainDensity(), s.cameraShake(),
                next(s.lightningFlash(), 0.2D, 0.0D, 1.0D), s.stormFog(), s.meteorDensity()));
        }).bounds(left, top + 128, 224, 20).build());
        fog = addRenderableWidget(Button.builder(Component.empty(), b -> {
            StormSettings s = StormSettingsState.current();
            apply(new StormSettings(s.quality(), s.cloudQuality(), s.rainDensity(), s.cameraShake(),
                s.lightningFlash(), !s.stormFog(), s.meteorDensity()));
        }).bounds(left, top + 153, 224, 20).build());
        meteors = addRenderableWidget(Button.builder(Component.empty(), b -> {
            StormSettings s = StormSettingsState.current();
            apply(new StormSettings(s.quality(), s.cloudQuality(), s.rainDensity(), s.cameraShake(),
                s.lightningFlash(), s.stormFog(), next(s.meteorDensity(), 0.4D, 0.2D, 3.0D)));
        }).bounds(left, top + 178, 224, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 55, top + 206, 114, 20).build());
        refresh();
    }

    /**
     * Optimistically updates the local copy so the button label changes at once, then asks the
     * server to make it official. The server's broadcast is authoritative and will correct this
     * client if the edit is refused.
     */
    private void apply(StormSettings settings) {
        StormSettings sanitized = settings.sanitized();
        StormSettingsState.set(sanitized);
        StormNetwork.sendLauncherSettingsUpdate(new StormSettingsUpdatePacket(sanitized));
        refresh();
    }

    private static double next(double current, double step, double min, double max) {
        double result = current + step;
        return result > max + 0.001D ? min : Math.round(result * 100.0D) / 100.0D;
    }

    private void refresh() {
        StormSettings s = StormSettingsState.current();
        quality.setMessage(Component.translatable("gui.stormweapon.settings.quality", s.quality().name()));
        rain.setMessage(Component.translatable("gui.stormweapon.settings.rain", String.format("%.2f", s.rainDensity())));
        clouds.setMessage(Component.translatable("gui.stormweapon.settings.clouds", s.cloudQuality()));
        shake.setMessage(Component.translatable("gui.stormweapon.settings.shake", String.format("%.1f", s.cameraShake())));
        flash.setMessage(Component.translatable("gui.stormweapon.settings.flash", String.format("%.1f", s.lightningFlash())));
        fog.setMessage(Component.translatable("gui.stormweapon.settings.fog", s.stormFog() ? "ON" : "OFF"));
        meteors.setMessage(Component.translatable("gui.stormweapon.settings.meteors", String.format("%.1f", s.meteorDensity())));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        // Another operator's change arrives asynchronously, so labels are re-read every frame
        // rather than only when this client clicks something.
        refresh();
        extractTransparentBackground(graphics);
        int left = width / 2 - 120, top = height / 2 - 126;
        graphics.fill(left, top, left + 240, top + 253, 0xE8151C27);
        graphics.outline(left, top, 240, 253, 0xFF788DA4);
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFEAF2FF);
        graphics.text(font, Component.translatable("gui.stormweapon.settings.help"), left + 12, top + 235, 0xFF9EB3C9, false);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    @Override public boolean isPauseScreen() { return false; }
}
