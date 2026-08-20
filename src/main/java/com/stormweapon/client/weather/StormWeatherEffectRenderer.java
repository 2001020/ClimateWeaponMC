package com.stormweapon.client.weather;

import com.stormweapon.client.StormClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps Minecraft's thunderstorm sky state while suppressing its vertical rain/snow columns.
 * Storm Weapon renders its wind-tilted precipitation in a later frame-graph pass.
 */
public final class StormWeatherEffectRenderer extends WeatherEffectRenderer {
    public static void ensureInstalled() {
        Minecraft minecraft = Minecraft.getInstance();
        WeatherEffectRenderer current = minecraft.levelRenderer.getWeatherEffects();
        if (current instanceof StormWeatherEffectRenderer) {
            return;
        }
        current.close();
        minecraft.levelRenderer.setWeatherEffects(new StormWeatherEffectRenderer());
    }

    @Override
    public void extractRenderState(
        ClientLevel level,
        float partialTick,
        Vec3 cameraPosition,
        WeatherRenderState state
    ) {
        if (StormClientManager.visualWeatherActive()) {
            // Rain/thunder levels remain untouched, so sky tint, celestial dimming and thunder
            // ambience still behave like a full Minecraft thunderstorm. Only precipitation
            // geometry is removed, preventing vanilla vertical streaks from mixing with ours.
            state.reset();
            return;
        }
        super.extractRenderState(level, partialTick, cameraPosition, state);
    }
}
