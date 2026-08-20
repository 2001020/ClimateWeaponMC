package com.stormweapon.client;

import com.stormweapon.storm.SkyExposure;
import com.stormweapon.storm.StormSnapshot;
import com.stormweapon.client.weather.StormWeatherPass;
import com.stormweapon.client.weather.StormLightningRenderer;
import com.stormweapon.client.weather.StormVanillaWeatherBridge;
import com.stormweapon.client.weather.StormWeatherEffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.stormweapon.StormWeaponMod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Client-side owner of the synchronized storm state.
 *
 * <p>{@link StormClientState} is the raw network holder and stays free of client-only classes;
 * this manager is the render-facing view. It keeps a smoothed intensity so phase changes and
 * {@code /stormweapon storm stop} fade instead of popping, and it retains the last active
 * snapshot for the duration of that fade so the visuals can finish before the state is dropped.</p>
 */
public final class StormClientManager {
    /** Storms build quickly, but release over roughly eight to ten seconds after shutdown. */
    private static final float ATTACK_SMOOTHING = 0.06F;
    private static final float RELEASE_SMOOTHING = 0.025F;

    /** Symmetric, deliberately slow ease for stepping indoors/outdoors, roughly a 3-4 second fade. */
    private static final float SKY_EXPOSURE_SMOOTHING = 0.035F;

    private static StormSnapshot renderThunder = StormSnapshot.CLEAR;
    private static StormSnapshot renderFog = StormSnapshot.CLEAR;
    private static StormSnapshot renderBlizzard = StormSnapshot.CLEAR;
    private static StormSnapshot renderCherry = StormSnapshot.CLEAR;
    /** Thunder-slot envelopes. Wind/rain/lightning only ever come from the thunder deployment. */
    private static float smoothedCloud;
    private static float smoothedWind;
    private static float smoothedRain;
    private static float smoothedLightning;
    /** Fog-slot envelopes: its own cloud cover plus the ground-haze intensity. */
    private static float smoothedFogCloud;
    private static float smoothedFog;
    private static float smoothedBlizzard;
    private static float smoothedCherry;
    private static float smoothedSkyExposure;
    /** Meteor sky darkening. Ramps far faster than the weather envelopes -- the sky is meant to
     *  slam shut over roughly a second, not build over the usual eight. */
    private static float smoothedMeteorDark;
    private static boolean backendLogged;
    private static boolean runtimeProbeLogged;

    private StormClientManager() {}

    public static void register() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(StormClientManager::onClientTick);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(StormClientManager::onLoggingOut);
    }

    /** Thunder-slot snapshot, including the retained snapshot during a fade-out. */
    public static StormSnapshot thunderSnapshot() {
        return renderThunder;
    }

    /** Fog-slot snapshot, including the retained snapshot during a fade-out. */
    public static StormSnapshot fogSnapshot() {
        return renderFog;
    }

    public static StormSnapshot blizzardSnapshot() {
        return renderBlizzard;
    }

    public static StormSnapshot cherrySnapshot() {
        return renderCherry;
    }

    /** Thunder slot's own cloud cover. */
    public static float smoothedCloudIntensity() {
        return smoothedCloud;
    }

    /** Fog slot's own cloud cover, used only where a second, independently positioned deck is rendered. */
    public static float smoothedFogCloudIntensity() {
        return smoothedFogCloud;
    }

    public static float smoothedWindIntensity() {
        return smoothedWind;
    }

    public static float smoothedRainIntensity() {
        return smoothedRain;
    }

    public static float smoothedLightningIntensity() {
        return smoothedLightning;
    }

    public static float smoothedFogIntensity() {
        return smoothedFog;
    }

    public static float smoothedBlizzardIntensity() {
        return smoothedBlizzard;
    }

    public static float smoothedCherryIntensity() {
        return smoothedCherry;
    }

    /** Eased 0..1 meteor sky darkening. */
    public static float smoothedMeteorDarkness() {
        return smoothedMeteorDark;
    }

    /** Eased 0..1 "outdoors-ness": 0 fully sheltered under a roof/overhang, 1 fully in the open. */
    public static float smoothedSkyExposure() {
        return smoothedSkyExposure;
    }

    /** True while any retained weather layer is still visibly fading out. */
    public static boolean visualWeatherActive() {
        // This must stay true for exactly as long as StormVanillaWeatherBridge is forcing
        // rainLevel, because that is what suppresses vanilla's own precipitation geometry. The
        // meteor darkening has to be counted here even though it drives no weather envelope of its
        // own: it was previously missing, so a meteor deployment pinned rainLevel to full while
        // leaving vanilla free to draw precipitation -- which in a cold biome rendered as a
        // full-blown blizzard. Darkening the sky is not optional either, since vanilla's
        // getThunderLevel multiplies by getRainLevel, so rain level is the only way to get there.
        boolean anyActive = renderThunder.active() || renderFog.active() || renderBlizzard.active()
            || renderCherry.active() || StormClientState.meteorActive() || smoothedMeteorDark > 0.01F;
        float peak = Math.max(
            Math.max(Math.max(smoothedCloud, smoothedWind), Math.max(smoothedRain, smoothedLightning)),
            Math.max(Math.max(Math.max(smoothedFogCloud, smoothedFog), Math.max(smoothedBlizzard, smoothedCherry)),
                smoothedMeteorDark));
        return anyActive && peak > 0.01F;
    }

    /** Game time of the client level, or 0 when no level is loaded. */
    public static long gameTime() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            reset();
            return;
        }

        if (!backendLogged) {
            try {
                StormWeaponMod.LOGGER.info("StormWeapon graphics backend: {}", RenderSystem.getDevice().getDeviceInfo().backendName());
                backendLogged = true;
            } catch (IllegalStateException ignored) {
                // The device may not be published during the first client tick; retry next tick.
            }
        }

        StormSnapshot liveThunder = StormClientState.thunder();
        StormSnapshot liveFog = StormClientState.fog();
        StormSnapshot liveBlizzard = StormClientState.blizzard();
        StormSnapshot liveCherry = StormClientState.cherry();
        StormWeatherEffectRenderer.ensureInstalled();
        long gameTime = level.getGameTime();
        CameraShakeManager.observeSnapshot(liveThunder, gameTime);
        CameraShakeManager.observeSnapshot(liveFog, gameTime);
        CameraShakeManager.observeSnapshot(liveBlizzard, gameTime);
        CameraShakeManager.observeSnapshot(liveCherry, gameTime);

        float thunderCloudTarget = 0.0F;
        float thunderWindTarget = 0.0F;
        float rainTarget = 0.0F;
        float lightningTarget = 0.0F;
        if (liveThunder.active()) {
            renderThunder = liveThunder;
            thunderCloudTarget = liveThunder.cloudIntensity(gameTime, 1.0F);
            thunderWindTarget = liveThunder.windIntensity(gameTime, 1.0F);
            rainTarget = liveThunder.rainIntensity(gameTime, 1.0F);
            lightningTarget = liveThunder.lightningIntensity(gameTime, 1.0F);
        }
        float fogCloudTarget = 0.0F;
        float fogTarget = 0.0F;
        if (liveFog.active()) {
            renderFog = liveFog;
            fogCloudTarget = liveFog.cloudIntensity(gameTime, 1.0F);
            fogTarget = liveFog.fogIntensity(gameTime, 1.0F);
        }
        float blizzardTarget = 0.0F;
        if (liveBlizzard.active()) {
            renderBlizzard = liveBlizzard;
            blizzardTarget = seasonalIntensity(liveBlizzard, gameTime);
        }
        float cherryTarget = 0.0F;
        if (liveCherry.active()) {
            renderCherry = liveCherry;
            cherryTarget = seasonalIntensity(liveCherry, gameTime);
        }

        smoothedCloud = smooth(smoothedCloud, thunderCloudTarget);
        smoothedWind = smooth(smoothedWind, thunderWindTarget);
        smoothedRain = smooth(smoothedRain, rainTarget);
        smoothedLightning = smooth(smoothedLightning, lightningTarget);
        smoothedFogCloud = smooth(smoothedFogCloud, fogCloudTarget);
        smoothedFog = smooth(smoothedFog, fogTarget);
        smoothedBlizzard = smooth(smoothedBlizzard, blizzardTarget);
        smoothedCherry = smooth(smoothedCherry, cherryTarget);
        float meteorTarget = StormClientState.meteorActive() ? 1.0F : 0.0F;
        // Deliberately not routed through smooth(): a meteor strike's sky goes dark almost at
        // once, which is the whole tell that something different just arrived overhead.
        smoothedMeteorDark += (meteorTarget - smoothedMeteorDark) * (meteorTarget > smoothedMeteorDark ? 0.22F : 0.05F);
        float skyExposureTarget = minecraft.player != null && SkyExposure.exposed(level, minecraft.player.blockPosition()) ? 1.0F : 0.0F;
        smoothedSkyExposure += (skyExposureTarget - smoothedSkyExposure) * SKY_EXPOSURE_SMOOTHING;
        // Driven by the smoothed fog intensity, not a plain on/off flag, so the cut ramps in over
        // the same ~8-second formation window as the haze itself instead of snapping instantly.
        StormRenderDistanceOverride.tick(smoothedFog);
        StormVanillaWeatherBridge.tick(level);
        com.stormweapon.client.weather.SeasonalPrecipitationManager.tick(level);
        StormWeatherPass.rainRenderer().tickSplashes();
        if (Boolean.getBoolean("stormweapon.runtimeProbe") && !runtimeProbeLogged && liveThunder.active() && smoothedRain > 0.75F) {
            StormWeaponMod.LOGGER.info(
                "Storm Weapon runtime probe active: phase={}, cloud={}, wind={}, rain={}",
                liveThunder.phase(), smoothedCloud, smoothedWind, smoothedRain
            );
            runtimeProbeLogged = true;
        }
        if (!liveThunder.active() && !liveFog.active() && !liveBlizzard.active() && !liveCherry.active()
            && !StormClientState.meteorActive()
            && smoothedCloud < 0.01F && smoothedWind < 0.01F && smoothedMeteorDark < 0.01F
            && smoothedRain < 0.01F && smoothedLightning < 0.01F && smoothedFogCloud < 0.01F
            && smoothedFog < 0.01F && smoothedBlizzard < 0.01F && smoothedCherry < 0.01F) {
            reset();
        }
    }

    private static float smooth(float current, float target) {
        float factor = target < current ? RELEASE_SMOOTHING : ATTACK_SMOOTHING;
        return current + (target - current) * factor;
    }

    private static float seasonalIntensity(StormSnapshot snapshot, long gameTime) {
        if (!snapshot.active()) {
            return 0.0F;
        }
        return snapshot.phase() == com.stormweapon.storm.StormPhase.ATMOSPHERIC_WAVE
            ? net.minecraft.util.Mth.clamp((gameTime - snapshot.startGameTime()) / 160.0F, 0.0F, 1.0F)
            : 1.0F;
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        StormVanillaWeatherBridge.restore();
        StormRenderDistanceOverride.reset();
        StormClientState.clear();
        // Shared settings belong to the world that was just left, not the next one.
        com.stormweapon.config.StormSettingsState.reset();
        StormLightningRenderer.clearEvents();
        reset();
    }

    private static void reset() {
        StormVanillaWeatherBridge.restore();
        StormRenderDistanceOverride.reset();
        renderThunder = StormSnapshot.CLEAR;
        renderFog = StormSnapshot.CLEAR;
        renderBlizzard = StormSnapshot.CLEAR;
        renderCherry = StormSnapshot.CLEAR;
        smoothedCloud = 0.0F;
        smoothedWind = 0.0F;
        smoothedRain = 0.0F;
        smoothedLightning = 0.0F;
        smoothedFogCloud = 0.0F;
        smoothedFog = 0.0F;
        smoothedBlizzard = 0.0F;
        smoothedCherry = 0.0F;
        smoothedSkyExposure = 0.0F;
        smoothedMeteorDark = 0.0F;
        runtimeProbeLogged = false;
    }

    /** Deterministic unit value shared by every client for a given seed, slice and salt. */
    public static float deterministic(long seed, long slice, long salt) {
        long z = seed ^ (slice * 0x9E3779B97F4A7C15L) ^ salt;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (float)((z >>> 11) / (double)(1L << 53));
    }
}
