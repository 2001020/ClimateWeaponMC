package com.stormweapon.client.weather;

import com.stormweapon.client.StormClientManager;
import com.stormweapon.config.StormConfig;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Visibility transition for the storm region.
 *
 * <p>Sky colour is deliberately left to Minecraft's own thunderstorm calculation through
 * {@link StormVanillaWeatherBridge}. This controller only pulls the atmospheric fog planes closer;
 * world time, chunk light, block light and the server render distance are never modified.</p>
 */
public final class StormFogController {
    /**
     * Fog far plane at full storm influence and at the first storm phases.
     *
     * <p>Environmental fog is spherical, so the core stays near the upper end of the intended
     * visibility band. This keeps nearby terrain readable while the stock thunder sky and distant
     * landscape merge into the same storm haze.</p>
     */
    private static final float CORE_FOG_DISTANCE = 165.0F;
    private static final float MEDIUM_FOG_DISTANCE = 245.0F;

    /**
     * The fog missile cuts a percentage off whatever visibility the player already has, rather
     * than pulling in to a fixed absolute distance like the storm above. That keeps the effect
     * equally noticeable at every render-distance setting instead of disappearing for players with
     * a short view distance already below the storm's fixed target.
     *
     * <p>100x deliberately overshoots a simple "cut the distance" reduction by a huge margin: the
     * multiplier below goes deeply negative almost the instant the fog starts forming, so it is
     * clamped to {@link #FOG_MIN_FAR_DISTANCE} for nearly the entire deployment, reading as a
     * genuine dense whiteout rather than a merely hazy view.</p>
     *
     * <p>This hook only takes effect on backends that actually consult {@code FogData} when
     * compositing the 3D scene; {@link com.stormweapon.client.StormFogOverlay} is the
     * backend-agnostic guarantee that carries the same effect on every renderer, Vulkan included.</p>
     */
    private static final float FOG_VISIBILITY_REDUCTION = 100.0F;

    /** Absolute floor for the fog far plane so a maxed-out reduction never collapses to zero. */
    private static final float FOG_MIN_FAR_DISTANCE = 2.5F;
    /** Absolute floor for the fog near plane so the player can still see their own surroundings. */
    private static final float FOG_MIN_NEAR_DISTANCE = 0.4F;

    private StormFogController() {}

    public static void register() {
        ViewportEvent.RenderFog.BUS.addListener(StormFogController::onRenderFog);
        ViewportEvent.ComputeFogColor.BUS.addListener(StormFogController::onComputeFogColor);
    }

    /**
     * Cancelling is required by the Forge contract for plane distance changes to be honoured,
     * so this listener returns {@code true} whenever it actually modified the fog.
     */
    private static boolean onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.ATMOSPHERIC) {
            return false;
        }
        float thunderStrength = strength(StormClientManager.thunderSnapshot(), StormClientManager.smoothedCloudIntensity(),
            event.getCamera());
        float fogStrength = strength(StormClientManager.fogSnapshot(), StormClientManager.smoothedFogIntensity(),
            event.getCamera());
        if (thunderStrength <= 0.0F && fogStrength <= 0.0F) {
            return false;
        }

        FogData data = event.getData();
        boolean modified = false;
        if (thunderStrength > 0.0F) {
            float target = Mth.lerp(thunderStrength, MEDIUM_FOG_DISTANCE, CORE_FOG_DISTANCE);
            float far = Math.min(data.environmentalEnd <= 0.0F ? target : data.environmentalEnd, target);
            float near = far * 0.25F;

            data.environmentalStart = Math.min(data.environmentalStart, near);
            data.environmentalEnd = far;
            // Pull the sky fade in with the fog so the sky reads as one deep blue-gray mass.
            data.skyEnd = data.skyEnd <= 0.0F ? far * 1.25F : Math.min(data.skyEnd, far * 1.25F);
            data.cloudEnd = data.cloudEnd <= 0.0F ? far * 2.0F : Math.min(data.cloudEnd, far * 2.0F);
            data.renderDistanceEnd = Math.min(data.renderDistanceEnd, far * 1.6F);
            data.renderDistanceStart = Math.min(data.renderDistanceStart, data.renderDistanceEnd * 0.35F);
            modified = true;
        }
        if (fogStrength > 0.0F) {
            // A flat percentage cut off the player's own current view distance, ramping in with
            // the 8-second fog-formation strength. renderDistanceEnd already reflects the actual
            // server/client render distance in blocks (or the thunder branch's own cut above, if
            // that already ran this frame), so this always further tightens whatever visibility
            // remained rather than a fixed absolute distance that could sit beyond it.
            float baseline = data.renderDistanceEnd > 0.0F ? data.renderDistanceEnd
                : (data.environmentalEnd > 0.0F ? data.environmentalEnd : 256.0F);
            float far = Math.max(FOG_MIN_FAR_DISTANCE, baseline * (1.0F - FOG_VISIBILITY_REDUCTION * fogStrength));
            // The haze starts almost immediately around the camera instead of only thickening at
            // the edge of view, so the player reads it as standing inside dense fog, not just
            // looking at a shortened horizon.
            float near = Math.max(FOG_MIN_NEAR_DISTANCE, far * 0.12F);

            data.environmentalStart = Math.min(data.environmentalStart <= 0.0F ? near : data.environmentalStart, near);
            data.environmentalEnd = data.environmentalEnd <= 0.0F ? far : Math.min(data.environmentalEnd, far);
            data.skyEnd = data.skyEnd <= 0.0F ? far * 1.1F : Math.min(data.skyEnd, far * 1.1F);
            data.cloudEnd = data.cloudEnd <= 0.0F ? far * 1.3F : Math.min(data.cloudEnd, far * 1.3F);
            data.renderDistanceEnd = Math.min(data.renderDistanceEnd, far);
            data.renderDistanceStart = Math.min(data.renderDistanceStart, near);
            modified = true;
        }
        return modified;
    }

    /**
     * Dark blue-gray for the thunderstorm; a clearly yellow-tinted pale haze for the fog missile.
     * The low blue channel relative to red/green is what reads as unmistakably yellow to the naked
     * eye instead of blending into an ordinary white/gray overcast. When both are active the fog
     * colour is blended in on top of the thunder colour, since the fog missile's haze is the more
     * dominant, close-range effect of the two.
     */
    private static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        float thunderStrength = strength(StormClientManager.thunderSnapshot(), StormClientManager.smoothedCloudIntensity(),
            event.getCamera());
        float fogStrength = strength(StormClientManager.fogSnapshot(), StormClientManager.smoothedFogIntensity(),
            event.getCamera());
        if (thunderStrength > 0.0F) {
            event.setRed(Mth.lerp(thunderStrength, event.getRed(), 0.025F));
            event.setGreen(Mth.lerp(thunderStrength, event.getGreen(), 0.040F));
            event.setBlue(Mth.lerp(thunderStrength, event.getBlue(), 0.095F));
        }
        if (fogStrength > 0.0F) {
            event.setRed(Mth.lerp(fogStrength, event.getRed(), 0.93F));
            event.setGreen(Mth.lerp(fogStrength, event.getGreen(), 0.82F));
            event.setBlue(Mth.lerp(fogStrength, event.getBlue(), 0.38F));
        }
    }

    /** Smoothed strength of one storm slot at the camera, or 0 when it must not affect the view. */
    private static float strength(StormSnapshot snapshot, float smoothedIntensity, Camera camera) {
        if (!StormConfig.stormFog() || !snapshot.active()) {
            return 0.0F;
        }
        float influence = snapshot.radialInfluence(camera.position().x, camera.position().z);
        if (influence <= 0.0F) {
            return 0.0F;
        }
        return Mth.clamp(smoothedIntensity * influence, 0.0F, 1.0F);
    }
}
