package com.stormweapon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stormweapon.StormWeaponMod;
import com.stormweapon.client.weather.StormCloudRenderer;
import com.stormweapon.client.weather.StormLightningBudget;
import com.stormweapon.client.weather.StormLightningField;
import com.stormweapon.client.weather.StormLightningRenderer;
import com.stormweapon.client.weather.StormRainRenderer;
import com.stormweapon.client.weather.StormWeatherPass;
import com.stormweapon.client.weather.StormWindField;
import com.stormweapon.config.StormConfig;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact storm debug overlay, shown only while the synchronized {@code debug} flag is set by
 * {@code /stormweapon debug}. It is a read-only view of client state and never drives rendering.
 */
public final class StormDebugHud {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "debug_hud");
    private static final int BACKGROUND_COLOR = 0xA0000000;
    private static final int TEXT_COLOR = 0xFFCFE0FF;
    private static final int TITLE_COLOR = 0xFF9FD0FF;

    private StormDebugHud() {}

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(
            event -> event.getLayeredDraw().add(ForgeLayeredDraw.VANILLA_ROOT, LAYER_ID, StormDebugHud::draw)
        );
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        StormLightningRenderer lightning = StormWeatherPass.lightningRenderer();
        int flashAlpha = Mth.clamp(Math.round(lightning.flashStrength() * 150.0F), 0, 150);
        if (flashAlpha > 0) {
            graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                (flashAlpha << 24) | 0x00EAF3FF);
        }
        if (minecraft.gui.hud.isHidden()) {
            return;
        }
        // Debug/status is shared between both slots; whichever is active is shown, thunder first.
        StormSnapshot thunder = StormClientState.thunder();
        StormSnapshot fog = StormClientState.fog();
        StormSnapshot snapshot = thunder.active() ? thunder : fog;
        if (!snapshot.debug()) {
            return;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        long gameTime = minecraft.level.getGameTime();
        double cameraX = minecraft.gameRenderer.mainCamera().position().x;
        double cameraZ = minecraft.gameRenderer.mainCamera().position().z;

        float influence = snapshot.radialInfluence(cameraX, cameraZ);
        float phaseSeconds = (gameTime + partialTick - snapshot.phaseStartGameTime()) / 20.0F;
        float phaseLength = snapshot.phaseDurationTicks() / 20.0F;
        float elapsed = (gameTime + partialTick - snapshot.startGameTime()) / 20.0F;
        StormWindField.Sample wind = StormWindField.sample(snapshot, cameraX, cameraZ, gameTime, partialTick);
        StormCloudRenderer clouds = StormWeatherPass.cloudRenderer();
        StormRainRenderer rain = StormWeatherPass.rainRenderer();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.stormweapon.debug.title"));
        lines.add(Component.translatable(
            "hud.stormweapon.debug.phase",
            snapshot.phase().name(),
            format(phaseSeconds),
            format(phaseLength),
            format(elapsed)
        ));
        lines.add(Component.translatable(
            "hud.stormweapon.debug.center",
            Math.round(snapshot.centerX()),
            Math.round(snapshot.centerZ()),
            format(influence)
        ));
        lines.add(Component.translatable(
            "hud.stormweapon.debug.envelopes",
            format(snapshot.cloudIntensity(gameTime, partialTick)),
            format(snapshot.windIntensity(gameTime, partialTick)),
            format(snapshot.rainIntensity(gameTime, partialTick)),
            format(snapshot.lightningIntensity(gameTime, partialTick))
        ));
        lines.add(Component.translatable(
            "hud.stormweapon.debug.wind",
            Math.round(wind.angleDegrees()),
            format(wind.speed()),
            format(wind.gust()),
            Math.round(wind.tiltDegrees())
        ));
        lines.add(Component.translatable(
            "hud.stormweapon.debug.quality",
            StormConfig.quality().name(),
            clouds.lastTileCount(),
            clouds.lastTileBudget(),
            clouds.lastQuadCount()
        ));
        lines.add(Component.translatable(
            "hud.stormweapon.debug.weather",
            rain.lastStreakCount(),
            rain.lastStreakBudget(),
            rain.lastCellCount(),
            rain.lastSplashCount()
        ));
        lines.add(Component.translatable(
            "hud.stormweapon.debug.lightning",
            lightning.lastCloudFlashes(),
            StormLightningBudget.of(StormConfig.quality()).maxCloudFlashes(),
            StormLightningField.lastCandidateCells(),
            lightning.lastPhysicalBolts()
        ));
        String backend = backendName();
        if (backend != null) {
            lines.add(Component.translatable("hud.stormweapon.debug.backend", backend));
        }

        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, minecraft.font.width(line));
        }
        int lineHeight = minecraft.font.lineHeight + 1;
        int x = 4;
        int y = 4;
        graphics.fill(x - 2, y - 2, x + width + 2, y + lines.size() * lineHeight + 1, BACKGROUND_COLOR);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(minecraft.font, lines.get(i), x, y + i * lineHeight, i == 0 ? TITLE_COLOR : TEXT_COLOR);
        }
    }

    /**
     * Blaze3D publishes the active backend on the device info record, so the HUD can report it
     * without reflection or any driver-specific query. Returns {@code null} if it is unavailable.
     */
    private static String backendName() {
        try {
            return RenderSystem.getDevice().getDeviceInfo().backendName();
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }

    private static String format(float value) {
        return String.format("%.2f", value);
    }
}
