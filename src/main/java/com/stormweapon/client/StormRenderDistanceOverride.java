package com.stormweapon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Directly drives Minecraft's own render-distance option while a fog missile deployment is
 * active, instead of only approximating a visibility cut through custom fog planes or geometry.
 * This is the real vanilla view-distance mechanism, so the drop in visible world reads as genuine
 * global fog rather than a shape sitting near the player, and it renders identically on every
 * backend since nothing beyond the option itself is involved.
 *
 * <p>{@code Options.renderDistance()} is not purely a local rendering setting though:
 * {@code Options.buildPlayerInformation()} reports it to the server as the client's requested view
 * distance, and the server caps how large a radius of chunks/entities it sends that client to
 * match. That used to be a real problem here -- flooring it all the way to the game's hard minimum
 * (2 chunks) starved an independently active thunder deployment's lightning bolts (and their
 * built-in vanilla thunder sound) whenever they landed outside the shrunken radius. It is safe to
 * floor all the way down now because {@link com.stormweapon.storm.StormSavedData#isOccupied} only
 * ever allows one deployment, thunder or fog, to be active at a time: there is no longer any
 * scenario where a fog missile's render-distance cut can starve an independently active thunder
 * deployment, because there is no such thing as an independently active thunder deployment anymore.</p>
 */
public final class StormRenderDistanceOverride {
    /** The game's own hard floor for this option (see {@code Options.IntRange(2, ...)}). */
    private static final int MIN_RENDER_DISTANCE = 2;

    /** The game's own hard floor for {@code entityDistanceScaling} (see {@code Options.IntRange(2, 20)}, step 0.5). */
    private static final double MIN_ENTITY_DISTANCE_SCALING = 0.5D;

    /** Below this, treated as "no fog yet" so brief flicker around zero can't retrigger a toggle. */
    private static final float ACTIVATION_THRESHOLD = 0.05F;

    private static boolean applied;
    private static int savedRenderDistance;
    private static double savedEntityDistanceScaling;

    private StormRenderDistanceOverride() {}

    /**
     * @param fogIntensity smoothed 0..1 strength of the fog missile's haze.
     */
    public static void tick(float fogIntensity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (fogIntensity > ACTIVATION_THRESHOLD) {
            if (!applied) {
                // Render distance is switched in one single step, not animated every tick: changing
                // it rebuilds the whole chunk render cache, and stepping through every intermediate
                // notch during the fog ramp used to rebuild it once per notch, which showed up as
                // chunks visibly flickering/reloading.
                savedRenderDistance = minecraft.options.renderDistance().get();
                savedEntityDistanceScaling = minecraft.options.entityDistanceScaling().get();
                int floor = Math.min(savedRenderDistance, MIN_RENDER_DISTANCE);
                minecraft.options.renderDistance().set(floor);
                applied = true;
            }
            // Cheap per-frame value, not a chunk rebuild, so this alone can stay smoothly animated.
            minecraft.options.entityDistanceScaling().set(Mth.lerp(fogIntensity, savedEntityDistanceScaling, MIN_ENTITY_DISTANCE_SCALING));
        } else if (applied) {
            minecraft.options.renderDistance().set(savedRenderDistance);
            minecraft.options.entityDistanceScaling().set(savedEntityDistanceScaling);
            applied = false;
        }
    }

    public static void reset() {
        if (applied) {
            Minecraft.getInstance().options.renderDistance().set(savedRenderDistance);
            Minecraft.getInstance().options.entityDistanceScaling().set(savedEntityDistanceScaling);
            applied = false;
        }
    }
}
