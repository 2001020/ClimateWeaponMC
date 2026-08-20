package com.stormweapon.client.weather;

import com.stormweapon.client.StormClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Feeds the artificial storm envelope into Minecraft's client-only rain/thunder interpolation.
 *
 * <p>This does not start server weather and does not replace the Storm Weapon state machine.
 * It only lets vanilla compute its familiar thunderstorm sky, sun/moon dimming and cloud tint for
 * the local observer. The strength is multiplied by the regional influence at the camera, so a
 * player outside the weapon radius still sees normal weather. Custom rain, wind, debris and
 * lightning remain driven by the synchronized Storm Weapon snapshot.</p>
 */
public final class StormVanillaWeatherBridge {
    private static ClientLevel overriddenLevel;
    private static float savedRain;
    private static float savedThunder;

    private StormVanillaWeatherBridge() {}

    public static void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            restore();
            return;
        }

        float thunderStrength = Mth.clamp(StormClientManager.smoothedCloudIntensity(), 0.0F, 1.0F);
        float fogStrength = Mth.clamp(StormClientManager.smoothedFogCloudIntensity(), 0.0F, 1.0F);
        float blizzardStrength = Mth.clamp(StormClientManager.smoothedBlizzardIntensity(), 0.0F, 1.0F);
        float meteorStrength = Mth.clamp(StormClientManager.smoothedMeteorDarkness(), 0.0F, 1.0F);
        if (thunderStrength <= 0.01F && fogStrength <= 0.01F && blizzardStrength <= 0.01F
            && meteorStrength <= 0.01F) {
            restore();
            return;
        }

        if (overriddenLevel != level) {
            restore();
            overriddenLevel = level;
            savedRain = level.rainLevel;
            savedThunder = level.thunderLevel;
        }

        // Full vanilla rain/thunder state supplies the stock thunderstorm sky without a command or
        // chat notification. StormWeatherEffectRenderer removes only the original precipitation
        // geometry, leaving Storm Weapon's wind-tilted rain as the visible rainfall.
        //
        // Fog weather borrows vanilla's overcast sky darkening at full rainLevel strength, plus a
        // moderate thunderLevel contribution (thunderLevel drives extra sky darkening beyond what
        // rainLevel alone gives) for a noticeably dimmer sky. It never spawns actual lightning (that
        // stays gated on lightningIntensity, always 0 for fog) and stays below a full thunderstorm's
        // thunderLevel, so the sky still reads as a darker overcast rather than a storm. When both
        // kinds are active at once their contributions simply take the stronger value, same as with
        // any other pre-existing weather this bridge layers on top of.
        // The meteor payload drives both channels to full: thunderLevel is what darkens the sky
        // beyond plain overcast, so pinning both is how the sky goes properly black rather than
        // merely grey. StormWeatherEffectRenderer still suppresses the precipitation geometry, so
        // no rain actually falls from it.
        float vanillaRain = Math.max(Math.max(Math.max(thunderStrength, fogStrength), blizzardStrength), meteorStrength);
        float vanillaThunder = Math.max(
            Math.max(Math.max(thunderStrength, fogStrength * 0.45F), blizzardStrength * 0.55F), meteorStrength);
        level.setRainLevel(Math.max(savedRain, vanillaRain));
        level.setThunderLevel(Math.max(savedThunder, vanillaThunder));
    }

    public static void restore() {
        if (overriddenLevel != null) {
            overriddenLevel.setRainLevel(savedRain);
            overriddenLevel.setThunderLevel(savedThunder);
            overriddenLevel = null;
        }
    }
}
