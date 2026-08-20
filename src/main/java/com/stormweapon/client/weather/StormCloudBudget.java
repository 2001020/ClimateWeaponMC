package com.stormweapon.client.weather;

import com.stormweapon.config.StormQuality;

/**
 * Hard per-preset caps for the storm cloud field. Every value is an explicit budget: the
 * renderer never emits more layers, tiles or geometry than the active preset allows, so the
 * cost of the storm is bounded regardless of storm radius or render distance.
 *
 * @param layers           storm sheets in the Y 180-260 band, counted up from the base
 * @param tileSize         edge length in blocks of one cloud tile
 * @param subdivisions     sub-quads per tile edge; controls how finely the continuous cloud field
 *                         is sampled, and therefore how smooth the deck looks at oblique angles
 * @param viewRadius       maximum distance from the camera at which tiles are emitted
 * @param maxTilesPerFrame total tile cap across all layers for one frame
 * @param maxVertices      absolute vertex ceiling for one frame of cloud geometry
 * @param skyWash          whether the extra high sky wash sheet is drawn
 */
public record StormCloudBudget(
    int layers,
    int tileSize,
    int subdivisions,
    int viewRadius,
    int maxTilesPerFrame,
    int maxVertices,
    boolean skyWash
) {
    // The sky wash is enabled on every preset. It costs a handful of very large tiles and it is
    // what closes the sky overhead; without it a thin single-layer deck leaves bare sky showing
    // through, which is the opposite of the reported problem but just as wrong.
    public static final StormCloudBudget LOW = new StormCloudBudget(2, 40, 1, 120, 96, 3_600, true);
    public static final StormCloudBudget MEDIUM = new StormCloudBudget(3, 32, 2, 144, 220, 8_000, true);
    public static final StormCloudBudget HIGH = new StormCloudBudget(5, 24, 3, 176, 600, 36_000, true);
    public static final StormCloudBudget ULTRA = new StormCloudBudget(5, 20, 4, 208, 1_000, 72_000, true);

    /** Vertices emitted per cloud sub-quad. */
    public static final int VERTICES_PER_QUAD = 4;

    public static StormCloudBudget of(StormQuality quality) {
        return switch (quality) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case ULTRA -> ULTRA;
        };
    }

    /** Sub-quads emitted by one fully drawn tile. */
    public int quadsPerTile() {
        return this.subdivisions * this.subdivisions;
    }

    /** Clamps the configured cloud detail slider (1-4) on top of the preset. */
    public StormCloudBudget withDetail(int cloudQuality) {
        int cappedLayers = Math.max(1, Math.min(layers, cloudQuality));
        if (cappedLayers == layers) {
            return this;
        }
        int scaledTiles = Math.max(24, maxTilesPerFrame * cappedLayers / Math.max(1, layers));
        int scaledVertices = Math.max(1_200, maxVertices * cappedLayers / Math.max(1, layers));
        // The wash is what closes the sky overhead, so it is kept even on a reduced layer count:
        // dropping it is what used to leave a bare strip of sky above a thinned deck.
        return new StormCloudBudget(cappedLayers, tileSize, subdivisions, viewRadius, scaledTiles, scaledVertices, skyWash);
    }
}
