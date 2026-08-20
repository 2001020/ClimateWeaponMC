package com.stormweapon.client;

import com.stormweapon.StormWeaponMod;
import com.stormweapon.config.StormConfig;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/**
 * Full-screen translucent haze for the fog missile, drawn as a GUI overlay layer instead of
 * through the {@code FogData}/{@code ViewportEvent} atmospheric-fog hooks in
 * {@link com.stormweapon.client.weather.StormFogController}.
 *
 * <p>Those hooks only take effect if the active rendering backend actually consults {@code FogData}
 * when compositing the 3D scene, which is not guaranteed on every backend. A full-screen 2D overlay
 * drawn through the same {@code AddGuiOverlayLayersEvent}/{@code GuiGraphics} pipeline already used
 * by this mod's lightning flash (see {@link StormDebugHud}) is backend-agnostic: it is a flat
 * colour composited over the finished frame, so it is guaranteed visible on Vulkan and OpenGL
 * alike, and it also depresses the readability of everything on screen (not just distant terrain),
 * which is what a large flat visibility reduction actually needs to look like.</p>
 */
public final class StormFogOverlay {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "fog_overlay");

    /**
     * A pale but recognizably yellow haze colour, RGB only; alpha is computed per frame below.
     *
     * <p>{@code fill()} is a standard straight alpha blend: on a bright outdoor pixel a light,
     * near-white colour is already very visible, but on a dark indoor/underground pixel the same
     * low-alpha near-white colour blends down to almost nothing (alpha * colour, both small), which
     * is why an earlier, paler version of this tint effectively disappeared indoors. Using a more
     * saturated colour keeps a visible warm shift even against a near-black base.</p>
     */
    private static final int HAZE_RGB = 0x00F6E6AA;

    /**
     * Alpha at full fog strength: 20% opacity. {@link StormRenderDistanceOverride} carries the
     * actual visibility cut, so this layer only needs to read as a light colour wash.
     */
    private static final int MAX_ALPHA = 51;

    private StormFogOverlay() {}

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            // Drawn below PRE_SLEEP_STACK, which holds the crosshair, hotbar, health and every
            // other HUD element: the tint composites first, so the HUD renders on top of it and
            // stays fully legible instead of being washed out underneath it.
            event.getLayeredDraw().addBelow(
                ForgeLayeredDraw.VANILLA_ROOT, LAYER_ID, ForgeLayeredDraw.PRE_SLEEP_STACK, StormFogOverlay::draw
            );
        });
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!StormConfig.stormFog()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        StormSnapshot snapshot = StormClientManager.fogSnapshot();
        if (!snapshot.active()) {
            return;
        }
        float influence = snapshot.radialInfluence(minecraft.player.getX(), minecraft.player.getZ());
        if (influence <= 0.0F) {
            return;
        }
        // The haze is outdoor weather: a real roof genuinely blocks it, the same way it blocks
        // rain. This uses SkyExposure rather than plain canSeeSky, which counted leaves as
        // sky-blocking and let players dodge the whole effect just by standing under any tree.
        // Rather than a hard cut the moment shelter is entered, this eases in/out over a few
        // seconds so walking through a doorway doesn't pop the tint on and off.
        float exposure = StormClientManager.smoothedSkyExposure();
        float strength = Mth.clamp(StormClientManager.smoothedFogIntensity() * influence * exposure, 0.0F, 1.0F);
        if (strength <= 0.01F) {
            return;
        }

        int alpha = Mth.clamp(Math.round(MAX_ALPHA * strength), 0, 255);
        graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
            (alpha << 24) | HAZE_RGB);
    }
}
